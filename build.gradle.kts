import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.owasp.dependency.check) apply true
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    if (!file("src/androidTest").exists()) {
        tasks
            .matching { task ->
                task.name.startsWith("connected") &&
                    task.name.endsWith("AndroidTest")
            }.configureEach {
                enabled = false
            }
    }

    extensions.configure<DetektExtension>("detekt") {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("detekt.yml"))
    }

    tasks.withType<Test>().configureEach {
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            // Avoid a reproducible HotSpot C2 crash in Room/Robolectric unit tests on Windows JDK 17.
            jvmArgs("-XX:TieredStopAtLevel=1")
        }
    }
}

val nvdApiKeyProvider =
    providers
        .gradleProperty("nvdApiKey")
        .orElse(providers.environmentVariable("NVD_API_KEY"))

configure<DependencyCheckExtension> {
    failBuildOnCVSS = 7.0f
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.absolutePath
    formats = listOf("HTML", "JSON")
    scanConfigurations = listOf("runtimeClasspath", "releaseRuntimeClasspath")
    val nvdApiKey = nvdApiKeyProvider.orNull
    if (!nvdApiKey.isNullOrBlank()) {
        nvd.apiKey = nvdApiKey
    }
    val dependencyCheckSuppressions = file("owasp-suppressions.xml")
    if (dependencyCheckSuppressions.exists()) {
        suppressionFile = dependencyCheckSuppressions.absolutePath
    }
}

tasks
    .matching { task ->
        task.name in
            setOf(
                "dependencyCheckAggregate",
                "dependencyCheckAnalyze",
                "dependencyCheckUpdate"
            )
    }.configureEach {
        doFirst {
            if (nvdApiKeyProvider.orNull.isNullOrBlank()) {
                throw GradleException(
                    "NVD API key is required for $name. Set NVD_API_KEY or pass -PnvdApiKey=<key>."
                )
            }
        }
    }
