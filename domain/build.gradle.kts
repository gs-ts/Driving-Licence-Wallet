import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: Android isn't on the classpath, so the compiler keeps the domain framework-free.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
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

detekt {
    source.setFrom("src/main/java", "src/main/kotlin")
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    allRules = true
    parallel = true
    buildUponDefaultConfig = true
}

dependencies {
    // Flow and other coroutine types are part of the domain's public API (the ports).
    api(libs.kotlin.coroutines.core)

    // The shared detekt config has ktlint and Compose sections; config validation needs both plugins.
    detektPlugins(libs.detekt.rules.ktlint.wrapper)
    detektPlugins(libs.detekt.compose.rules)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.app.cash.turbine)
}
