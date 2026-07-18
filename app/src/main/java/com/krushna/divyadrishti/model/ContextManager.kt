package com.krushna.divyadrishti.model

object ContextManager {

    private val context = UnifiedContext()

    fun getContext(): UnifiedContext {
        return context
    }

    fun clear() {
        context.scene = SceneContext()
        context.ocr = OCRContext()
        context.faces = FaceContext()
        context.colors = ColorContext()
        context.currency = CurrencyContext()
    }

    fun updateScene(scene: SceneContext) {
        context.scene = scene
    }

    fun updateOCR(ocr: OCRContext) {
        context.ocr = ocr
    }

    fun updateFaces(faces: FaceContext) {
        context.faces = faces
    }

    fun updateColors(colors: ColorContext) {
        context.colors = colors
    }

    fun updateCurrency(currency: CurrencyContext) {
        context.currency = currency
    }
}