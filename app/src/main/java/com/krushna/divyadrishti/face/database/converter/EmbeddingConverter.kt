package com.krushna.divyadrishti.face.database.converter

import androidx.room.TypeConverter

class EmbeddingConverter {

    @TypeConverter
    fun fromFloatArray(embedding: FloatArray): String {
        return embedding.joinToString(separator = ",")
    }

    @TypeConverter
    fun toFloatArray(value: String): FloatArray {

        if (value.isBlank()) {
            return FloatArray(0)
        }

        return value
            .split(",")
            .map { it.toFloat() }
            .toFloatArray()
    }
}