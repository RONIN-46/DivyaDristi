package com.krushna.divyadrishti.face.registration

import android.graphics.Bitmap
import com.krushna.divyadrishti.face.database.repository.FaceRepository
import com.krushna.divyadrishti.face.embedding.inference.FaceEmbedding

class FaceRegistrationManager(
    private val faceRepository: FaceRepository,
    private val faceEmbedding: FaceEmbedding
) {

    suspend fun registerFace(
        name: String,
        relation: String,
        faceBitmap: Bitmap
    ): Long {

        require(name.isNotBlank()) {
            "Person name cannot be blank"
        }

        require(relation.isNotBlank()) {
            "Person relation cannot be blank"
        }

        val embedding = faceEmbedding.getEmbedding(faceBitmap)

        require(embedding.size == 128) {
            "Invalid face embedding size: ${embedding.size}"
        }

        return faceRepository.registerPerson(
            name = name,
            relation = relation,
            embedding = embedding
        )
    }
}