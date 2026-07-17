package com.krushna.divyadrishti.model

data class UnifiedContext(

    var scene: SceneContext = SceneContext(),

    var ocr: OCRContext = OCRContext(),

    var faces: FaceContext = FaceContext(),

    var colors: ColorContext = ColorContext(),

    var currency: CurrencyContext = CurrencyContext()

)