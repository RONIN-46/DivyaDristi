package com.krushna.divyadrishti.face.registration

import android.graphics.Bitmap
import com.krushna.divyadrishti.face.detection.FaceDetector
import com.krushna.divyadrishti.face.embedding.FaceCropper

class FaceRegistrationFlow(
    private val faceDetector: FaceDetector,
    private val faceCropper: FaceCropper,
    private val registrationManager: FaceRegistrationManager
) {

    fun register(
        bitmap: Bitmap,
        name: String,
        relation: String,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {

        if (name.isBlank()) {
            onError("Enter person name")
            return
        }

        if (relation.isBlank()) {
            onError("Enter person relation")
            return
        }

        faceDetector.detectFaces(
            bitmap = bitmap,
            onSuccess = { faces ->

                if (faces.isEmpty()) {
                    onError("No face detected")
                    return@detectFaces
                }

                if (faces.size > 1) {
                    onError("Multiple faces detected. Use an image with one face.")
                    return@detectFaces
                }

                try {

                    val faceBitmap = faceCropper.crop(
                        bitmap,
                        faces.first()
                    )

                    Thread {

                        try {

                            val personId = kotlinx.coroutines.runBlocking {
                                registrationManager.registerFace(
                                    name = name,
                                    relation = relation,
                                    faceBitmap = faceBitmap
                                )
                            }

                            onSuccess(personId)

                        } catch (e: Exception) {

                            onError(
                                e.message ?: "Face registration failed"
                            )
                        }

                    }.start()

                } catch (e: Exception) {

                    onError(
                        e.message ?: "Face crop failed"
                    )
                }
            },
            onFailure = { exception ->

                onError(
                    exception.message ?: "Face detection failed"
                )
            }
        )
    }
}