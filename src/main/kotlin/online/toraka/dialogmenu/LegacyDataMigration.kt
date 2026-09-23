package online.toraka.dialogmenu

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Imports the old directory without deleting it or replacing administrator edits. */
object LegacyDataMigration {
    private const val MARKER = ".playersettings-import"

    fun migrate(destination: Path, generatedFiles: Map<String, ByteArray> = emptyMap()): Boolean {
        val target = destination.toAbsolutePath().normalize()
        val legacy = target.resolveSibling("PlayerSettings")
        val marker = target.resolve(MARKER)
        val mainFiles = listOf("config.yml", "menu.yml")
        require(!Files.isSymbolicLink(target) && !Files.isSymbolicLink(legacy)) {
            "配置目录不能是符号链接：$target / $legacy"
        }
        if (!Files.exists(marker) && mainFiles.any { Files.isRegularFile(target.resolve(it)) })
            return false
        if (!Files.isDirectory(legacy)) {
            check(!Files.exists(marker)) { "迁移未完成，但旧目录已不存在：$legacy" }
            return false
        }
        if (mainFiles.none { Files.isRegularFile(legacy.resolve(it)) }) return false
        val entries = Files.walk(legacy).use { paths -> paths.filter { it != legacy }.toList() }
        // Preflight every path before writing; existing user files must never be overwritten.
        for (source in entries) {
            val relative = legacy.relativize(source)
            val output = target.resolve(relative)
            require(!Files.isSymbolicLink(source) && !Files.isSymbolicLink(output)) {
                "迁移不支持符号链接：$source / $output"
            }
            if (Files.isDirectory(source, NOFOLLOW_LINKS)) {
                check(!Files.exists(output) || Files.isDirectory(output, NOFOLLOW_LINKS)) {
                    "迁移路径冲突：$output"
                }
            } else {
                check(Files.isRegularFile(source, NOFOLLOW_LINKS)) { "迁移不支持此文件：$source" }
                if (Files.exists(output, NOFOLLOW_LINKS)) {
                    val bundled = generatedFiles[relative.toString().replace('\\', '/')]
                    check(
                        Files.isRegularFile(output, NOFOLLOW_LINKS) &&
                            (Files.mismatch(source, output) == -1L ||
                                bundled != null &&
                                    Files.readAllBytes(output).contentEquals(bundled))
                    ) {
                        "迁移文件冲突，保留两份原文件，请手动合并：$output"
                    }
                }
            }
        }
        Files.createDirectories(target)
        Files.writeString(marker, "PlayerSettings -> DialogMenu\n")
        // Configs are copied last, so an interrupted copy cannot look like a completed migration.
        for (source in
            entries.sortedBy { if (legacy.relativize(it).toString() in mainFiles) 1 else 0 }) {
            val output = target.resolve(legacy.relativize(source))
            if (Files.isDirectory(source, NOFOLLOW_LINKS)) Files.createDirectories(output)
            else {
                Files.createDirectories(output.parent)
                Files.copy(source, output, REPLACE_EXISTING)
            }
        }
        Files.delete(marker)
        return true
    }
}
