import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip

val appVersion = "1.0.0"

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.hot.reload)
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
            implementation(libs.compose.material.icons.core)
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
            implementation(libs.compose.desktop.current.os)
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
            packageName = "Screen Saver App"
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

tasks.register("runHot") {
    group = "compose hot reload"
    description = "Runs the Compose Hot Reload dev entry point."
    dependsOn("hotDevJvm")
}

tasks.register<Copy>("packageScr") {
    group = "distribution"
    description = "Builds the Windows .scr screen saver launcher from the jpackage executable."
    dependsOn("createDistributable")

    from(layout.buildDirectory.dir("compose/binaries/main/app/Screen Saver App"))
    from(layout.buildDirectory.file("compose/binaries/main/app/Screen Saver App/Screen Saver App.exe")) {
        rename { "ScreenSaverApp.scr" }
    }
    into(layout.buildDirectory.dir("compose/binaries/main/scr/Screen Saver App"))
}

tasks.register("verifyScrPackage") {
    group = "verification"
    description = "Verifies that the .scr package contains the launcher and adjacent runtime."
    dependsOn("packageScr")

    doLast {
        val scrDir = layout.buildDirectory.dir("compose/binaries/main/scr/Screen Saver App").get().asFile
        val scrFile = scrDir.resolve("ScreenSaverApp.scr")
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
