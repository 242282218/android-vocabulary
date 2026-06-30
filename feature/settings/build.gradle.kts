plugins {
    id("androidvocab.feature.convention")
}

android {
    namespace = "com.zzz.androidvocab.feature.settings"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
}
