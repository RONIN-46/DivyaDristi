package com.krushna.divyadrishti.face.detection

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceDetector {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.1f)
            .build()
    )

    fun detectFaces(
        bitmap: Bitmap,
        onSuccess: (List<Face>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {

        val image = InputImage.fromBitmap(bitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->
                onSuccess(faces)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }
}