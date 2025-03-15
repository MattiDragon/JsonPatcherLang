plugins {
    id("shared")
}

dependencies {
    implementation(project(":analysis"))
    api(project(":parser"))
    implementation(project(":stdlib"))

    runtimeOnly(project(":runtime"))
    implementation(libs.bundles.asm)
}