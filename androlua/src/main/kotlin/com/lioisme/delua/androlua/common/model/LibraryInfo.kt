package com.lioisme.delua.androlua.common.model

data class LibraryInfo(
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val filePath: String = "",
    val fileSize: Long = 0,
    val installTime: Long = 0
)
