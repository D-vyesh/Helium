plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":modules:auth-user"))
    implementation(project(":modules:compliance-lite"))
    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("org.web3j:core:4.10.3")
    implementation("org.bitcoinj:bitcoinj-core:0.16.2")
    
    implementation(project(":modules:ledger"))
    implementation(project(":modules:outbox"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":shared:test-support"))
}
