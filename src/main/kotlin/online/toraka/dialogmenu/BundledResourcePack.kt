package online.toraka.dialogmenu

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.bukkit.configuration.file.YamlConfiguration

/** Paths follow each provider's documented resource-pack input, never its generated output. */
enum class ResourcePackProvider(
    val pluginName: String,
    val sourceDirectory: String,
    val inputDirectory: String,
) {
    CRAFT_ENGINE("CraftEngine", "resources/dialogmenu", "resources"),
    ITEMS_ADDER("ItemsAdder", "contents/dialogmenu", "contents"),
    NEXO("Nexo", "pack/external_packs/dialogmenu", "pack/external_packs"),
    ORAXEN("Oraxen", "pack", "pack");

    fun instructions(): String =
        when (this) {
            CRAFT_ENGINE ->
                "执行 /ce reload all 重新读取资源目录，再执行服务器配置的资源包工作流（默认 /ce workflow default），让玩家重新接收合并包。"
            ITEMS_ADDER -> "执行 /iazip 重新生成资源包，再由 ItemsAdder 发送，让玩家接受并加载。"
            NEXO -> "执行 /nexo reload pack；确认 Nexo 已启用资源包发送，让玩家接受并加载。"
            ORAXEN -> "执行 /oraxen reload pack，再使用 /oraxen pack send <玩家> 或重新进服接收。"
        }
}

data class AvailableResourceProvider(
    val provider: ResourcePackProvider,
    val directory: Path,
    val externalDirectories: List<Path> = emptyList(),
    val externalArchives: List<Path> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun read(provider: ResourcePackProvider, directory: Path): AvailableResourceProvider {
            val file =
                directory.resolve(
                    if (provider == ResourcePackProvider.CRAFT_ENGINE) "config.yml"
                    else "settings.yml"
                )
            if (!Files.isRegularFile(file)) return AvailableResourceProvider(provider, directory)
            return try {
                val yaml = YamlConfiguration().also { it.load(file.toFile()) }
                val directories = mutableListOf<Path>()
                val archives = mutableListOf<Path>()
                val warnings = mutableListOf<String>()
                if (provider == ResourcePackProvider.CRAFT_ENGINE) {
                    // CraftEngine's PackCacheData resolves external inputs relative to plugins/.
                    directories.addAll(
                        yaml.getStringList("resource-pack.merge-external-folders").map {
                            directory.parent.resolve(it)
                        }
                    )
                    archives.addAll(
                        yaml
                            .getStringList("resource-pack.merge-external-zip-files")
                            .map { directory.parent.resolve(it) }
                            .filter { Files.exists(it) }
                    )
                    if (yaml.getBoolean("resource-pack.exclude-core-shaders")) {
                        warnings +=
                            "$file 的 resource-pack.exclude-core-shaders 已启用，合并包会缺少 DialogMenu GUI shader；请由服主检查，未自动修改。"
                    }
                }
                if (
                    provider == ResourcePackProvider.ORAXEN &&
                        yaml.getBoolean("Pack.import.remove_core_shaders_from_imported_packs")
                ) {
                    warnings +=
                        "$file 的 Pack.import.remove_core_shaders_from_imported_packs 已启用，Oraxen 会过滤 DialogMenu GUI shader；请由服主检查，未自动修改。"
                }
                AvailableResourceProvider(provider, directory, directories, archives, warnings)
            } catch (error: Exception) {
                // Do not write into a provider whose source configuration could not be inspected.
                throw IllegalArgumentException(
                    "无法读取 ${provider.pluginName} 资源输入配置 $file：${error.message}",
                    error,
                )
            }
        }
    }
}

data class ResourcePackInstallResult(
    val exportedZip: Path,
    val provider: AvailableResourceProvider?,
    val changed: Int,
    val conflicts: List<String>,
)

