plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":modules:auth-user"))
    implementation(project(":modules:ledger"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":shared:test-support"))
}
