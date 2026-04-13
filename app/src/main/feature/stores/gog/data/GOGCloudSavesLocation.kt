package com.winlator.cmod.feature.stores.gog.data

data class GOGCloudSavesLocationTemplate(
    val name: String,
    val location: String,
)

data class GOGCloudSavesLocation(
    val name: String,
    val location: String,
    val clientId: String,
    val clientSecret: String,
)
