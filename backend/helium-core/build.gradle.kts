plugins {
    java
    id("org.springframework.boot") version "3.5.15" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "com.helium"
version = "0.1.0-SNAPSHOT"

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.11.4"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
    }
}

