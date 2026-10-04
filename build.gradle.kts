import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

// Keep transient Gradle outputs outside OneDrive; only the final jar is exported to dist/.
layout.buildDirectory.set(
    File(System.getProperty("user.home"), ".gradle-builds/${rootProject.name}")
)

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.gtemc.net/releases/")
}

dependencies {
    // Exact Paper 26.3 API/NMS boundary for fullscreen packets and the existing Dialog renderer.
    paperweight.paperDevBundle("26.3.build.142-beta")
    // Read-only resource-pack observer on the player's Netty channel (the server provides Netty).
    compileOnly("io.netty:netty-transport:4.2.15.Final")
    // Other plugins' API jars (PlaceholderAPI), never shipped.
    compileOnly(fileTree("libs") {
        include("*.jar")
        exclude("keystone-*.jar")
    })
    // Shared helpers (lang/commands/tasks/events/update check), relocated into this plugin below.
    implementation(files("libs/keystone-0.3.5.jar"))
    // Item-source bridge shipped inside the jar under the same relocated package as before.
    implementation("cn.gtemc:itembridge:1.0.32") { isTransitive = false }

    testImplementation("io.papermc.paper:paper-api:26.3.build.142-beta")
    // EmbeddedChannel for the resource-pack observer tests.
    testImplementation("io.netty:netty-transport:4.2.15.Final")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.19.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

listOf("compileClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach {
    configurations.named(it) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test { useJUnitPlatform() }

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") { expand("version" to version) }
}

// The plugin jar: our classes plus Keystone and ItemBridge, relocated so nothing is shared with
// other plugins (each plugin carries its own copy; no runtime downloads).
tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("${project.name}-${project.version}.jar")
    destinationDirectory.set(layout.projectDirectory.dir("dist"))
    relocate("dev.keystone", "online.toraka.dialogmenu.libs.keystone")
    relocate("cn.gtemc.itembridge", "online.toraka.dialogmenu.library.itembridge")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**")
    exclude("META-INF/versions/*/module-info.class", "module-info.class")
    from("LICENSE")
    from("THIRD_PARTY_NOTICES.md")
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Ship only the Keystone classes this plugin actually reaches (Shadow minimize).
// - Keystone is the only files(...) dependency. Shadow's minimize filter sees Maven-coordinate
//   dependencies only, so `exclude { true }` keeps ItemBridge whole while Keystone is trimmed.
// - Roots are the main classes. Shadow's default also roots the test classes, which would ship
//   Keystone classes that only the tests use.
// - Keystone 0.3.5's language/update reflection adapters are verified by tests and minimize_check.
// Verify with tools/minimize_check.py (see plugins-dev/refactor/notes/shadow-minimize.md).
tasks.shadowJar {
    minimize {
        exclude { true }
    }
    sourceSetsClassesDirs.setFrom(sourceSets.main.get().output.classesDirs)
}

tasks.jar { archiveClassifier.set("plain") }

tasks.assemble { dependsOn(tasks.shadowJar) }

apply(from = rootProject.file("gradle/source-quality.gradle"))
apply(from = rootProject.file("gradle/bundled-resourcepack.gradle"))

val fullscreenPack = tasks.register<Exec>("fullscreenPack") {
    val client = providers.gradleProperty("fullscreenClientJar").orElse(
        "${System.getenv("LOCALAPPDATA")}/TorakaSelfdev/fullscreen-client-26.3/client.jar"
    )
    inputs.files("tools/build_fullscreen_pack.py", "design/fullscreen", "design/font/native_cjk.vsh",
        "src/main/java/online/toraka/dialogmenu/fullscreen/DemoLayout.java", client.get())
    inputs.property("version", project.version)
    val output = layout.buildDirectory.file("generated/fullscreen/DialogMenu-fullscreen.zip")
    outputs.file(output)
    commandLine("py", "-3", "-B", "-X", "utf8", "tools/build_fullscreen_pack.py",
        "--client", client.get(), "--version", project.version.toString(), "--output", output.get().asFile.absolutePath)
}
// Protocol validation is an isolated test plugin, never included in the production JAR.
val probe = sourceSets.create("probe")
probe.compileClasspath += sourceSets.main.get().compileClasspath
tasks.register<Jar>("fullscreenProbeJar") {
    dependsOn(tasks.named(probe.classesTaskName))
    from(probe.output)
    archiveFileName.set("FullscreenProbe.jar")
    destinationDirectory.set(layout.buildDirectory.dir("probe"))
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
}
