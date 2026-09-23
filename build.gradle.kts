import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import io.izzel.taboolib.gradle.*

plugins {
    kotlin("jvm") version "2.4.10"
    id("io.izzel.taboolib") version "2.0.38"
}

taboolib {
    description {
        name("PlayerSettings")
        desc("Player settings with custom Dialog layout and live personal controls")
        dependencies {

            name("PlaceholderAPI").optional(true)
            name("Ambience").optional(true)
            name("LootBeam").optional(true)
            name("PickupNotifier").optional(true)
        }
    }
    env { install(Basic, Bukkit, BukkitUtil, I18n, MinecraftChat) }
    version { taboolib = "6.3.0-75b18a2" }
}

layout.buildDirectory.set(File(System.getProperty("user.home"), ".gradle-builds/${rootProject.name}"))

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.google.code.gson:gson:2.8.7")
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")
    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))
    testImplementation("io.papermc.paper:paper-api:26.2.build.123-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
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
