import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class ExtractOnnxRuntime @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archives: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun extract() {
        fileSystemOperations.sync {
            into(outputDirectory)
            archives.files.forEach { archive ->
                from(archiveOperations.zipTree(archive)) {
                    include("headers/**")
                    include("jni/arm64-v8a/libonnxruntime.so")
                    include("jni/x86_64/libonnxruntime.so")
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val onnxRuntimeRoot = layout.buildDirectory.dir("onnxruntime").get().asFile
val appVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1
val appVersionName = providers.gradleProperty("versionName").orNull ?: "1.0"
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
val onnxRuntimeAar by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    onnxRuntimeAar(libs.onnxruntime.android)
    implementation(libs.onnxruntime.android)
}

val prepareOnnxRuntime by tasks.registering(ExtractOnnxRuntime::class) {
    description = "Extracts ONNX Runtime headers and native libraries from the Android AAR."
    archives.from(onnxRuntimeAar)
    outputDirectory.set(onnxRuntimeRoot)
}

tasks.matching {
    it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake")
}.configureEach {
    dependsOn(prepareOnnxRuntime)
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
                    "-DPITCHEE_ORT_ROOT=$onnxRuntimeRoot",
                    "-DANDROID_STL=c++_shared",
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
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
