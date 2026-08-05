package com.lioisme.lide.model

data class Project(
    val name: String,
    val path: String,
    val mainFile: String = "main.lua",
    val packageName: String = "",
    val icon: String? = null,
)
