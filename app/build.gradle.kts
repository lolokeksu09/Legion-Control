plugins { id("com.android.application") }

// The private release keystore is never checked into this repository.
// The release workflow restores it from GitHub Actions repository secrets.
val releaseStore = System.getenv("LEGION_RELEASE_STORE_FILE")
val releaseStorePass = System.getenv("LEGION_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("LEGION_RELEASE_KEY_ALIAS")
val releaseKeyPass = System.getenv("LEGION_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStore, releaseStorePass, releaseKeyAlias, releaseKeyPass
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.lolokeksu.legioncontrol"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.lolokeksu.legioncontrol"
        minSdk = 33
        targetSdk = 36
        versionCode = 26
        versionName = "0.1.0-alpha.26"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("legionRelease") {
                storeFile = file(releaseStore!!)
                storePassword = releaseStorePass!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPass!!
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("legionRelease")
            }
        }
    }
}

// Never silently distribute an unsigned APK as a release artifact.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.path == ":app:assembleRelease" } && !hasReleaseSigning) {
        throw GradleException(
            "Release signing secrets missing; configure LEGION_RELEASE_* or build assembleDebug"
        )
    }
}

// Pure JVM regressions for CSV integrity and Russian quantity formatting.
dependencies {
    // Stable AndroidX Views/Fragments navigation: no Compose compiler or duplicate backend.
    implementation("androidx.navigation:navigation-fragment:2.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.1")
    implementation("androidx.lifecycle:lifecycle-livedata:2.11.1")
    testImplementation("junit:junit:4.13.2")
}