/** Pure file installation, separate from Bukkit and from the resource-pack sender. */
class BundledResourcePack(private val dataDirectory: Path, private val archive: ByteArray) {
    companion object {
        const val RESOURCE = "bundled/DialogMenu-resourcepack.zip"

        fun detect(
            config: MenuResourcePack,
            available: List<AvailableResourceProvider>,
        ): AvailableResourceProvider? {
            val eligible =
                if (config.provider == "craftengine") {
                    available.filter { it.provider == ResourcePackProvider.CRAFT_ENGINE }
                } else available
            return eligible.minByOrNull { it.provider.ordinal }
        }

        fun select(
            config: MenuResourcePack,
            available: List<AvailableResourceProvider>,
        ): AvailableResourceProvider? = if (config.autoInstall) detect(config, available) else null

        private fun digest(bytes: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }

        private fun validName(name: String): Boolean =
            name.isNotEmpty() &&
                !name.startsWith('/') &&
                name.none { it == '\\' || it == ':' || it.isISOControl() } &&
                name.split('/').all { it.isNotEmpty() && it != "." && it != ".." }

        private fun safePath(root: Path, name: String, expectedRoot: Path? = null): Path {
            require(validName(name)) { "不安全的资源路径：$name" }
            val absolute = root.toAbsolutePath().normalize()
            Files.createDirectories(absolute)
            val realRoot = absolute.toRealPath()
            require(expectedRoot == null || realRoot == expectedRoot) {
                "资源目标目录已改变，已停止写入：$absolute"
            }
            val target = absolute.resolve(name).normalize()
            require(target.startsWith(absolute)) { "资源路径超出目标目录：$name" }
            var current = absolute
            for (component in absolute.relativize(target)) {
                current = current.resolve(component)
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    require(
                        !Files.isSymbolicLink(current) &&
                            current.toRealPath() == realRoot.resolve(absolute.relativize(current))
                    ) {
                        "资源路径包含链接或重定向：$current"
                    }
                }
            }
            return target
        }

        private fun atomicWrite(path: Path, bytes: ByteArray) {
            Files.createDirectories(path.parent)
            val temporary = Files.createTempFile(path.parent, ".dialogmenu-", ".tmp")
            try {
                Files.write(temporary, bytes)
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    private val entries: Map<String, ByteArray> by lazy {
        val result = linkedMapOf<String, ByteArray>()
        var expanded = 0L
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                require(validName(entry.name)) { "内置资源包包含不安全路径：${entry.name}" }
                require(entry.name !in result) { "内置资源包包含重复文件：${entry.name}" }
                val bytes = zip.readNBytes(16 * 1024 * 1024 + 1)
                expanded += bytes.size
                require(
                    bytes.size <= 16 * 1024 * 1024 &&
                        expanded <= 128 * 1024 * 1024 &&
                        result.size < 50000
                ) {
                    "内置资源包超过大小限制"
                }
                result[entry.name] = bytes
            }
        }
        require("pack.mcmeta" in result && result.keys.any { it.startsWith("assets/") }) {
            "内置资源包缺少 pack.mcmeta 或 assets"
        }
        result
    }

    /**
     * Only the provider's input root may redirect; all descendants stay inside its resolved root.
     */
    private data class InstallationRoot(
        val logicalRoot: Path,
        val logicalInput: Path,
        val resolvedInput: Path,
    ) {
        fun resolve(name: String): Path {
            require(validName(name)) { "不安全的资源路径：$name" }
            val logicalTarget = logicalRoot.resolve(name).normalize()
            require(logicalTarget.startsWith(logicalInput)) { "资源路径超出输入目录：$name" }
            val relative = logicalInput.relativize(logicalTarget).joinToString("/")
            return safePath(resolvedInput, relative, resolvedInput)
        }
    }

