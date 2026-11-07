plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.detekt)
}

android {
    namespace = "gts.drivinglicencewallet.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

room { // https://developer.android.com/jetpack/androidx/releases/room#gradle-plugin
    schemaDirectory("$projectDir/schemas")
}

detekt {
    source.setFrom("src/main/java", "src/main/kotlin")
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    allRules = true
    parallel = true
    buildUponDefaultConfig = true
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":simulated-backend"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.sqlcipher.android)
    implementation(libs.tink.android)
    implementation(libs.androidx.work.manager)
    implementation(libs.androidx.hilt.work)
    implementation(libs.hilt.android)
    implementation(libs.timber)

    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.room.compiler)

    // The shared detekt config has ktlint and Compose sections; config validation needs both plugins.
    detektPlugins(libs.detekt.rules.ktlint.wrapper)
    detektPlugins(libs.detekt.compose.rules)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlin.coroutines.test)
}
