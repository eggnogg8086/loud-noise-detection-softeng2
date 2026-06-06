import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  kotlin("jvm") // Kotlin version to use
  groovy
  application
  id("biz.aQute.bnd.builder") version "7.2.3" // To generate MANIFEST file
}

group = "org.noise-planet"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral() {
    content { excludeGroupByRegex("javax\\.media") }
  }
  maven("https://repo.osgeo.org/repository/release/") {
    content { includeGroupByRegex("org\\.geotools.*") }
    content { includeGroupByRegex("jgridshift") }
    content { includeGroupByRegex("org\\.apache.*") }
    content { includeGroupByRegex("javax\\.media") }
  }
}

val vertxVersion = "5.0.0"
val junitJupiterVersion = "5.9.1"

val mainVerticleName = "org.noise_planet.onomap.MainVerticle"
val launcherClassName = "io.vertx.launcher.application.VertxApplication"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
  mainClass.set(mainVerticleName)
}

dependencies {
  implementation(platform(libs.vertx.stack.depchain))
  implementation(libs.hikaricp)
  implementation(libs.groovy.all)
  implementation(libs.postgresql)
  implementation(libs.vertx.web)
  implementation(libs.h2gis)
  implementation(libs.postgis.jts)
  implementation(libs.vertx.launcher.application)
  implementation(libs.org.osgi.service.jdbc)
  implementation(libs.vertx.config)
  implementation(libs.vertx.lang.kotlin)
  implementation(libs.vertx.lang.kotlin.coroutines)
  implementation(libs.commons.text)
  implementation(kotlin("stdlib-jdk8"))
  implementation(libs.org.osgi.framework)
  implementation(libs.client)
  implementation(libs.gt.xsd.wps)
  implementation(libs.gt.xsd.core)
  implementation(libs.slf4j.log4j12)
  annotationProcessor(libs.log4j.core)
  testImplementation(libs.slf4j.simple)
  testImplementation(libs.vertx.junit5)
  testImplementation(libs.vertx.web.client)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.hamcrest.library)
}

tasks.compileGroovy {
  classpath = sourceSets.main.get().compileClasspath
}

tasks.compileKotlin {
  libraries.from(sourceSets.main.get().groovy.classesDirectory)
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}
