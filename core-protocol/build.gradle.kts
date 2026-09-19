plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The protocol layer must compile on the plain JVM with no Android SDK
// (guiding principle 5) — this is what makes it unit-testable at 90 %+.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bouncycastle)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
