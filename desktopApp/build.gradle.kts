import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.saubh.deskbuddy.MainKt"
        // Tray-resident app: cap the heap and use the low-footprint collector.
        jvmArgs += listOf("-Xms32m", "-Xmx256m", "-XX:+UseSerialGC")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            // From ./gradlew :desktopApp:suggestRuntimeModules (Ktor Netty, JNA, StartupRegistration).
            modules("java.instrument", "java.management", "jdk.jfr", "jdk.unsupported")
            packageName = "KonneKt"
            packageVersion = "1.0.0"
            // Per-OS files next to the app (read through compose.application.resources.dir): macos/ holds mediaremote-adapter.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("icons/deskbuddy.ico"))
                menuGroup = "KonneKt"
                shortcut = true
            }
            macOS {
                iconFile.set(project.file("icons/deskbuddy.icns"))
                bundleID = "com.saubh.konnekt"
                infoPlist {
                    // Run as a menu-bar app, without a Dock icon. The usage string enables Apple Events.
                    extraKeysRawXml = """
                        <key>LSUIElement</key>
                        <true/>
                        <key>NSAppleEventsUsageDescription</key>
                        <string>KonneKt controls music, windows and power on this Mac when you ask from your phone.</string>
                    """.trimIndent()
                }
            }
            linux {
                iconFile.set(project.file("icons/deskbuddy.png"))
            }
        }
    }
}

val signMacApp = tasks.register<Exec>("signMacApp") {
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/KonneKt.app")
    val dmgFile = layout.buildDirectory.file("compose/binaries/main/dmg/KonneKt-1.0.0.dmg")

    onlyIf {
        System.getProperty("os.name").lowercase().contains("mac")
    }

    commandLine(
        "sh",
        "-c",
        "codesign -f --deep -s - '${appDir.get().asFile.absolutePath}' 2>/dev/null || true; [ -f '${dmgFile.get().asFile.absolutePath}' ] && codesign -f -s - '${dmgFile.get().asFile.absolutePath}' 2>/dev/null || true"
    )
}

afterEvaluate {
    tasks.findByName("packageDmg")?.let { pkgTask ->
        signMacApp.configure {
            mustRunAfter(pkgTask)
        }
        pkgTask.finalizedBy(signMacApp)
    }
    tasks.findByName("createDistributable")?.let { distTask ->
        signMacApp.configure {
            mustRunAfter(distTask)
        }
        distTask.finalizedBy(signMacApp)
    }
}
