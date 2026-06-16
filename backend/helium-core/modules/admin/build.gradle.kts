plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":modules:auth-user"))
    implementation(project(":modules:wallet"))
    implementation(project(":modules:trading"))
    implementation(project(":modules:ledger"))
    implementation(project(":shared:security"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework:spring-context")
    testImplementation(project(":shared:test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
