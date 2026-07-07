package com.krushna.divyadrishti.face.recognition

import android.content.Context
import android.graphics.Bitmap
import com.krushna.divyadrishti.face.database.repository.FaceRepository
import com.krushna.divyadrishti.face.detection.FaceDetector
import com.krushna.divyadrishti.face.embedding.FaceCropper
import com.krushna.divyadrishti.face.embedding.inference.FaceEmbedding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FaceRecognitionFlow(
    context: Context,
    private val faceRepository: FaceRepository
) {

    private val faceDetector = FaceDetector()
    private val faceCropper = FaceCropper()
    private val faceEmbedding = FaceEmbedding(context)

    fun recognize(
        bitmap: Bitmap,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        faceDetector.detectFaces(

            bitmap = bitmap,

            onSuccess = { faces ->

                if (faces.isEmpty()) {
                    onResult("No face detected")
                    return@detectFaces
                }

                try {

                    val face = faces.first()

                    val croppedFace = faceCropper.crop(
                        bitmap,
                        face
                    )

                    val queryEmbedding =
                        faceEmbedding.getEmbedding(croppedFace)

                    CoroutineScope(Dispatchers.IO).launch {

                        try {

                            val person =
                                faceRepository.recognizePerson(
                                    queryEmbedding = queryEmbedding
                                )

                            withContext(Dispatchers.Main) {

                                if (person != null) {

                                    onResult(
                                        "${person.name}, your ${person.relation}"
                                    )

                                } else {

                                    onResult("Unknown person")
                                }
                            }

                        } catch (e: Exception) {

                            withContext(Dispatchers.Main) {

                                onError(
                                    e.message
                                        ?: "Face recognition failed"
                                )
                            }
                        }
                    }

                } catch (e: Exception) {

                    onError(
                        e.message
                            ?: "Face embedding failed"
                    )
                }
            },

            onFailure = { exception ->

                onError(
                    exception.message
                        ?: "Face detection failed"
                )
            }
        )
    }
}