plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(javaxInject())
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

fun javaxInject(): String = "javax.inject:javax.inject:1"
