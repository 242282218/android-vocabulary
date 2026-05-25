import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.gradle.api.GradleException

val appVersionCode: Int =
    providers.gradleProperty("androidVocab.versionCode").get().toInt()
val appVersionName: String =
    providers.gradleProperty("androidVocab.versionName").get()
val releaseStoreFile = providers.environmentVariable("ANDROID_VOCAB_RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("ANDROID_VOCAB_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_VOCAB_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_VOCAB_RELEASE_KEY_PASSWORD")
val releaseSigningInputs =
    listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    )
val isReleaseSigningConfigured = releaseSigningInputs.all { it.isPresent }

if (releaseSigningInputs.any { it.isPresent } && !isReleaseSigningConfigured) {
    throw GradleException(
        "Incomplete release signing environment. Required: " +
            "ANDROID_VOCAB_RELEASE_STORE_FILE, " +
            "ANDROID_VOCAB_RELEASE_STORE_PASSWORD, " +
            "ANDROID_VOCAB_RELEASE_KEY_ALIAS, " +
            "ANDROID_VOCAB_RELEASE_KEY_PASSWORD.",
    )
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zzz.androidvocab"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zzz.androidvocab"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    if (isReleaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            if (isReleaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel2Api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp"
                }
            }
        }
    }

    applicationVariants.all {
        outputs.all {
            val variantName = name
            (this as BaseVariantOutputImpl).outputFileName =
                "AndroidVocabulary-$variantName-v$appVersionName-$appVersionCode.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

fun registerRootApkCopyTask(variantName: String) {
    val capitalizedVariantName = variantName.replaceFirstChar { it.uppercase() }
    val sourceDir = layout.buildDirectory.dir("outputs/apk/$variantName")
    val rootApkName = "AndroidVocabulary-v$appVersionName-$appVersionCode-$variantName.apk"

    val copyTask =
        tasks.register("copy${capitalizedVariantName}ApkToRoot") {
            group = "distribution"
            description = "Copy the $variantName APK to the project root with a versioned name."

            doLast {
                val apkFiles =
                    fileTree(sourceDir) {
                        include("*.apk")
                    }.files

                require(apkFiles.size == 1) {
                    "Expected exactly one $variantName APK in ${sourceDir.get().asFile}, found ${apkFiles.size}."
                }

                copy {
                    from(apkFiles.single())
                    into(rootProject.layout.projectDirectory)
                    rename { rootApkName }
                }
            }
        }

    tasks.matching { task -> task.name == "assemble$capitalizedVariantName" }.configureEach {
        finalizedBy(copyTask)
    }

    tasks.register("package${capitalizedVariantName}ApkToRoot") {
        group = "distribution"
        description = "Build and copy the $variantName APK to the project root."
        dependsOn("assemble$capitalizedVariantName")
    }
}

registerRootApkCopyTask("debug")

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:vocabulary"))
    implementation(project(":core:stats"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:today"))
    implementation(project(":feature:review"))
    implementation(project(":feature:wordbook"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:settings"))
    implementation(project(":worker"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
