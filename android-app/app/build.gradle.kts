import com.google.gms.googleservices.GoogleServicesTask

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Firebase Google Services plugin for analytics, crashlytics etc.
    id("com.google.gms.google-services")
}

android {
    namespace = "com.menumanager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.menumanager"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the default debug keystore so we can ship a generically signed artifact.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            // applicationIdSuffix rimosso per compatibilità con google-services.json
            // applicationIdSuffix = ".debug"
        }
    }

    // NOTE: AGP 8.x supporta attualmente JDK 17 per il build. Restiamo su 17 anche se il runtime desiderato sarebbe 21.
    // Quando AGP dichiarerà compatibilità con JDK 21 potremo alzare questi valori.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.datastore:datastore-preferences:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Firebase BoM per versioni allineate.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    // Servizi base (Auth anon/email) - opzionale se usiamo solo Firestore pubblico protetto da regole.
    implementation("com.google.firebase:firebase-auth-ktx")
    // Firestore per dati realtime con snapshot listener
    implementation("com.google.firebase:firebase-firestore-ktx")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

    // Workaround per cartelle OneDrive: evita che il task fallisca quando i file generati sono reparse point
    tasks.withType<GoogleServicesTask>().configureEach {
        doNotTrackState("OneDrive può marcare i file generati come reparse point e impedirne lo snapshot")
    }
