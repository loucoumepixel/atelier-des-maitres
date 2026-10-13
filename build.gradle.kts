// Les plugins sont déclarés ici sans être appliqués, pour que tous les modules
// partagent le même classpath Gradle.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
