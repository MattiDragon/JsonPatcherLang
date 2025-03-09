plugins {
    id("shared")
}

dependencies {
    implementation(project(":analysis"))
    implementation(project(":parser"))
    runtimeOnly(project(":runtime"))
    implementation(libs.bundles.asm)
}