import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The simulated issuer backend. Pure Kotlin and independent of the other modules:
// :data calls it like a remote API and maps its types.
// No detekt: it only stands in for the issuer's server.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

// Same bytecode level as the Android modules, without a separate JDK toolchain to download.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    // delay() for the simulated latency.
    implementation(libs.kotlin.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlin.coroutines.test)
}
