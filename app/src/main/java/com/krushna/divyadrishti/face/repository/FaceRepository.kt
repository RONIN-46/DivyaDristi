package com.krushna.divyadrishti.face.database.repository

import com.krushna.divyadrishti.face.database.dao.FaceDao
import com.krushna.divyadrishti.face.database.entity.PersonEntity
import com.krushna.divyadrishti.face.embedding.similarity.RecognitionEngine
import kotlinx.coroutines.flow.Flow

class FaceRepository(
    private val faceDao: FaceDao
) {

    private val recognitionEngine = RecognitionEngine()

    fun getAllPersons(): Flow<List<PersonEntity>> {
        return faceDao.getAllPersons()
    }

    suspend fun registerPerson(
        name: String,
        relation: String,
        embedding: FloatArray
    ): Long {

        require(name.isNotBlank()) {
            "Person name cannot be blank"
        }

        require(embedding.size == 128) {
            "Face embedding must contain exactly 128 values"
        }

        val person = PersonEntity(
            name = name.trim(),
            relation = relation.trim(),
            embedding = embedding
        )

        return faceDao.insertPerson(person)
    }

    suspend fun updatePerson(person: PersonEntity) {
        faceDao.updatePerson(person)
    }

    suspend fun deletePerson(person: PersonEntity) {
        faceDao.deletePerson(person)
    }

    suspend fun getPersonById(
        personId: Long
    ): PersonEntity? {
        return faceDao.getPersonById(personId)
    }

    suspend fun recognizePerson(
        queryEmbedding: FloatArray,
        threshold: Float = 0.75f
    ): PersonEntity? {

        require(queryEmbedding.size == 128) {
            "Query embedding must contain exactly 128 values"
        }

        val persons = faceDao.getAllPersonsOnce()

        if (persons.isEmpty()) {
            return null
        }

        var bestPerson: PersonEntity? = null
        var bestSimilarity = -1f

        for (person in persons) {

            val registeredFace = mapOf(
                person.id.toString() to person.embedding
            )

            val result = recognitionEngine.recognize(
                queryEmbedding = queryEmbedding,
                registeredFaces = registeredFace,
                threshold = threshold
            )

            if (
                result.name != "Unknown" &&
                result.similarity > bestSimilarity
            ) {
                bestSimilarity = result.similarity
                bestPerson = person
            }
        }

        return bestPerson
    }
}