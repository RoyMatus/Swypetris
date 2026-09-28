// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.dokka") version "2.2.0" apply false
    id("org.jetbrains.dokka-javadoc") version "2.2.0" apply false
    id("org.sonarqube") version "7.2.3.7755"
}

sonar {
    properties {
        property("sonar.projectKey", providers.environmentVariable("SONAR_PROJECT_KEY").orNull ?: "")
        property("sonar.organization", providers.environmentVariable("SONAR_ORGANIZATION").orNull ?: "")
        property("sonar.coverage.jacoco.xmlReportPaths", "app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml")
        property("sonar.exclusions", "**/build/**,**/generated/**")
    }
}
