import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
        }

    }
}
compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "Screen Saver App"
            packageVersion = "1.0.0"
            description = "A modern flip clock screen saver application"
            copyright = "© 2025 DroidsLife"
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

            macOS {
                iconFile.set(project.file("desktopAppIcons/MacosIcon.icns"))
                bundleID = "com.droidslife.screensaver.desktopApp"
                appCategory = "public.app-category.utilities"
                signing {
                    sign.set(false)
                }
            }
        }
    }
}

tasks.register("runHot") {
    group = "compose hot reload"
    description = "Runs the Compose Hot Reload dev entry point."
    dependsOn("hotDevJvm")
}
