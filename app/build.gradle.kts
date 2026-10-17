plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1_000_001
val appVersionName = providers.gradleProperty("versionName").orNull ?: "1.0.1"
val releaseStorePath = System.getenv("PITCHEE_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("PITCHEE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("PITCHEE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("PITCHEE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
// Precedence: an explicit -Ppitchee.device.serial, then the device selected in
// Android Studio, then ANDROID_SERIAL. With none of them the script prepares
// every connected device, which is what makes this work with several devices
// plugged in.
val targetSerial = listOf(
    providers.gradleProperty("pitchee.device.serial").orNull,
    providers.gradleProperty("android.injected.device.serial").orNull,
    providers.environmentVariable("ANDROID_SERIAL").orNull,
).firstOrNull { !it.isNullOrBlank() }

val keepDataUninstall by tasks.registering(Exec::class) {
    group = "install"
    description = "Uninstalls Pitchee while retaining app data before installing a new build."
    commandLine("bash", rootProject.file("scripts/keep-data-uninstall.sh").absolutePath)
    if (targetSerial != null) {
        environment("ANDROID_SERIAL", targetSerial)
    }
}

tasks.matching { it.name == "installDebug" || it.name == "installRelease" }.configureEach {
    dependsOn(keepDataUninstall)
}

val invokedFromIde = providers.gradleProperty("android.injected.invoked.from.ide").orNull == "true" ||
    providers.systemProperty("android.injected.invoked.from.ide").orNull == "true"
if (invokedFromIde && !targetSerial.isNullOrBlank()) {
    tasks.matching { it.name == "assembleDebug" }.configureEach {
        dependsOn(keepDataUninstall)
    }
}

android {
    namespace = "io.rovly.pitchee"
    compileSdk {
        version = release(37)
    }
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "io.rovly.pitchee"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DPITCHEE_CORE_DIR=${rootProject.projectDir}/third_party/Pitchee-core",
                    "-DPITCHEE_NCNN_ROOT=${rootProject.projectDir}/third_party/ncnn-android",
                    "-DANDROID_STL=c++_static",
                )
                cppFlags += "-std=c++17"
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets["main"].assets.directories.add(
        "${rootProject.projectDir}/third_party/Pitchee-core/models"
    )

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.markwon.core)
    implementation(libs.markwon.latex)
    implementation(libs.zentone)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
