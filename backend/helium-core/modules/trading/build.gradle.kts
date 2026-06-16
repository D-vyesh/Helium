plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":modules:auth-user"))
    implementation(project(":modules:ledger"))
    implementation(project(":modules:outbox"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation(project(":modules:matching"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":shared:test-support"))
}
