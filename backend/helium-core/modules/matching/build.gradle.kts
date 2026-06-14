plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework.security:spring-security-core")
    testImplementation(project(":shared:test-support"))
}
