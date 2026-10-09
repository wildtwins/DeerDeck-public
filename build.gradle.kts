plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
}

val isolatedBuildRoot = providers.gradleProperty("deerdeck.isolatedBuildRoot")
if (isolatedBuildRoot.isPresent) {
    allprojects {
        val projectBuildDir = project.path
            .removePrefix(":")
            .replace(":", "/")
            .ifBlank { "root" }

        layout.buildDirectory.set(
            rootProject.layout.projectDirectory.dir(isolatedBuildRoot.get()).dir(projectBuildDir),
        )
    }
}
