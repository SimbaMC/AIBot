plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = "1.6.0-gtnh284"

tasks.named<Jar>("jar") {
    archiveBaseName.set("AiBot-MC1.7.10-GTNH2.8.4")
}

tasks.withType<Jar>().configureEach {
    exclude("META-INF/versions/**")
}
