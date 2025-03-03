import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  java
  application
  id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.project"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val vertxVersion = "4.5.13"
val junitJupiterVersion = "5.9.1"
val elasticsearchVersion= "8.12.0"

val mainVerticleName = "com.project.elasticsearch.MainVerticle"
val launcherClassName = "io.vertx.core.Launcher"

val watchForChange = "src/**/*"
val doOnChange = "${projectDir}/gradlew classes"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
  implementation("io.vertx:vertx-core:4.5.11")
  implementation("io.vertx:vertx-web-client:4.5.11")
  implementation("io.vertx:vertx-mongo-client:4.5.11")
  implementation("io.vertx:vertx-auth-mongo:4.5.11")
  implementation("io.vertx:vertx-openapi:4.5.11")
  implementation("io.vertx:vertx-web-openapi-router:4.5.11")
  testImplementation("io.vertx:vertx-junit5")
  implementation("org.slf4j:slf4j-api:1.7.36")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")

  // Elasticsearch dependencies
  implementation("co.elastic.clients:elasticsearch-java:$elasticsearchVersion")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
  implementation("jakarta.json:jakarta.json-api:2.1.1")

  // Additional Elasticsearch dependencies
  implementation("org.elasticsearch.client:elasticsearch-rest-high-level-client:7.17.10")
  implementation("org.elasticsearch.client:elasticsearch-rest-client:7.17.10")
  implementation("org.elasticsearch:elasticsearch:7.17.10")

  // Logging for Elasticsearch
  implementation("org.apache.logging.log4j:log4j-core:2.20.0")
  implementation("org.apache.logging.log4j:log4j-api:2.20.0")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf("run", mainVerticleName, "--redeploy=$watchForChange", "--launcher-class=$launcherClassName", "--on-redeploy=$doOnChange")
}
