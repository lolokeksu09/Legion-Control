plugins { id("com.android.application") }

android {
    namespace = "dev.lolokeksu.legioncontrol"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.lolokeksu.legioncontrol"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha.1"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
