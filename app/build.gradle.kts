plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.secrets.gradle.plugin)
}

android {
    namespace = "gts.drivinglicensewallet"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "gts.drivinglicensewallet"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
        aidl = false
        resValues = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.collections)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.core)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.accompanist.permissions)

    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)

    detektPlugins(libs.detekt.rules.ktlint.wrapper)
    detektPlugins(libs.detekt.compose.rules)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.androidx.viewmodel.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.app.cash.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
