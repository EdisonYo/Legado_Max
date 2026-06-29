package io.legado.app.data.entities

import androidx.room.ColumnInfo

/**
 * 书源/订阅源轻量投影，用于选择器列表
 */
data class SourceBasic(
    @ColumnInfo(name = "sourceUrl")
    val sourceUrl: String,
    @ColumnInfo(name = "sourceName")
    val sourceName: String
)