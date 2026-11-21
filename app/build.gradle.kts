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
        versionCode = 6
        versionName = "0.1.0-alpha.6"
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
    testImplementation("junit:junit:4.13.2")
}
