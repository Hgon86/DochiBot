import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

 plugins {
     id("org.springframework.boot") version "3.4.1"
     id("io.spring.dependency-management") version "1.1.6"
     kotlin("jvm") version "2.0.21"
     kotlin("plugin.spring") version "2.0.21"
 }

group = "com.dochibot"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.2")
        mavenBom("software.amazon.awssdk:bom:2.31.58")
    }
}

  dependencies {
     // WebFlux + Coroutines
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")

      // Spring AI (Ollama/OpenAI)
      implementation("org.springframework.ai:spring-ai-starter-model-ollama")
      implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // PostgreSQL (R2DBC - Reactive)
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

    // Flyway (migrations run via JDBC)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // UUID v7 (time-ordered UUID)
    implementation("com.github.f4b6a3:uuid-creator:5.3.7")

    // Redis (Token storage / Session)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Logging (kotlin-logging + logback)
    implementation("io.github.oshai:kotlin-logging:7.0.0")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // S3-compatible storage (Presigned URL)
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:url-connection-client")
    implementation("software.amazon.awssdk:netty-nio-client")

    // PDF text extraction
    implementation("org.apache.pdfbox:pdfbox:2.0.35")

    // Runtime dependencies
    runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
    runtimeOnly("org.postgresql:postgresql")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  }

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.test {
    useJUnitPlatform()
}
