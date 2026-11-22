import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.geofilho.dominogjieshu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.geofilho.dominogjieshu"
        minSdk = 26
        targetSdk = 35
        // No GitHub Actions cada build ganha um número maior, permitindo atualizar por cima.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        resourceConfigurations += listOf("pt", "pt-rBR")
    }

    // No GitHub Actions o APK é assinado sempre com a mesma chave (guardada no cache do
    // Actions), para poder atualizar por cima. Localmente usa a chave de debug.
    val keystorePath = System.getenv("DOMINOG_KEYSTORE")
    if (keystorePath != null) {
        signingConfigs.create("fixa") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("DOMINOG_KEYSTORE_PASS")
            keyAlias = "dominog"
            keyPassword = System.getenv("DOMINOG_KEYSTORE_PASS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Para publicar na Play Store, use uma keystore própria guardada em segredo.
            signingConfig = signingConfigs.findByName("fixa") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// O estado da rodada é um objeto mutável; sem "strong skipping" o Compose sempre
// redesenha as partes que recebem a rodada, evitando telas desatualizadas.
composeCompiler {
    featureFlags = setOf(ComposeFeatureFlag.StrongSkipping.disabled())
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