    fun install(
        config: MenuResourcePack,
        available: List<AvailableResourceProvider>,
    ): ResourcePackInstallResult {
        // Validate the entire archive before writing anything.
        val assets = entries
        val exported = safePath(dataDirectory, "resourcepack/DialogMenu-resourcepack.zip")
        val exportResult =
            synchronize(
                dataDirectory,
                "resourcepack/export-state.properties",
                mapOf("resourcepack/DialogMenu-resourcepack.zip" to archive),
            )
        val provider =
            select(config, available)
                ?: return ResourcePackInstallResult(exported, null, 0, exportResult.conflicts)
        val prefix = provider.provider.sourceDirectory
        val destination =
            when (provider.provider) {
                ResourcePackProvider.CRAFT_ENGINE,
                ResourcePackProvider.ITEMS_ADDER -> "$prefix/resourcepack"
                else -> prefix
            }
        val files = linkedMapOf<String, ByteArray>()
        for ((name, bytes) in assets) {
            // Oraxen owns pack.mcmeta; CE and IA also generate their own top-level metadata.
            val license =
                !name.contains('/') &&
                    (name.startsWith("LICENSE", ignoreCase = true) ||
                        name.startsWith("NOTICE", ignoreCase = true))
            if (
                name.startsWith("assets/") ||
                    license ||
                    provider.provider == ResourcePackProvider.NEXO
            ) {
                files["$destination/$name"] = bytes
            }
        }
        if (provider.provider == ResourcePackProvider.CRAFT_ENGINE) {
            files["$prefix/pack.yml"] =
                ("author: DialogMenu\ndescription: Bundled DialogMenu menu resources\n" +
                        "version: '1.0'\nnamespace: dialogmenu\nenable: true\n")
                    .toByteArray(Charsets.UTF_8)
        }
        val shared = externalConflicts(provider, assets)
        val result =
            synchronize(
                provider.directory,
                "resourcepack/${provider.provider.name.lowercase()}-state.properties",
                files,
                shared.files.mapKeys { "$destination/${it.key}" },
                provider.provider.inputDirectory,
            )
        return ResourcePackInstallResult(
            exported,
            provider,
            result.changed,
            exportResult.conflicts + provider.warnings + shared.warnings + result.conflicts,
        )
    }

    private data class WriteResult(val changed: Int, val conflicts: List<String>)

    private fun synchronize(
        targetRoot: Path,
        stateName: String,
        files: Map<String, ByteArray>,
        external: Map<String, String> = emptyMap(),
        inputDirectory: String = "",
    ): WriteResult {
        val stateFile = safePath(dataDirectory, stateName)
        val state = Properties()
        if (Files.isRegularFile(stateFile)) Files.newInputStream(stateFile).use { state.load(it) }
        val logicalRoot = targetRoot.toAbsolutePath().normalize()
        val logicalInput = logicalRoot.resolve(inputDirectory)
        val root =
            try {
                Files.createDirectories(logicalInput)
                InstallationRoot(logicalRoot, logicalInput, logicalInput.toRealPath())
            } catch (error: Exception) {
                return WriteResult(0, listOf("$logicalInput（无法访问资源输入目录：${error.message}）"))
            }
        val scope = logicalRoot.toString()
        val previousRoot = state.getProperty("resolved-root")
        // Legacy state did not record resolved paths. Only inherit it for an ordinary directory.
        val sameRoot =
            if (previousRoot == null) root.resolvedInput == logicalInput
            else previousRoot == root.resolvedInput.toString()
        if (state.getProperty("target") != scope || !sameRoot) state.clear()
        val next =
            Properties().apply {
                setProperty("target", scope)
                setProperty("resolved-root", root.resolvedInput.toString())
            }
        var changed = 0
        val conflicts = mutableListOf<String>()
        for ((name, bytes) in files) {
            val previous = state.getProperty("file.$name")
            if (previous != null) next.setProperty("file.$name", previous)
            try {
                val target = root.resolve(name)
                val hash = digest(bytes)
                val current =
                    if (Files.isRegularFile(target)) digest(Files.readAllBytes(target)) else null
                val elsewhere = external[name]
                if (elsewhere != null) {
                    // Remove our unchanged duplicate so it cannot override the user's shader during
                    // merging.
                    if (previous != null && current == previous) {
                        Files.delete(target)
                        next.remove("file.$name")
                        changed++
                    }
                    conflicts += "$name（另一个资源源中有不同内容：$elsewhere）"
                } else if (
                    Files.exists(target) &&
                        current != hash &&
                        (previous == null || current != previous)
                ) {
                    conflicts += "$name（已有文件或目录由服主维护，已保留）"
                } else if (current != hash) {
                    atomicWrite(target, bytes)
                    next.setProperty("file.$name", hash)
                    changed++
                } else if (previous != null) {
                    next.setProperty("file.$name", hash)
                }
                // Identical unmanaged files remain unmanaged; never claim ownership of user files.
            } catch (error: Exception) {
                conflicts += "$name（${error.message}）"
            }
        }
        for (key in state.stringPropertyNames().filter { it.startsWith("file.") }) {
            val name = key.removePrefix("file.")
            if (name in files) continue
            try {
                val target = root.resolve(name)
                if (
                    Files.isRegularFile(target) &&
                        digest(Files.readAllBytes(target)) == state.getProperty(key)
                ) {
                    Files.delete(target)
                    changed++
                }
            } catch (error: Exception) {
                conflicts += "$name（旧文件保留：${error.message}）"
            }
        }
        val output = ByteArrayOutputStream()
        next.store(output, "DialogMenu managed resources; edited files are never overwritten")
        if (next != state) atomicWrite(stateFile, output.toByteArray())
        return WriteResult(changed, conflicts)
    }

