plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(projects.api.db)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.utils.utilsSystem)
}
