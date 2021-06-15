plugins {
    java
    id("antlr")
    id("application")
}

group = "com.pentlander"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(16))
    }
}

dependencies {
    antlr("org.antlr:antlr4:4.5")
    implementation("org.ow2.asm:asm:9.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<JavaCompile>().configureEach {
    options.isIncremental = false
//    options.compilerArgs.add("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
//    jvmArgs?.add("--enable-preview")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-package", "com.pentlander.sasquach", "-visitor")
}
