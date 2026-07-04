package com.krushna.divyadrishti.face.embedding

import kotlin.math.sqrt

object EmbeddingUtils {

    fun normalize(embedding: FloatArray): FloatArray {

        var norm = 0f

        for (value in embedding) {
            norm += value * value
        }

        norm = sqrt(norm)

        if (norm == 0f) {
            return embedding
        }

        return FloatArray(embedding.size) { i ->
            embedding[i] / norm
        }
    }

    fun distance(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {

        require(embedding1.size == embedding2.size)

        var sum = 0f

        for (i in embedding1.indices) {

            val diff = embedding1[i] - embedding2[i]

            sum += diff * diff
        }

        return sqrt(sum)
    }
}