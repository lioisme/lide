package com.lioisme.delua.androlua.common.model

data class ProjectDependencies(
    val libs: List<String> = emptyList(),
    val modules: List<String> = emptyList()
)
