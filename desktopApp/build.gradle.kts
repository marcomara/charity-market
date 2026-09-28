import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.ktor.client.cio)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "it.charitymarket.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "CharityMarket"
            packageVersion = "1.0.3"
            description = "Charity Market inventory and sales application"
            vendor = "Marco Marannino"

            includeAllModules = true

            appResourcesRootDir.set(
                project.layout.projectDirectory.dir(
                    "app-resources"
                )
            )

            windows{
                menuGroup = "Charity Market"
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
