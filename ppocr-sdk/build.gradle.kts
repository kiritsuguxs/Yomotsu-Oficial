plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
}

spotless {
    kotlin {
        // Keep the vendored upstream SDK byte-for-byte auditable against its pinned commit.
        targetExclude("src/main/java/com/paddle/ocr/**/*.kt")
    }
}

android {
    namespace = "com.paddle.ocr"

    buildTypes.named("release") {
        consumerProguardFiles("proguard-rules.pro")
    }
}

dependencies {
    implementation(libs.onnxruntime.android)
    // QuickBird explicitly marks 4.5.3 as having runtime issues on some Android versions.
    implementation("com.quickbirdstudios:opencv:4.5.3.0")
    implementation(libs.kotlinx.coroutines.android)
}
