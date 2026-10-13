import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Moteur pur Kotlin (aucune dépendance Android) : découpage des toiles en zones,
// état de la peinture, historique et sérialisation. Testable sur JVM en quelques secondes.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
