plugins {
    val kotlinVersion = "2.0.21"
    id("org.springframework.boot") version "3.4.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
}

group = "com.quocdat.lingolens"
kotlin.sourceSets.getByName("main").kotlin.srcDir("../shared/src/main/kotlin")
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    maven(url = "https://maven-central.storage-download.googleapis.com/maven2")
    maven(url = "https://repo1.maven.org/maven2")
    mavenCentral()
}

dependencies {
    // Import Spring Boot Dependencies BOM to manage versions without the dependency-management plugin
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.4.0"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database Migration & PostgreSQL
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // JJWT (Java JWT Library)
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // Swagger / OpenAPI 3
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // 1.21.4 supports the newer Docker Engine API used locally and by CI.
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("postgres") }
}

tasks.register<Test>("postgresTest") {
    description = "Runs PostgreSQL migration and API integration tests (requires Docker)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("postgres") }
    shouldRunAfter(tasks.test)
}
