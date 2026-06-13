plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":shared:security"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    testImplementation(project(":shared:test-support"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
