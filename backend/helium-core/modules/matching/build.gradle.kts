plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":shared:test-support"))
}
