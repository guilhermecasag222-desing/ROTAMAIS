plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "br.com.rotamais"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.rotamais"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    /**
     * Chave fixa do projeto. Sem ela, cada build no GitHub Actions roda numa maquina
     * nova, o Gradle gera uma chave de debug diferente e o Android recusa instalar
     * por cima da versao anterior ("App nao instalado", sem explicar o motivo).
     * O CI gera o arquivo na primeira vez; depois ele fica versionado no repositorio.
     */
    signingConfigs {
        create("rotamais") {
            val chave = rootProject.file("chave-rotamais.jks")
            if (chave.exists()) {
                storeFile = chave
                storePassword = "rotamais"
                keyAlias = "rotamais"
                keyPassword = "rotamais"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            // APK depuravel e um dos sinais que mais irrita Play Protect e Auto Blocker.
            isDebuggable = false
            signingConfig = signingConfigs.getByName("rotamais").takeIf { it.storeFile != null }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")

    // OCR das etiquetas. Roda 100% no aparelho, offline, sem chave e sem custo.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Camera para o modo scanner continuo.
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
