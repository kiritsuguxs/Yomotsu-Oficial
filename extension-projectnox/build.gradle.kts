plugins {
    id("com.android.application") version "8.2.2"
    kotlin("android") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

android {
    namespace = "eu.kanade.tachiyomi.extension.pt.projectnox"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.pt.projectnox"
        minSdk = 21
        targetSdk = 34
        versionCode = 3
        versionName = "1.4.3"

        manifestPlaceholders["appName"] = "Tachiyomi: Project Nox"
        manifestPlaceholders["extClass"] = ".ProjectNox"
        manifestPlaceholders["extVersionCode"] = versionCode.toString()
        manifestPlaceholders["extFactory"] = ""
        manifestPlaceholders["nsfw"] = "0"
        manifestPlaceholders["sourceUrl"] = ""
        manifestPlaceholders["isNsfw"] = "false"
        manifestPlaceholders["libVersion"] = "1.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Mihon/Tachiyomi extension API
    compileOnly("com.github.tachiyomiorg:extensions-lib:1.4.4")

    // OkHttp
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")

    // JSoup
    compileOnly("org.jsoup:jsoup:1.17.2")

    // Kotlin serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
