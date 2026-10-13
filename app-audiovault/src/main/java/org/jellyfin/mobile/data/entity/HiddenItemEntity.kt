package org.jellyfin.mobile.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_item")
data class HiddenItemEntity(
    @PrimaryKey val itemId: String,
)
