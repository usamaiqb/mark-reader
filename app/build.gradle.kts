import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

// Load signing config from keystore.properties (local) or environment variables (CI)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val releaseStoreFile: File? = when {
    keystorePropertiesFile.exists() -> rootProject.file(keystoreProperties["storeFile"] as String)
    System.getenv("KEYSTORE_PATH") != null -> file(System.getenv("KEYSTORE_PATH")!!)
    else -> null
}
val releaseStorePassword: String? = keystoreProperties["storePassword"] as? String ?: System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = keystoreProperties["keyAlias"] as? String ?: System.getenv("KEY_ALIAS")
val releaseKeyPassword: String? = keystoreProperties["keyPassword"] as? String ?: System.getenv("KEY_PASSWORD")

android {
    namespace = "com.markreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.markreader"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        // Suppressions and severities live in lint.xml so the IDE, the CLI and CI all read
        // the same policy. Warnings are not errors yet — see the note in that file.
        lintConfig = file("lint.xml")
    }

    testOptions {
        unitTests {
            // The JVM tests below cover logic that only touches Android types at
            // its edges; stubs returning defaults keep them from throwing there.
            isReturnDefaultValues = true
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs (required for F-Droid reproducible builds)
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    // Findings that predate detekt, so the rules bind to new code without the change that
    // introduced them rewriting the app. This file is meant to shrink and then be deleted —
    // regenerating it wholesale during a refactor turns a temporary debt into a permanent one.
    baseline = file("$rootDir/config/detekt/baseline.xml")
    // Our config only overrides thresholds, so the rest of detekt's defaults still apply.
    buildUponDefaultConfig = true
    allRules = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_17.toString()
    reports {
        html.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
        txt.required.set(false)
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains:annotations:${libs.versions.jetbrainsAnnotations.get()}")
    }
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.google.material)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.syntax.highlight)
    implementation(libs.markwon.image) {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation(libs.prism4j)
    kapt(libs.prism4j.bundler)

    implementation(libs.androidx.core.splashscreen)

    detektPlugins(libs.detekt.compose.rules)

    testImplementation(libs.junit)
}
