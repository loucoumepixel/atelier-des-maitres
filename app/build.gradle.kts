import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Le numéro de build GitHub Actions sert de numéro de version : il augmente à chaque
// publication, ce qu'exige le Play Store.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Clé d'envoi Play Store, fournie par les secrets GitHub (voir docs/play-store/README.md).
// Sans elle, l'APK est signé avec la clé de debug, ce qui suffit pour l'installer à la main.
val uploadKeystore = System.getenv("UPLOAD_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }
val uploadPassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")

android {
    namespace = "app.atelier.maitres"
    compileSdk = 36

    defaultConfig {
        // Identifiant définitif sur le Play Store : il ne pourra plus changer après la publication.
        applicationId = "io.github.loucoumepixel.atelierdesmaitres"
        minSdk = 28
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        if (uploadKeystore != null && uploadPassword != null) {
            create("upload") {
                storeFile = uploadKeystore
                storePassword = uploadPassword
                keyAlias = "upload"
                keyPassword = uploadPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    // Langue choisissable par application (Paramètres › Langues, Android 13+) : anglais, français.
    androidResources { generateLocaleConfig = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Installe le profil de démarrage même hors Play Store (APK des Releases).
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
