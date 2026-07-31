package com.lioisme.delua.androlua.common.model

data class ModuleInfo(
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val entryPoint: String = "init.lua",
    val files: List<String> = emptyList(),
    val installTime: Long = 0
)
