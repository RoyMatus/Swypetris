// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.dokka") version "2.2.0" apply false
    id("org.jetbrains.dokka-javadoc") version "2.2.0" apply false
    id("org.sonarqube") version "7.3.1.8318"
}

sonar {
    properties {
        property("sonar.projectKey", providers.environmentVariable("SONAR_PROJECT_KEY").orNull ?: "")
        property("sonar.organization", providers.environmentVariable("SONAR_ORGANIZATION").orNull ?: "")
        property("sonar.exclusions", "**/build/**,**/generated/**,**/*.png,**/*.ogg,**/*.wav")
    }
}

project(":app") {
    sonar {
        properties {
            property("sonar.coverage.jacoco.xmlReportPaths", layout.buildDirectory.file("reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml").get().asFile.absolutePath)
        }
    }
}
