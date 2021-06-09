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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<JavaCompile>().configureEach {
    options.isIncremental = false
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-package", "com.pentlander.sasquach", "-visitor")
}
