plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.abidnafi.pocketdeck"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.abidnafi.pocketdeck"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.6.0"
    }

    // Release signing key, supplied through environment variables (CI secrets or your shell).
    // Without them, release builds fall back to the debug key so they can still be side-loaded.
    val releaseKeystore = System.getenv("POCKETDECK_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("POCKETDECK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("POCKETDECK_KEY_ALIAS")
                keyPassword = System.getenv("POCKETDECK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // BouncyCastle (Moonlight engine) jars each ship the same LICENSE.md; same fix as upstream.
            excludes += "/META-INF/*.md"
        }
    }

    // Same NDK as the streaming engine; the tunnel library below is built with it.
    ndkVersion = "29.0.14206865"
    sourceSets.getByName("main").jniLibs.directories.add(layout.buildDirectory.dir("rustJniLibs").get().asFile.path)
}

// Play from anywhere: the Rust tunnel (rust/pocketdeck-tunnel-jni, built on Pocket-Deck-PC's
// crates/remote) as libpocketdeck_tunnel_jni.so. Needs Rust (rustup picks the toolchain from
// rust/rust-toolchain.toml) and cargo-ndk (`cargo install cargo-ndk`).
val tunnelCrate = rootProject.file("rust/pocketdeck-tunnel-jni")
val tunnelOut = layout.buildDirectory.dir("rustJniLibs")
val buildTunnel = tasks.register<Exec>("buildTunnelLib") {
    description = "Builds the play-from-anywhere tunnel library with cargo-ndk."
    workingDir = tunnelCrate
    inputs.files(fileTree(tunnelCrate.resolve("src")), tunnelCrate.resolve("Cargo.toml"))
    inputs.files(fileTree(rootProject.file("../Pocket-Deck-PC/crates/remote/src")))
    outputs.dir(tunnelOut)
    val ndk = androidComponents.sdkComponents.ndkDirectory
    doFirst { environment("ANDROID_NDK_HOME", ndk.get().asFile.absolutePath) }
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86_64",
        "-P", "26",
        "build", "--release",
    )
    // Only our library goes into the APK (cargo-ndk's -o would also copy iroh's own cdylibs).
    val abis = mapOf("arm64-v8a" to "aarch64-linux-android", "armeabi-v7a" to "armv7-linux-androideabi", "x86_64" to "x86_64-linux-android")
    val target = tunnelCrate.resolve("target")
    val out = tunnelOut.get().asFile
    doLast {
        out.deleteRecursively()
        for ((abi, triple) in abis) {
            val lib = target.resolve("$triple/release/libpocketdeck_tunnel_jni.so")
            lib.copyTo(out.resolve("$abi/${lib.name}"), overwrite = true)
        }
    }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach { dependsOn(buildTunnel) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":moonlight"))
    // Pair with one scan: camera preview + QR decoding (pure Java, no Play Services).
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
