plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.ktlint)
}

// Android nimmt eine APK nur dann als Update an, wenn ihre Nummer hoeher ist als die der
// installierten. Veroeffentlichte Builds bekommen deshalb die Laufnummer der CI; lokal und
// in den Pruef-Jobs bleibt es bei 1, damit der Build-Cache nicht bei jedem Lauf verfaellt.
val buildNumber = System.getenv("REZEPTLI_BUILD_NUMBER")?.toIntOrNull()?.takeIf { it > 0 } ?: 1

android {
    namespace = "ch.rezeptli.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ch.rezeptli.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.1.0"

        // Adresse des Pairing-Dienstes. Nur der Mehrspieler-Modus nutzt sie;
        // alles andere in der App kommt ohne Server aus.
        buildConfigField(
            "String",
            "PAIRING_URL",
            "\"https://rezeptli-pairing.rezeptli.workers.dev\"",
        )

        // Das taeglich gebaute Rezeptverzeichnis. Es liegt unter der Website,
        // nicht an deren Wurzel - dort steht die Startseite. Ist der Index nicht
        // erreichbar, holt die App die Sitemaps wie bisher selbst.
        buildConfigField(
            "String",
            "INDEX_URL",
            "\"https://rezeptli.app/index\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Der Test-Schluessel liegt im Projekt, damit jeder Build dieselbe Unterschrift traegt.
    // Vorher erzeugte jede CI-Maschine ihren eigenen Wegwerf-Schluessel: Android hielt jede
    // neue APK fuer eine fremde App und verlangte, die alte samt ihren Daten zu loeschen.
    // Er ist bewusst oeffentlich und schuetzt nichts - ein Release fuer einen Store braucht
    // einen eigenen, geheimen Schluessel (siehe docs/adr/0009).
    signingConfigs {
        getByName("debug") {
            storeFile = file("rezeptli-test.keystore")
            storePassword = "rezeptli-test"
            keyAlias = "rezeptli-test"
            keyPassword = "rezeptli-test"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    // Die exportierten Schemas liegen als Assets im androidTest-Quellsatz, damit der
    // Migrationstest gegen die tatsaechliche Vorgaengerversion laufen kann.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    // Room schreibt das Schema waehrend der Kompilierung nach schemas/, die Assets
    // der Geraetetests werden davon unabhaengig zusammengestellt. Ohne diese
    // Abhaengigkeit entscheidet der Zufall, was zuerst laeuft - und der
    // Migrationstest fand die Datei zur naechsten Datenbankfassung nicht.
    tasks.configureEach {
        if (name == "mergeDebugAndroidTestAssets") dependsOn("kspDebugKotlin")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

ktlint {
    version.set(libs.versions.ktlint.get())
    android.set(true)
    ignoreFailures.set(false)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    // Einstellungen und Onboarding-Antworten - schlanker als eine Tabelle,
    // und die Werte werden als Flow beobachtbar.
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.coil.compose)

    // Web-Import: HTML lesen (jsoup) und die eingebetteten strukturierten Daten
    // auswerten (kotlinx-serialization, nur die Laufzeit ohne Codegen).
    implementation(libs.jsoup)

    // Uebersetzt auf dem Geraet. Das Sprachmodell (rund 30 MB je Paar) holt die
    // App beim ersten Bedarf ueber WLAN nach - im APK steckt es nicht.
    implementation(libs.mlkit.translate)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
