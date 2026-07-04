package com.krushna.divyadrishti.face.embedding.similarity

import kotlin.math.sqrt

object SimilarityUtils {

    fun cosineSimilarity(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {

        require(embedding1.size == embedding2.size) {
            "Embedding sizes do not match."
        }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in embedding1.indices) {

            dotProduct += embedding1[i] * embedding2[i]

            normA += embedding1[i] * embedding1[i]

            normB += embedding2[i] * embedding2[i]
        }

        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}