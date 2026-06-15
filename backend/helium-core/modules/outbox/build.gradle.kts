plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation("io.micrometer:micrometer-core")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-json")
    testImplementation(project(":shared:test-support"))
}
