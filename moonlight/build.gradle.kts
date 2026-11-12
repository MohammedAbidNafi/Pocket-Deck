// Moonlight streaming engine (GameStream client), vendored from moonlight-android.
// Engine only: no activities, views or resources. See UPSTREAM.md. Licensed GPLv3 (LICENSE.txt).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.limelight"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("boolean", "ROOT_BUILD", "false")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // CMake port of upstream's ndk-build files (ndk-build can't handle spaces in paths).
    // Non-root flavour only: the evdev reader is not built.
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.jcodec)
    api(libs.jmdns) // JmDNSDiscoveryAgent exposes jmDNS types
    implementation(libs.shield.controller.extensions)
    api(libs.okhttp)
}
