package com.krushna.divyadrishti.face.embedding.similarity

data class RecognitionResult(
    val name: String,
    val similarity: Float
)

class RecognitionEngine {

    fun recognize(
        queryEmbedding: FloatArray,
        registeredFaces: Map<String, FloatArray>,
        threshold: Float = 0.75f
    ): RecognitionResult {

        var bestName = "Unknown"
        var bestScore = -1f

        for ((name, embedding) in registeredFaces) {

            val similarity = SimilarityUtils.cosineSimilarity(
                queryEmbedding,
                embedding
            )

            if (similarity > bestScore) {
                bestScore = similarity
                bestName = name
            }
        }

        return if (bestScore >= threshold) {
            RecognitionResult(bestName, bestScore)
        } else {
            RecognitionResult("Unknown", bestScore)
        }
    }
}