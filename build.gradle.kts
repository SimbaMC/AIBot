plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = "1.0.2-gtnh284"

tasks.named<Jar>("jar") {
    archiveBaseName.set("AiBot-MC1.7.10-GTNH2.8.4")
}
