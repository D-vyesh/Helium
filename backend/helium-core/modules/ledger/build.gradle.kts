plugins {
    `java-library`
}

dependencies {
    api(project(":shared:common"))
    implementation(project(":shared:persistence"))
    implementation(project(":shared:observability"))
    testImplementation(project(":shared:test-support"))
}

