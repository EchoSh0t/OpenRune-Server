plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.openrune.central.common)
    implementation(libs.postgresql)
    implementation(projects.api.db)
    implementation(projects.api.net)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.serverConfig)
    implementation(projects.api.totp)
    implementation(projects.api.utils.utilsSystem)
    implementation(projects.api.webAuth)
    implementation(projects.content.other.websiteApi)
}
