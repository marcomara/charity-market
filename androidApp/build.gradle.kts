import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val signingProperties = Properties().apply {
    val file = rootProject.file("android-signing.properties")

    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)

    implementation(libs.compose.foundation)
    implementation(compose.materialIconsExtended)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    implementation(libs.ktor.client.cio)
    implementation(libs.kotlinx.coroutinesAndroid)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    testImplementation(libs.junit)
}

android {
    namespace = "it.charitymarket.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "it.charitymarket.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(
                signingProperties.getProperty("storeFile")
            )

            storePassword =
                signingProperties.getProperty("storePassword")

            keyAlias =
                signingProperties.getProperty("keyAlias")

            keyPassword =
                signingProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
