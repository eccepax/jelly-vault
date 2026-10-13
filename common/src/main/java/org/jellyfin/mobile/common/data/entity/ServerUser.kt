package org.jellyfin.mobile.common.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ServerUser(
    @Embedded val user: UserEntity,
    @Relation(
        parentColumn = UserEntity.SERVER_ID,
        entityColumn = ServerEntity.ID,
    )
    val server: ServerEntity,
)
