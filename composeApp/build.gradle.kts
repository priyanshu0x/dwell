import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip

val appVersion = "1.0.0"
val appDisplayName = "Screen Saver App"
val scrLauncherName = "$appDisplayName.scr"

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
    id("org.jetbrains.compose.hot-reload")
}
kotlin {
    jvmToolchain(21)

    jvm()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.animation)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.ui.tooling.preview)
            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kstore)
            implementation(libs.charleskorn.kaml)
            implementation(project(":widget-api"))
            // Koin for dependency injection
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.bundles.koin)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }

    }
}
compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = appDisplayName
            packageVersion = appVersion
            description = "Idle-triggered desktop dashboard screen saver with widget support"
            copyright = "© 2026 DroidsLife"
            vendor = "DroidsLife"

            // JVM options
            modules("java.sql")
            jvmArgs("-Xms256m", "-Xmx2g")

            linux {
                iconFile.set(project.file("desktopAppIcons/LinuxIcon.png"))
                packageName = "screensaver-app"
                debMaintainer = "support@droidslife.com"
                menuGroup = "Accessories"
            }

            windows {
                iconFile.set(project.file("desktopAppIcons/WindowsIcon.ico"))
                // Windows-specific settings
                dirChooser = true
                perUserInstall = true
                menuGroup = "Screen Savers"
                upgradeUuid = "9007D6F4-70B0-46F3-BD6A-51E26F103C9A"
                // Create a shortcut in the Windows Start menu
                shortcut = true
                // Create a desktop shortcut
                menu = true
            }
        }
    }
}

tasks.configureEach {
    if (name == "createDistributable") {
        doFirst {
            delete(layout.buildDirectory.dir("compose/binaries/main/app"))
        }
    }
}

tasks.register("runHot") {
    group = "compose hot reload"
    description = "Runs the Compose Hot Reload dev entry point."
    dependsOn("hotDevJvm")
}

// --- BuildInfo generation -----------------------------------------------------
// Produces composeApp/build/generated/buildInfo/.../BuildInfo.kt so the About
// screen can show the real version + commit hash without hardcoding.
val buildInfoDir = layout.buildDirectory.dir("generated/buildInfo/commonMain/kotlin")

// Eagerly resolve script-object refs at configuration time so the task action
// only captures plain serializable types (config cache friendly).
val resolvedAppVersion = appVersion
val resolvedRootDir = rootDir
val resolvedBuildEpoch = System.currentTimeMillis() / 1000L

val generateBuildInfo by tasks.registering {
    group = "build"
    description = "Writes BuildInfo.kt with version, commit hash, and build timestamp."
    inputs.property("version", resolvedAppVersion)
    inputs.property("epoch", resolvedBuildEpoch)
    outputs.dir(buildInfoDir)

    val outDirFile = buildInfoDir.get().asFile
    val rootFile = resolvedRootDir
    val version = resolvedAppVersion
    val epoch = resolvedBuildEpoch

    doLast {
        val commit = runCatching {
            val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(rootFile)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readText().trim().ifEmpty { "dev" }
        }.getOrDefault("dev")
        val pkgDir = outDirFile.resolve("com/droidslife/screensaver")
        pkgDir.mkdirs()
        pkgDir.resolve("BuildInfo.kt").writeText(
            """
            // Auto-generated by the `generateBuildInfo` Gradle task. Do not edit.
            package com.droidslife.screensaver

            object BuildInfo {
                const val VERSION: String = "$version"
                const val COMMIT: String = "$commit"
                const val BUILD_EPOCH_S: Long = ${epoch}L
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(buildInfoDir)
        }
    }
}

tasks.matching { it.name.startsWith("compileKotlin") || it.name == "sourcesJar" }
    .configureEach { dependsOn(generateBuildInfo) }

tasks.register<Copy>("packageScr") {
    group = "distribution"
    description = "Builds the Windows .scr screen saver launcher from the jpackage executable."
    dependsOn("createDistributable")

    doFirst {
        delete(layout.buildDirectory.dir("compose/binaries/main/scr"))
    }

    from(layout.buildDirectory.dir("compose/binaries/main/app/$appDisplayName"))
    from(layout.buildDirectory.file("compose/binaries/main/app/$appDisplayName/$appDisplayName.exe")) {
        rename { scrLauncherName }
    }
    into(layout.buildDirectory.dir("compose/binaries/main/scr/$appDisplayName"))
}

tasks.register("verifyScrPackage") {
    group = "verification"
    description = "Verifies that the .scr package contains the launcher and adjacent runtime."
    dependsOn("packageScr")

    doLast {
        val scrDir = layout.buildDirectory.dir("compose/binaries/main/scr/$appDisplayName").get().asFile
        val scrFile = scrDir.resolve(scrLauncherName)
        val runtimeDir = scrDir.resolve("runtime")
        val appDir = scrDir.resolve("app")

        if (!scrFile.isFile) {
            throw GradleException("Missing screensaver launcher: ${scrFile.absolutePath}")
        }
        if (!runtimeDir.isDirectory) {
            throw GradleException("Missing packaged runtime beside .scr: ${runtimeDir.absolutePath}")
        }
        if (!appDir.isDirectory) {
            throw GradleException("Missing packaged app directory beside .scr: ${appDir.absolutePath}")
        }
    }
}

tasks.register<Zip>("packageScrZip") {
    group = "distribution"
    description = "Builds a zip containing the full Windows .scr screensaver bundle."
    dependsOn("verifyScrPackage")

    archiveFileName.set("ScreenSaverApp-scr-$appVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main"))
    from(layout.buildDirectory.dir("compose/binaries/main/scr")) {
        into("ScreenSaverApp-scr")
    }
}
