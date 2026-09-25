import org.gradle.api.attributes.java.TargetJvmVersion
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import io.izzel.taboolib.gradle.*

plugins {
    kotlin("jvm") version "2.4.10"
    id("io.izzel.taboolib") version "2.0.38"
}

taboolib {
    description {
        name("DialogMenu")
        desc("Configurable Dialog menus with custom layouts and optional item sources")
        dependencies {

            name("PlaceholderAPI").optional(true)
            name("Ambience").optional(true)
            name("LootBeam").optional(true)
            name("PickupNotifier").optional(true)
            val providers = Properties().apply {
                rootProject.file("src/main/resources/itembridge-providers.properties").inputStream().use { load(it) }
            }
            providers.values.map { it.toString() }.sorted().forEach { name(it).optional(true) }
        }
    }
    env { install(Basic, Bukkit, BukkitUtil, BukkitNMS, I18n, MinecraftChat) }
    version { taboolib = "6.3.0-75b18a2" }
    relocate("cn.gtemc.itembridge", "online.toraka.dialogmenu.library.itembridge")
}

layout.buildDirectory.set(File(System.getProperty("user.home"), ".gradle-builds/${rootProject.name}"))

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.gtemc.net/releases/")
}

dependencies {
    implementation("cn.gtemc:itembridge:1.0.32") { isTransitive = false }
    add("taboo", "cn.gtemc:itembridge:1.0.32") { isTransitive = false }
    compileOnly("com.google.code.gson:gson:2.8.7")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-transport:4.2.15.Final")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.19.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.compileClasspath {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}
configurations.testCompileClasspath { attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
configurations.testRuntimeClasspath { attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
tasks.test { useJUnitPlatform() }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

val exportJar by tasks.registering(Copy::class) {
    dependsOn("taboolibMainTask")
    from(tasks.jar)
    into(layout.projectDirectory.dir("dist"))
    outputs.upToDateWhen { false }
}
tasks.named("assemble") { dependsOn(exportJar) }
apply(from = rootProject.file("gradle/source-quality.gradle"))
apply(from = rootProject.file("gradle/bundled-resourcepack.gradle"))
