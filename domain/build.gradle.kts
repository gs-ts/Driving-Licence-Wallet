import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The domain is pure Kotlin. Android is not on its classpath, so the compiler
// keeps the domain free of framework code.
plugins {
    `java-library`
    // Shares the fakes and the example data in src/testFixtures with the tests
    // of the other modules. They do not depend on a test library, so that the
    // Compose previews can use them as well.
    `java-test-fixtures`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

// The module targets the same bytecode level as the Android modules, without
// downloading a separate JDK toolchain.
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
    // Flow and other coroutine types are part of the domain's public API, in
    // the interfaces that the data layer implements.
    api(libs.kotlin.coroutines.core)

    // The shared detekt config has ktlint and Compose sections, so validating
    // it needs both plugins, even in this module without Compose.
    detektPlugins(libs.detekt.rules.ktlint.wrapper)
    detektPlugins(libs.detekt.compose.rules)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.app.cash.turbine)
}
