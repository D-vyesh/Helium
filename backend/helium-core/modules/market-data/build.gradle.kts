plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":modules:matching"))
    implementation(project(":modules:outbox"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":shared:test-support"))
}
