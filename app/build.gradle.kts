plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.joaoeoneves.fintrack"
    compileSdk {
        version =
            release(36) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "com.joaoeoneves.fintrack"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Exposes this dev's local (gitignored) google-services.json values to JVM unit tests only,
        // so FirebaseTestApp can construct a real (network-inert) FirebaseApp without hardcoding a
        // copy of the project's Firebase client identifiers in source. Empty when the file is absent
        // (e.g. a fresh checkout before running Firebase setup) rather than failing the build.
        val googleServicesFile = rootProject.file("app/google-services.json")
        val googleServices =
            if (googleServicesFile.exists()) {
                groovy.json.JsonSlurper().parse(googleServicesFile) as Map<*, *>
            } else {
                null
            }
        val client = (googleServices?.get("client") as? List<*>)?.firstOrNull() as? Map<*, *>
        val projectInfo = googleServices?.get("project_info") as? Map<*, *>
        val clientInfo = client?.get("client_info") as? Map<*, *>
        val apiKey = (client?.get("api_key") as? List<*>)?.firstOrNull() as? Map<*, *>
        buildConfigField("String", "TEST_FIREBASE_PROJECT_ID", "\"${projectInfo?.get("project_id") ?: ""}\"")
        buildConfigField(
            "String",
            "TEST_FIREBASE_APPLICATION_ID",
            "\"${clientInfo?.get("mobilesdk_app_id") ?: ""}\"",
        )
        buildConfigField("String", "TEST_FIREBASE_API_KEY", "\"${apiKey?.get("current_key") ?: ""}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    // The baseline profile plugin clones `release` into nonMinifiedRelease/benchmarkRelease, which
    // would be unsigned (release has no signingConfig). Sign only those generated types with the
    // debug key so the generator can install them; `release` itself is untouched.
    buildTypes.matching { it.name in setOf("nonMinifiedRelease", "benchmarkRelease") }.configureEach {
        signingConfig = signingConfigs.getByName("debug")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        // Hand-written repository fakes shared by JVM unit tests and instrumented tests, kept out
        // of the production APK.
        getByName("test").java.srcDir("src/test/shared/java")
        getByName("androidTest").java.srcDir("src/test/shared/java")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Strong skipping (default since Kotlin 2.0.20) already lets composables with unstable params skip
// via instance equality; the stability config additionally lets equal-but-new instances of our
// known-immutable types skip via equals(). Reports/metrics are opt-in: -PcomposeReports=true.
composeCompiler {
    if (providers.gradleProperty("composeReports").orNull == "true") {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

ktlint {
    android.set(true)
}

// Profile generation is manual (`:app:generateBaselineProfile` with a connected device); normal
// builds never trigger it. Output is committed under src/main/generated/baselineProfiles.
baselineProfile {
    mergeIntoMain = true
    saveInSrc = true
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(file("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    source.setFrom(
        files("src/main/java", "src/main/kotlin", "src/test/java", "src/test/kotlin", "src/test/shared/java"),
    )
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}
