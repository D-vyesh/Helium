plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation(project(":modules:auth-user"))
    
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.15")
    testImplementation(project(":shared:test-support"))
}

