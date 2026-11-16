plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.classgraph)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.openrune.central.common)
    implementation(libs.postgresql)
    implementation(libs.rsprot.api)
    implementation(projects.api.db)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.api.net)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.serverConfig)
    implementation(projects.api.totp)
    implementation(projects.api.webAuth)
}
