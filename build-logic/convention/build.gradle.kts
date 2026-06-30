plugins {
    `kotlin-dsl`
}

group = "com.zzz.androidvocab.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("featureConvention") {
            id = "androidvocab.feature.convention"
            implementationClass = "com.zzz.androidvocab.convention.FeatureConventionPlugin"
        }
    }
}
