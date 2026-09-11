plugins {
    id("com.android.application") version "8.2.2"
    kotlin("android") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

val extVersionCode = 3
val libVersion = "1.6"

val calculatedVersionCode = libVersion.split(".")
    .joinToString("") { it.padStart(2, '0') }
    .toInt() * 1000 + extVersionCode

android {
    namespace = "eu.kanade.tachiyomi.extension.pt.nhentaibr"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.pt.nhentaibr"
        minSdk = 21
        targetSdk = 34
        versionCode = calculatedVersionCode
        versionName = "$libVersion.$extVersionCode"

        manifestPlaceholders["appName"] = "Tachiyomi: Nhentai BR"
        manifestPlaceholders["extClass"] = ".NhentaiBr"
        manifestPlaceholders["extVersionCode"] = extVersionCode.toString()
        manifestPlaceholders["extFactory"] = ""
        manifestPlaceholders["nsfw"] = "1"
        manifestPlaceholders["sourceUrl"] = ""
        manifestPlaceholders["isNsfw"] = "true"
        manifestPlaceholders["libVersion"] = libVersion
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
    compileOnly("com.github.keiyoushi:extensions-lib:42255ee5fa")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("org.jsoup:jsoup:1.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