    private data class ExternalConflicts(val files: Map<String, String>, val warnings: List<String>)

    private fun externalConflicts(
        provider: AvailableResourceProvider,
        assets: Map<String, ByteArray>,
    ): ExternalConflicts {
        val roots = mutableListOf<Path>()
        val archives = mutableListOf<Path>()
        val conflicts = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()
        val base = provider.directory
        roots.addAll(provider.externalDirectories)
        archives.addAll(provider.externalArchives)
        fun unreadable(path: Path, error: Exception) {
            warnings += "$path（无法检查已有资源，核心 shader 未安装：${error.message}）"
            assets.keys
                .filter { it.startsWith("assets/minecraft/shaders/") }
                .forEach {
                    conflicts[it] = path.toString()
                }
        }
        fun children(folder: Path): List<Path> =
            try {
                if (!Files.isDirectory(folder)) emptyList()
                else Files.list(folder).use { it.toList() }
            } catch (error: Exception) {
                unreadable(folder, error)
                emptyList()
            }
        fun imported(folder: Path, skipOwnDirectory: Boolean = false) {
            children(folder)
                .filter { !skipOwnDirectory || it.fileName.toString() != "dialogmenu" }
                .forEach {
                    if (Files.isDirectory(it)) roots.add(it)
                    else if (it.fileName.toString().endsWith(".zip", ignoreCase = true))
                        archives.add(it)
                }
        }
        when (provider.provider) {
            ResourcePackProvider.CRAFT_ENGINE ->
                children(base.resolve("resources"))
                    .filter {
                        it.fileName.toString() != "dialogmenu" &&
                            !it.fileName.toString().startsWith('.')
                    }
                    .forEach { roots.add(it.resolve("resourcepack")) }
            ResourcePackProvider.ITEMS_ADDER ->
                children(base.resolve("contents"))
                    .filter { it.fileName.toString() != "dialogmenu" }
                    .forEach {
                        roots.add(it.resolve("resourcepack"))
                        roots.add(it)
                    }
            ResourcePackProvider.NEXO -> {
                roots.add(base.resolve("pack"))
                imported(base.resolve("pack/external_packs"), skipOwnDirectory = true)
            }
            ResourcePackProvider.ORAXEN -> imported(base.resolve("pack/uploads"))
        }
        for ((name, bytes) in assets.filterKeys { it.startsWith("assets/") }) {
            for (root in roots) {
                val candidates =
                    if (provider.provider == ResourcePackProvider.ITEMS_ADDER) {
                        listOf(root.resolve(name), root.resolve(name.removePrefix("assets/")))
                    } else listOf(root.resolve(name))
                for (candidate in candidates) {
                    try {
                        if (Files.isRegularFile(candidate)) {
                            val existing =
                                Files.newInputStream(candidate).use {
                                    it.readNBytes(bytes.size + 1)
                                }
                            if (!existing.contentEquals(bytes))
                                conflicts[name] = candidate.toString()
                        }
                    } catch (error: Exception) {
                        conflicts[name] = "$candidate（${error.message}）"
                    }
                }
            }
        }
        for (archive in archives) {
            try {
                ZipFile(archive.toFile()).use { zip ->
                    for ((name, bytes) in assets.filterKeys { it.startsWith("assets/") }) {
                        val entry = zip.getEntry(name) ?: continue
                        val existing =
                            zip.getInputStream(entry).use { it.readNBytes(bytes.size + 1) }
                        if (!existing.contentEquals(bytes)) conflicts[name] = "$archive!/$name"
                    }
                }
            } catch (error: Exception) {
                unreadable(archive, error)
            }
        }
        return ExternalConflicts(conflicts, warnings)
    }
}
