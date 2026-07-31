package com.lioisme.delua.model

import com.lioisme.delua.androlua.common.model.ProjectDependencies

data class Project(
    val name: String,
    val path: String,
    val mainFile: String = "main.lua",
    val files: List<String> = listOf("main.lua"),
    val appName: String = name,
    val appVersion: String = "1.0",
    val packageName: String = "",
    val icon: String? = null,
    val dependencies: ProjectDependencies = ProjectDependencies()
)
