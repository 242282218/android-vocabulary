plugins {
    id("androidvocab.feature.convention")
}

android {
    namespace = "com.zzz.androidvocab.feature.wordbook"
}

dependencies {
    testImplementation(project(":core:common"))
}
