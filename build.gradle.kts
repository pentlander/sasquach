plugins {
    java
    id("application")
    id("com.github.mrcjkb.module-finder") version "0.0.7"
}

group = "com.pentlander"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    modularity.inferModulePath.set(true)
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
}

dependencies {
    annotationProcessor("io.soabase.record-builder:record-builder-processor:37")
    annotationProcessor("org.atteo.classindex:classindex:3.13")

    compileOnly("io.soabase.record-builder:record-builder-core:37")

    implementation("org.atteo.classindex:classindex:3.13")
    implementation("info.picocli:picocli:4.7.6")

    api("org.jspecify:jspecify:1.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.mockito:mockito-core:4.8.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
    systemProperty("junit.jupiter.execution.parallel.enabled", true)
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
}
