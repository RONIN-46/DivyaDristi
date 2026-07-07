package com.krushna.divyadrishti.face.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val relation: String,

    val embedding: FloatArray,

    val createdAt: Long = System.currentTimeMillis()
)