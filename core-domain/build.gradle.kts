plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core-protocol"))
    api(project(":core-transport"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

// A CLI -D does not cross into test JVMs; forward the sim-mesh ports
// explicitly so `gradlew :core-domain:test -Dmeshpigeon.sim.ports=…` works
// (mesh-sim tests skip when the property is absent, 09-testing §2).
tasks.withType<Test>().configureEach {
    System.getProperty("meshpigeon.sim.ports")?.let { systemProperty("meshpigeon.sim.ports", it) }
}
