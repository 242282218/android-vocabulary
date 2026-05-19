plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.fsrs)
    implementation(javaxInject())
    testImplementation(libs.junit)
}

fun javaxInject(): String = "javax.inject:javax.inject:1"
