package com.krushna.divyadrishti

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.krushna.divyadrishti.ocr.OCRManager
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import android.widget.Button
import android.widget.TextView
import com.krushna.divyadrishti.ocr.ImagePreprocessor
import com.krushna.divyadrishti.color.ColorDetector
import com.krushna.divyadrishti.face.embedding.inference.FaceEmbedding
import android.util.Log
import android.app.AlertDialog
import android.widget.EditText
import com.krushna.divyadrishti.face.database.FaceDatabase
import com.krushna.divyadrishti.face.database.repository.FaceRepository
import com.krushna.divyadrishti.face.detection.FaceDetector
import com.krushna.divyadrishti.face.embedding.FaceCropper
import com.krushna.divyadrishti.face.registration.FaceRegistrationFlow
import com.krushna.divyadrishti.face.registration.FaceRegistrationManager
import com.krushna.divyadrishti.speech.SpeechManager
import com.krushna.divyadrishti.speech.VoiceCommandListener
import com.krushna.divyadrishti.llm.LLMManager
import com.krushna.divyadrishti.llm.IntentClassifier
import com.krushna.divyadrishti.llm.IntentType
import com.krushna.divyadrishti.router.FeatureRouter
import com.krushna.divyadrishti.router.FeatureType
import com.krushna.divyadrishti.llm.PromptBuilder
import com.krushna.divyadrishti.model.FaceContext
import com.krushna.divyadrishti.model.ContextManager
import com.krushna.divyadrishti.model.SceneContext
import com.krushna.divyadrishti.model.DetectedObject
import com.krushna.divyadrishti.model.ColorContext
import com.krushna.divyadrishti.translation.TranslationManager
import com.krushna.divyadrishti.model.CurrencyContext
import android.widget.Toast

import com.krushna.divyadrishti.face.recognition.FaceRecognitionFlow
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.krushna.divyadrishti.model.OCRContext
import com.krushna.divyadrishti.executor.FeatureExecutor
import com.krushna.divyadrishti.llm.LLMCallback
import com.krushna.divyadrishti.llm.LLMNative
import com.krushna.divyadrishti.llm.ModelManager
import java.io.File
import java.util.Locale
import com.krushna.divyadrishti.language.LanguageManager

private data class ObjectInfo(
    val label: String,
    val color: String,
    val xCenterNorm: Float,
    val box: BoundingBox
)
private data class Detection(val box: BoundingBox, val label: String, val confidence: Float)
private data class BoundingBox(val x: Float, val y: Float, val w: Float, val h: Float)

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, VoiceCommandListener {

    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private lateinit var originalBitmap: Bitmap
    private lateinit var debugText: TextView
    private lateinit var detectButton: Button
    private lateinit var detectColorButton: Button
    private lateinit var connectButton: Button
    private lateinit var languageButton: Button
    private lateinit var translationManager: TranslationManager
    private lateinit var modeToggleButton: Button
    private lateinit var captureButton: Button
    private lateinit var ocrButton: Button

    private lateinit var ocrManager: OCRManager
    private lateinit var pickButton: Button

    private lateinit var registerFaceButton: Button
    private lateinit var manageFacesButton: Button
    private lateinit var ocrResultText: TextView
    private lateinit var statusText: TextView
    private lateinit var currencyToggleButton: Switch
    private lateinit var recognizeCurrencyButton: Button
    private lateinit var tts: TextToSpeech
    private lateinit var yoloInterpreter: Interpreter
    private lateinit var placesInterpreter: Interpreter

    private lateinit var faceRecognitionFlow: FaceRecognitionFlow
    private lateinit var faceRegistrationFlow: FaceRegistrationFlow
    private lateinit var faceRepository: FaceRepository

    private lateinit var labels: List<String>
    private lateinit var categories: List<String>

    // UI components
    private lateinit var connectionStatus: TextView
    private lateinit var modeStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var cameraStatusIndicator: View
    private lateinit var cameraStatusText: TextView
    private lateinit var intervalEditText: EditText
    private lateinit var autoModeSettingsCard: CardView
    private lateinit var speechManager: SpeechManager
    private lateinit var voiceButton: Button
    private lateinit var currencyInterpreter: Interpreter
    private lateinit var currencyLabels: List<String>
    private lateinit var llmManager: LLMManager
    private var isCurrencyDetectionEnabled = false

    private var selectedImageUri: Uri? = null // This tracks if a gallery image is loaded

    //    private var originalBitmap: Bitmap? = null
    private val currencyInputSize = 640
    private val currencyConfidenceThreshold = 0.35f

    private val esp32IP = "192.168.4.1"
    private val esp32SSID = "ESP32_CAM_AP"
    private val esp32Password = "12345678"
    private val esp32CaptureURL = "http://$esp32IP/capture"

    private var isAutoMode = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var autoCaptureRunnable: Runnable
    private var autoCaptureInterval = 30000L // Default 30 seconds

    private val latestScene = mutableListOf<ObjectInfo>()
    private lateinit var intentClassifier: IntentClassifier
    private lateinit var featureRouter: FeatureRouter

    private val featureExecutor = FeatureExecutor()

    // Launcher for selecting an image from the gallery
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->

        uri?.let {

            selectedImageUri = it

            try {

                // Load the selected gallery image as Bitmap
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) {
                    Toast.makeText(
                        this,
                        getString(R.string.msg_unable_to_load_image),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@let
                }

                // Store and display the image
                originalBitmap = bitmap
                imageView.setImageBitmap(bitmap)

                // Clear old OCR result
                ocrResultText.text = ""

                statusText.text = getString(R.string.msg_image_selected_processing)

                // Automatically run YOLO + Scene + HSV + Face
                processAndSpeak(bitmap)

                // OCR continues automatically


            } catch (e: Exception) {

                Log.e(
                    "GALLERY",
                    "Failed to load gallery image",
                    e
                )

                Toast.makeText(
                    this,
                    getString(R.string.msg_failed_to_load_image, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MAIN", "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        llmManager = LLMManager(this)
        llmManager.initialize()
        Log.d("MAIN", "Initializing LLM")
        ocrManager = OCRManager()
        // Initialize UI components
        imageView = findViewById(R.id.imageView)
        resultText = findViewById(R.id.resultText)
        debugText = findViewById(R.id.debugText)
        detectButton = findViewById(R.id.detectButton)
        detectColorButton = findViewById(R.id.detectColorButton)
        connectButton = findViewById(R.id.connectButton)
        modeToggleButton = findViewById(R.id.modeToggleButton)
        captureButton = findViewById(R.id.captureButton)
        ocrButton = findViewById(R.id.ocrButton)
        pickButton = findViewById(R.id.pickButton)
        ocrResultText = findViewById(R.id.ocrResultText)
        statusText = findViewById(R.id.statusText)
        registerFaceButton = findViewById(R.id.registerFaceButton)
        manageFacesButton = findViewById(R.id.manageFacesButton)
        currencyToggleButton = findViewById(R.id.currencyToggleButton)
        recognizeCurrencyButton = findViewById(R.id.recognizeCurrencyButton)
        connectionStatus = findViewById(R.id.connectionStatus)
        modeStatus = findViewById(R.id.modeStatus)
        voiceButton = findViewById(R.id.voiceButton)
        speechManager = SpeechManager(this, this)
        intentClassifier = IntentClassifier()
        featureRouter = FeatureRouter()
        statusIndicator = findViewById(R.id.statusIndicator)
        cameraStatusIndicator = findViewById(R.id.cameraStatusIndicator)
        cameraStatusText = findViewById(R.id.cameraStatusText)
        intervalEditText = findViewById(R.id.intervalEditText)
        autoModeSettingsCard = findViewById(R.id.autoModeSettingsCard)
        languageButton = findViewById(R.id.languageButton)

        languageButton.setOnClickListener {

            val currentLanguage =
                LanguageManager.getLanguage(this)

            if (currentLanguage == LanguageManager.ENGLISH) {

                switchLanguage(LanguageManager.HINDI)

            } else {

                switchLanguage(LanguageManager.ENGLISH)
            }
        }

        translationManager = TranslationManager()
        if (isHindi()) {
            translationManager.prepareHindi(
                onReady = {
                    Log.d("TRANSLATION", "Hindi translation model ready")
                },
                onError = {
                    Log.e("TRANSLATION", "Hindi translation model failed", it)
                }
            )
        }
        tts = TextToSpeech(this, this)

        translationManager.checkHindiModel(

            onResult = { downloaded ->

                Log.d(
                    "HINDI_MODEL",
                    "Hindi model downloaded = $downloaded"
                )

                runOnUiThread {

                    Toast.makeText(
                        this,
                        if (downloaded)
                            "Hindi model IS downloaded"
                        else
                            "Hindi model is NOT downloaded",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },

            onError = { error ->

                Log.e(
                    "HINDI_MODEL",
                    "Could not check Hindi model",
                    error
                )
            }
        )

        val faceDatabase = FaceDatabase.getInstance(this)

        faceRepository = FaceRepository(
            faceDatabase.faceDao()
        )

        faceRecognitionFlow = FaceRecognitionFlow(
            context = this,
            faceRepository = faceRepository
        )

        val faceEmbedding = FaceEmbedding(this)

        val registrationManager = FaceRegistrationManager(
            faceRepository = faceRepository,
            faceEmbedding = faceEmbedding
        )

        faceRegistrationFlow = FaceRegistrationFlow(
            faceDetector = FaceDetector(),
            faceCropper = FaceCropper(),
            registrationManager = registrationManager
        )

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            100
        )

        loadModels()

        currencyToggleButton.setOnCheckedChangeListener { _, isChecked ->
            isCurrencyDetectionEnabled = isChecked
            val message =
                if (isChecked) getString(R.string.msg_currency_detection_enabled)
                else getString(R.string.msg_currency_detection_disabled)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            statusText.text = message
        }

        autoCaptureRunnable = object : Runnable {
            override fun run() {
                if (isAutoMode) {
                    captureImageFromESP32()
                    handler.postDelayed(this, autoCaptureInterval)
                }
            }
        }
        voiceButton.setOnClickListener {

            speechManager.startListening()

        }
        connectButton.setOnClickListener { connectToESP32WiFi() }
        modeToggleButton.setOnClickListener { toggleCaptureMode() }
        captureButton.setOnClickListener { if (!isAutoMode) captureImageFromESP32() }

        pickButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch("image/*")
        }

        recognizeCurrencyButton.setOnClickListener {
            val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
            if (bitmap == null) {
                speakAndToast(getString(R.string.msg_please_capture_or_select_image))
            } else {
                thread {
                    runOnUiThread { statusText.text = getString(R.string.msg_processing) }
                    val currencyResult = detectCurrency(bitmap)
                    val text = if (currencyResult.isNotEmpty()) currencyResult else getString(R.string.msg_no_currency_detected)
                    presentResult(text)
                }
            }
        }

        findViewById<View>(R.id.voiceCard)?.setOnClickListener {
            speechManager.startListening()
        }

        registerFaceButton.setOnClickListener {

            val bitmap =
                (imageView.drawable as? BitmapDrawable)?.bitmap

            if (bitmap == null) {

                Toast.makeText(
                    this,
                    getString(R.string.msg_capture_or_pick_image_first),
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            showFaceRegistrationDialog(bitmap)
        }

        manageFacesButton.setOnClickListener {

            lifecycleScope.launch {

                try {

                    val persons =
                        faceRepository.getAllPersons().first()

                    if (persons.isEmpty()) {

                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.msg_no_registered_faces),
                            Toast.LENGTH_SHORT
                        ).show()

                        return@launch
                    }

                    val personNames = persons.map { person ->
                        "${person.name} (${person.relation})"
                    }.toTypedArray()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.title_registered_faces))
                        .setItems(personNames) { _, position ->

                            val selectedPerson =
                                persons[position]

                            AlertDialog.Builder(this@MainActivity)
                                .setTitle(getString(R.string.title_delete_registered_face))
                                .setMessage(
                                    getString(
                                        R.string.msg_delete_registered_face_confirm,
                                        selectedPerson.name,
                                        selectedPerson.relation
                                    )
                                )
                                .setNegativeButton(
                                    getString(R.string.cancel),
                                    null
                                )
                                .setPositiveButton(getString(R.string.delete)) { _, _ ->

                                    lifecycleScope.launch {

                                        try {

                                            faceRepository.deletePerson(
                                                selectedPerson
                                            )

                                            Toast.makeText(
                                                this@MainActivity,
                                                getString(
                                                    R.string.msg_person_deleted,
                                                    selectedPerson.name
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()

                                        } catch (e: Exception) {

                                            Toast.makeText(
                                                this@MainActivity,
                                                getString(
                                                    R.string.msg_delete_failed,
                                                    e.message ?: ""
                                                ),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                                .show()
                        }
                        .show()

                } catch (e: Exception) {

                    Toast.makeText(
                        this@MainActivity,
                        getString(
                            R.string.msg_failed_load_registered_faces,
                            e.message ?: ""
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        detectButton.setOnClickListener {
            val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                processAndSpeak(bitmap)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.msg_no_image_to_process),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        detectColorButton.setOnClickListener {
            val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
            if (bitmap == null) {
                speakAndToast(getString(R.string.msg_please_capture_or_select_image))
            } else {
                detectColorForCommand(bitmap, "")
            }
        }

        try {
            Log.d("MODEL", "Before getModelPath")

            val modelPath = ModelManager.getModelPath(this)

            Log.d("MODEL", "Path = $modelPath")

            val file = File(modelPath)

            Log.d(
                "MODEL",
                "Exists=${file.exists()} Size=${file.length()}"
            )
        } catch (e: Exception) {
            Log.e("MODEL", "ModelManager failed", e)
        }

        ocrButton.setOnClickListener {
            val handleOcrResult: (String) -> Unit = { text ->
                if (isValidOCRText(text)) {
                    ocrResultText.text = text
                    presentResult(text)
                } else {
                    val noTextMsg = getString(R.string.msg_no_text_found)
                    ocrResultText.text = noTextMsg
                    speakAndToast(noTextMsg)
                }
            }

            if (selectedImageUri != null) {
                // CASE 1: Use URI if image was picked from Gallery
                ocrManager.recognizeFromUri(
                    context = this,
                    uri = selectedImageUri!!,
                    onResult = handleOcrResult,
                    onError = { e -> speakAndToast(getString(R.string.msg_error_prefix, e.message ?: "")) }
                )
            } else {
                // CASE 2: Use Bitmap if image was captured from ESP32
                val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    ocrManager.recognize(
                        bitmap = bitmap,
                        onResult = handleOcrResult,
                        onError = { e -> speakAndToast(getString(R.string.msg_error_prefix, e.message ?: "")) }
                    )
                } else {
                    speakAndToast(getString(R.string.msg_please_capture_or_select_image))
                }
            }
        }
        updateUIForMode()
        updateFeatureCardsLanguage()
    }



    override fun onCommandRecognized(command: String) {


        if (handleLanguageCommand(command)) {
            return
        }

        val intent = intentClassifier.classify(command)
        val feature = featureRouter.route(intent)


        val prompt = PromptBuilder.build(
            command,
            ContextManager.getContext()
        )
        Log.d("PROMPT", prompt)

        llmManager.generate(
            prompt,
            object : LLMCallback {

                override fun onToken(token: String) {
                }

                override fun onComplete(response: String) {
                    presentResult(response)
                }

                override fun onError(message: String) {
                    presentResult(message)
                }
            }
        )
    }


    private fun handleLanguageCommand(command: String): Boolean {

        val normalized = command
            .trim()
            .lowercase(Locale.getDefault())

        return when {

            normalized == "hindi" ||
                    normalized.contains("switch to hindi") ||
                    normalized.contains("change to hindi") ||
                    normalized.contains("hindi language") ||
                    normalized.contains("हिंदी") ||
                    normalized.contains("हिंदी में") -> {

                switchLanguage(LanguageManager.HINDI)
                true
            }

            normalized == "english" ||
                    normalized.contains("switch to english") ||
                    normalized.contains("change to english") ||
                    normalized.contains("english language") ||
                    normalized.contains("इंग्लिश") ||
                    normalized.contains("अंग्रेजी") -> {

                switchLanguage(LanguageManager.ENGLISH)
                true
            }

            else -> false
        }
    }

    private fun isHindi(): Boolean {
        return LanguageManager.getLanguage(this) == LanguageManager.HINDI
    }
    private fun translateObjectLabel(label: String): String {

        if (!isHindi()) return label

        return when (label.lowercase(Locale.ENGLISH)) {
            "person" -> "व्यक्ति"
            "bicycle" -> "साइकिल"
            "car" -> "कार"
            "motorcycle" -> "मोटरसाइकिल"
            "airplane" -> "हवाई जहाज़"
            "bus" -> "बस"
            "train" -> "ट्रेन"
            "truck" -> "ट्रक"
            "boat" -> "नाव"

            "traffic light" -> "ट्रैफिक लाइट"
            "fire hydrant" -> "फायर हाइड्रेंट"
            "stop sign" -> "स्टॉप साइन"
            "parking meter" -> "पार्किंग मीटर"
            "bench" -> "बेंच"

            "bird" -> "पक्षी"
            "cat" -> "बिल्ली"
            "dog" -> "कुत्ता"
            "horse" -> "घोड़ा"
            "sheep" -> "भेड़"
            "cow" -> "गाय"
            "elephant" -> "हाथी"
            "bear" -> "भालू"
            "zebra" -> "ज़ेब्रा"
            "giraffe" -> "जिराफ़"

            "backpack" -> "बैग"
            "umbrella" -> "छाता"
            "handbag" -> "हैंडबैग"
            "tie" -> "टाई"
            "suitcase" -> "सूटकेस"

            "bottle" -> "बोतल"
            "wine glass" -> "गिलास"
            "cup" -> "कप"
            "fork" -> "कांटा"
            "knife" -> "चाकू"
            "spoon" -> "चम्मच"
            "bowl" -> "कटोरा"

            "banana" -> "केला"
            "apple" -> "सेब"
            "sandwich" -> "सैंडविच"
            "orange" -> "संतरा"
            "broccoli" -> "ब्रोकोली"
            "carrot" -> "गाजर"

            "chair" -> "कुर्सी"
            "couch" -> "सोफ़ा"
            "bed" -> "बिस्तर"
            "dining table" -> "खाने की मेज़"
            "tv" -> "टीवी"
            "laptop" -> "लैपटॉप"
            "mouse" -> "माउस"
            "remote" -> "रिमोट"
            "keyboard" -> "कीबोर्ड"
            "cell phone" -> "मोबाइल फोन"

            else -> label
        }
    }



    private fun detectColorForCommand(bitmap: Bitmap, command: String) {
        thread {
            runOnUiThread { statusText.text = getString(R.string.msg_detecting_color) }
            val response = if (command.isBlank()) {
                colorResponse(ColorDetector.analyze(bitmap))
            } else {
                val detections = detectObjects(bitmap)
                val normalizedCommand = command.lowercase(Locale.getDefault())
                val matchingObject = detections.firstOrNull { objectInfo ->
                    val label = objectInfo.label.lowercase(Locale.getDefault())
                    normalizedCommand.contains(label) ||
                            normalizedCommand.contains(label.removeSuffix("s"))
                }
                when {
                    matchingObject != null ->
                        getString(
                            R.string.msg_object_color_appears,
                            matchingObject.label,
                            matchingObject.color.lowercase(Locale.getDefault())
                        )
                    detections.isNotEmpty() -> colorResponse(ColorDetector.analyze(bitmap))
                    else -> getString(R.string.msg_color_object_not_found)
                }
            }
            runOnUiThread {
                statusText.text = getString(R.string.msg_color_detection_complete)
            }
            presentResult(response)
        }
    }

    private fun colorResponse(result: ColorDetector.ColorResult): String =
        if (result.name == "Unknown") {
            getString(R.string.msg_color_unknown)
        } else {
            getString(R.string.msg_color_appears, result.name.lowercase(Locale.getDefault()))
        }

    override fun onError(error: String) {
        Toast.makeText(
            this,
            error,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun loadModels() {
        thread {
            try {
                runOnUiThread {
                    debugText.visibility = View.VISIBLE
                    debugText.text = getString(R.string.msg_loading_models)
                }
                yoloInterpreter = Interpreter(loadModelFile("yolov8n_float32.tflite"))
                placesInterpreter = Interpreter(loadModelFile("places365_float32.tflite"))
                currencyInterpreter = Interpreter(loadModelFile("Currency_32.tflite"))

                categories = assets.open("places365_labels.txt").bufferedReader().readLines()
                labels = assets.open("yolo_labels.txt").bufferedReader().readLines().map {
                    if (it.contains(":")) it.substringAfter(":").trim() else it.trim()
                }
                currencyLabels = assets.open("Currency_labels.txt").bufferedReader().readLines()

                runOnUiThread {
                    statusText.text = getString(R.string.msg_all_models_loaded)
                    debugText.text = getString(R.string.msg_models_loaded)
                    detectButton.isEnabled = true
                    currencyToggleButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = getString(R.string.msg_error_loading_models)
                    debugText.visibility = View.VISIBLE
                    debugText.text = getString(R.string.msg_error_prefix, e.message ?: "")
                }
            }
        }
    }

    private fun connectToESP32WiFi() {
        statusText.text = getString(R.string.msg_connecting_esp32)
        thread {
            try {
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val wifiConfig = WifiConfiguration().apply {
                    SSID = "\"$esp32SSID\""
                    preSharedKey = "\"$esp32Password\""
                }

                val netId = wifiManager.addNetwork(wifiConfig)
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()

                Thread.sleep(5000)

                runOnUiThread {
                    statusText.text = getString(R.string.msg_connected_esp32)
                    connectionStatus.text = getString(R.string.online)
                    connectionStatus.setTextColor(
                        ContextCompat.getColor(
                            this,
                            android.R.color.holo_green_light
                        )
                    )
                    cameraStatusText.text = getString(R.string.connected)
                    statusIndicator.setBackgroundResource(R.drawable.status_indicator_online)
                    cameraStatusIndicator.setBackgroundResource(R.drawable.status_indicator_online)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = getString(R.string.msg_connection_failed, e.message ?: "")
                    connectionStatus.text = getString(R.string.offline)
                    connectionStatus.setTextColor(
                        ContextCompat.getColor(
                            this,
                            android.R.color.holo_red_light
                        )
                    )
                    cameraStatusText.text = getString(R.string.disconnected)
                    statusIndicator.setBackgroundResource(R.drawable.status_indicator_offline)
                    cameraStatusIndicator.setBackgroundResource(R.drawable.status_indicator_offline)
                }
            }
        }
    }

    private fun captureImageFromESP32() {
        statusText.text = getString(R.string.msg_capturing_image)
        thread {
            try {
                val url = URL(esp32CaptureURL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream: InputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val processedBitmap = ImagePreprocessor().process(bitmap)
                    inputStream.close()
                    runOnUiThread {

                        selectedImageUri = null
                        ocrResultText.text = ""
                        // Save the original captured image
                        originalBitmap = bitmap

                        // Display ONLY the original image
                        imageView.setImageBitmap(originalBitmap)

                        statusText.text =
                            if (isAutoMode) getString(R.string.msg_auto_mode_image_captured)
                            else getString(R.string.msg_manual_capture_successful)

                        // Send the original image for processing
                        processAndSpeak(originalBitmap)
                    }

                } else {
                    runOnUiThread {
                        statusText.text = getString(R.string.msg_capture_failed_http, connection.responseCode)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                runOnUiThread { statusText.text = getString(R.string.msg_capture_error, e.message ?: "") }
            }
        }
    }

    private fun processAndSpeak(bitmap: Bitmap) {

        thread {

            try {

                runOnUiThread {
                    statusText.text = getString(R.string.msg_processing)
                    detectButton.isEnabled = false
                    debugText.visibility = View.GONE
                }

                // -----------------------------------------
                // 1. Existing YOLO + Scene + HSV + Currency
                // -----------------------------------------
                val sceneCaption = processImage(bitmap)

                // -----------------------------------------
                // Results from OCR + Face
                // -----------------------------------------
                var ocrText = ""
                var faceCaption = ""

                var ocrDone = false
                var faceDone = false

                val lock = Any()

                fun finishIfReady() {

                    val finalCaption: String

                    synchronized(lock) {

                        if (!ocrDone || !faceDone) {
                            return
                        }

                        val parts = mutableListOf<String>()

                        // ExistocrText.isNotBlanking YOLO / Scene / Currency output
                        if (sceneCaption.isNotBlank()) {
                            parts.add(sceneCaption)
                        }

                        // Face result
                        if (faceCaption.isNotBlank()) {
                            parts.add(faceCaption)
                        }

                        // OCR result
                        if (ocrText.isNotBlank()) {

                            parts.add(
                                getString(R.string.msg_ocr_text_format, ocrText)
                            )
                        }

                        finalCaption =
                            parts.joinToString(" ")
                    }

                    runOnUiThread {

                        presentResult(finalCaption)

                        statusText.text = getString(R.string.msg_processing_complete)
                        detectButton.isEnabled = true
                    }
                }

                // -----------------------------------------
                // 2. OCR
                // -----------------------------------------
                runOCR(

                    bitmap = bitmap,

                    onResult = { text ->

                        synchronized(lock) {

                            val cleanText = text.trim()

                            if (isValidOCRText(cleanText)) {

                                ocrText = cleanText

                                ContextManager.updateOCR(
                                    OCRContext(
                                        text = cleanText,
                                        available = true
                                    )
                                )

                            } else {

                                ocrText = ""

                                ContextManager.updateOCR(
                                    OCRContext()
                                )
                            }

                            ocrDone = true
                        }

                        runOnUiThread {
                            ocrResultText.text = if (ocrText.isNotBlank()) ocrText else getString(R.string.msg_no_text_found)
                        }

                        finishIfReady()
                    },

                    onError = { error ->

                        Log.e(
                            "OCR",
                            "OCR failed",
                            error
                        )

                        synchronized(lock) {

                            ocrText = ""

                            ContextManager.updateOCR(
                                OCRContext()
                            )

                            ocrDone = true
                        }

                        runOnUiThread {
                            ocrResultText.text = ""
                        }

                        finishIfReady()
                    }
                )

                // -----------------------------------------
                // 3. Face Recognition
                // -----------------------------------------
                runOnUiThread {

                    faceRecognitionFlow.recognize(

                        bitmap = bitmap,

                        onResult = { faceResult ->

                            synchronized(lock) {

                                val recognizedPersons =
                                    mutableListOf<String>()

                                if (
                                    faceResult != "No face detected" &&
                                    faceResult != "Unknown person"
                                ) {

                                    recognizedPersons.add(
                                        faceResult
                                    )
                                }

                                ContextManager.updateFaces(
                                    FaceContext(
                                        persons = recognizedPersons
                                    )
                                )

                                faceCaption =
                                    when (faceResult) {

                                        "No face detected" ->
                                            ""

                                        "Unknown person" ->
                                            getString(R.string.msg_person_unknown)

                                        else ->
                                            getString(R.string.msg_person_known, faceResult)
                                    }

                                faceDone = true
                            }

                            finishIfReady()
                        },

                        onError = { error ->

                            Log.e(
                                "FACE_RECOGNITION",
                                error
                            )

                            synchronized(lock) {

                                ContextManager.updateFaces(
                                    FaceContext()
                                )

                                faceCaption = ""
                                faceDone = true
                            }

                            finishIfReady()
                        }
                    )
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        getString(R.string.msg_error_prefix, e.message ?: "")

                    debugText.visibility =
                        View.VISIBLE

                    debugText.text =
                        getString(R.string.msg_error_prefix, e.message ?: "")

                    detectButton.isEnabled = true
                }
            }
        }
    }

    private fun runOCR(
        bitmap: Bitmap,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {

        try {

            val processedBitmap =
                ImagePreprocessor().process(bitmap)

            ocrManager.recognize(

                processedBitmap,

                onResult = { text ->

                    onResult(text.trim())
                },

                onError = { error ->

                    onError(error)
                }
            )

        } catch (e: Exception) {

            onError(e)
        }
    }
    private fun isValidOCRText(text: String): Boolean {

        val cleanText = text.trim()

        if (cleanText.isBlank()) {
            return false
        }

        val noTextMessages = listOf(
            "No text found",
            "No text detected",
            "No text found in this image.",
            "No text found in this image",
            "No text detected in this image.",
            "No text detected in this image"
        )

        return noTextMessages.none {
            cleanText.equals(it, ignoreCase = true)
        }
    }
    private fun toggleCaptureMode() {
        isAutoMode = !isAutoMode
        if (isAutoMode) {
            val intervalString = intervalEditText.text.toString()
            autoCaptureInterval = if (intervalString.isNotEmpty() && intervalString.toLong() > 0) {
                intervalString.toLong() * 1000
            } else {
                30000L
            }
            handler.post(autoCaptureRunnable)
            Toast.makeText(
                this,
                getString(R.string.msg_auto_mode_enabled, autoCaptureInterval / 1000),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            handler.removeCallbacks(autoCaptureRunnable)
            Toast.makeText(this, getString(R.string.msg_manual_mode_enabled), Toast.LENGTH_SHORT).show()
        }
        updateUIForMode()
    }

    private fun updateUIForMode() {
        modeToggleButton.text = if (isAutoMode) getString(R.string.btn_manual_caps) else getString(R.string.btn_auto_caps)
        modeStatus.text = if (isAutoMode) getString(R.string.status_auto_dropdown) else getString(R.string.status_manual_dropdown)
        modeStatus.setTextColor(
            ContextCompat.getColor(
                this,
                R.color.brand_primary
            )
        )
        captureButton.isEnabled = !isAutoMode
        statusText.text = if (isAutoMode) getString(R.string.msg_auto_mode_active) else getString(R.string.msg_manual_mode_active)
        autoModeSettingsCard.visibility = if (isAutoMode) View.VISIBLE else View.GONE
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun processImage(bitmap: Bitmap): String {
        if (isCurrencyDetectionEnabled) {
            val currencyResult = detectCurrency(bitmap)
            return if (currencyResult.isNotEmpty()) currencyResult else getString(R.string.msg_no_currency_detected)
        }

        val (scene, confidence) = predictScene(bitmap)
        val confidencePercentage = (confidence * 100).toInt()

        val detections = detectObjects(bitmap)

        latestScene.clear()
        latestScene.addAll(detections)


// =========================================
// SEPARATE HSV COLOR FEATURE
// =========================================

        val detectedColors =
            detectColorsForObjects(
                bitmap,
                detections
            )

        Log.d(
            "COLOR_FEATURE",
            "HSV Object Colors = $detectedColors"
        )

        val cleanScene = scene.substringAfterLast("/").replace(Regex("[\\d/_\\\\-]"), " ").trim()
        val sceneDescription = when {
            confidencePercentage >= 60 -> "I'm pretty sure you are in a $cleanScene."
            confidencePercentage >= 30 -> "I'm fairly sure this is a $cleanScene."
            else -> "I think this might be a $cleanScene."
        }

        val objectSummary =
            if (detections.isNotEmpty()) summarizeEntities(detections)
            else "I don't see any other major objects."

        val finalDescription = "$sceneDescription $objectSummary"

        val detectedObjects = detections.map {
            DetectedObject(
                label = it.label,
                confidence = 1.0f,
                position = getObjectPosition(
                    xCenter = it.xCenterNorm
                ),
                color = ""
            )
        }.toMutableList()


        ContextManager.updateColors(
            ColorContext(
                colors = detectedColors.toMutableMap()
            )
        )
        Log.d(
            "COLOR_FEATURE",
            "ColorContext updated = $detectedColors"
        )

        ContextManager.updateScene(
            SceneContext(
                sceneName = cleanScene,
                confidence = confidence,
                description = finalDescription,
                objects = detectedObjects
            )
        )
        return finalDescription
    }
    private fun detectCurrency(bitmap: Bitmap): String {
        try {
            val resized =
                Bitmap.createScaledBitmap(bitmap, currencyInputSize, currencyInputSize, true)
            val byteBuffer =
                ByteBuffer.allocateDirect(1 * currencyInputSize * currencyInputSize * 3 * 4)
                    .apply { order(ByteOrder.nativeOrder()) }
            val intValues = IntArray(currencyInputSize * currencyInputSize)
            resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)
            var pixel = 0
            for (i in 0 until currencyInputSize) {
                for (j in 0 until currencyInputSize) {
                    val value = intValues[pixel++]
                    byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)
                    byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)
                    byteBuffer.putFloat((value and 0xFF) / 255.0f)
                }
            }
            val output = Array(1) { Array(11) { FloatArray(8400) } }
            currencyInterpreter.run(byteBuffer, output)
            val currencyDetections = mutableListOf<Detection>()
            for (i in 0 until 8400) {
                val classScores = FloatArray(7) { c -> output[0][4 + c][i] }
                val confidence = classScores.maxOrNull() ?: 0f
                if (confidence > currencyConfidenceThreshold) {
                    val classIndex = classScores.indexOfFirst { it == confidence }
                    if (classIndex in currencyLabels.indices) {
                        currencyDetections.add(
                            Detection(
                                BoundingBox(
                                    output[0][0][i],
                                    output[0][1][i],
                                    output[0][2][i],
                                    output[0][3][i]
                                ), currencyLabels[classIndex], confidence
                            )
                        )
                    }
                }
            }
            val finalDetections = nonMaxSuppression(currencyDetections, 0.5f)
            if (finalDetections.isNotEmpty()) {
                val counts = finalDetections.groupingBy { it.label }.eachCount()
                val detectedNotes = mutableListOf<String>()
                counts.forEach { (label, count) ->
                    repeat(count) {
                        detectedNotes.add(label)
                    }
                }
                ContextManager.updateCurrency(
                    CurrencyContext(
                        notes = detectedNotes
                    )
                )
                val detectedList = counts.map { (label, count) ->
                    if (count > 1)
                        getString(R.string.msg_currency_notes_plural, count, label)
                    else
                        getString(R.string.msg_currency_note_single, label)
                }.joinToString(", ")
                return getString(R.string.msg_currency_detected_suffix, detectedList)
            }
            ContextManager.updateCurrency(
                CurrencyContext()
            )
            return ""
        } catch (e: Exception) {
            ContextManager.updateCurrency(
                CurrencyContext()
            )
            return ""
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp(it - maxLogit) }
        val sumExps = exps.sum()
        return exps.map { it / sumExps }.toFloatArray()
    }

    private fun predictScene(bitmap: Bitmap): Pair<String, Float> {
        val inputSize = 224
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .apply { order(ByteOrder.nativeOrder()) }
        val intValues = IntArray(inputSize * inputSize)
        resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val v = intValues[pixel++]
                byteBuffer.putFloat((((v shr 16 and 0xFF) / 255.0f - 0.485f) / 0.229f))
                byteBuffer.putFloat((((v shr 8 and 0xFF) / 255.0f - 0.456f) / 0.224f))
                byteBuffer.putFloat((((v and 0xFF) / 255.0f - 0.406f) / 0.225f))
            }
        }
        val outputLogits = Array(1) { FloatArray(365) }
        placesInterpreter.run(byteBuffer, outputLogits)
        val probabilities = softmax(outputLogits[0])
        val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        return Pair(categories[maxIdx], probabilities[maxIdx])
    }

    private fun detectObjects(bitmap: Bitmap): List<ObjectInfo> {
        val inputSize = 640
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .apply { order(ByteOrder.nativeOrder()) }
        val intValues = IntArray(inputSize * inputSize)
        resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val v = intValues[pixel++]
                byteBuffer.putFloat(((v shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((v shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((v and 0xFF) / 255.0f)
            }
        }
        val output = Array(1) { Array(84) { FloatArray(8400) } }
        yoloInterpreter.run(byteBuffer, output)
        val detections = mutableListOf<Detection>()
        for (i in 0 until 8400) {
            val classScores = FloatArray(80) { c -> output[0][4 + c][i] }
            val confidence = classScores.maxOrNull() ?: 0f
            if (confidence > 0.25f) {
                val classIndex = classScores.indexOfFirst { it == confidence }
                detections.add(
                    Detection(
                        BoundingBox(
                            output[0][0][i],
                            output[0][1][i],
                            output[0][2][i],
                            output[0][3][i]
                        ), labels[classIndex], confidence
                    )
                )
            }
        }
        return nonMaxSuppression(detections).map {
            ObjectInfo(
                label = it.label,
                color = "unknown",
                xCenterNorm = it.box.x,
                box = it.box
            )
        }
    }
    private fun cropObject(
        bitmap: Bitmap,
        box: BoundingBox
    ): Bitmap {

        // YOLO BoundingBox:
        // x, y = CENTER of object
        // w, h = WIDTH and HEIGHT
        // Values are normalized to 0..1

        val left =
            ((box.x - box.w / 2f) * bitmap.width).toInt()

        val top =
            ((box.y - box.h / 2f) * bitmap.height).toInt()

        val right =
            ((box.x + box.w / 2f) * bitmap.width).toInt()

        val bottom =
            ((box.y + box.h / 2f) * bitmap.height).toInt()

        // Keep coordinates inside bitmap
        val safeLeft =
            left.coerceIn(0, bitmap.width - 1)

        val safeTop =
            top.coerceIn(0, bitmap.height - 1)

        val safeRight =
            right.coerceIn(safeLeft + 1, bitmap.width)

        val safeBottom =
            bottom.coerceIn(safeTop + 1, bitmap.height)

        return Bitmap.createBitmap(
            bitmap,
            safeLeft,
            safeTop,
            safeRight - safeLeft,
            safeBottom - safeTop
        )
    }
    private fun detectColorsForObjects(
        bitmap: Bitmap,
        objects: List<ObjectInfo>
    ): Map<String, String> {

        val colors = mutableMapOf<String, String>()

        objects.forEach { obj ->

            try {
                val croppedBitmap = cropObject(bitmap, obj.box)

                val colorResult = ColorDetector.analyze(croppedBitmap)

                val color = colorResult.name

                colors[obj.label] = color

                Log.d(
                    "COLOR_FEATURE",
                    "Object=${obj.label}, HSV Color=$color"
                )

            } catch (e: Exception) {

                Log.e(
                    "COLOR_FEATURE",
                    "Color detection failed for ${obj.label}",
                    e
                )

                colors[obj.label] = "Unknown"
            }
        }

        return colors
    }

    private fun nonMaxSuppression(
        detections: List<Detection>,
        iouThreshold: Float = 0.5f
    ): List<Detection> {
        val finalDetections = mutableListOf<Detection>()
        detections.groupBy { it.label }.forEach { (_, group) ->
            var candidates = group.sortedByDescending { it.confidence }
            while (candidates.isNotEmpty()) {
                val best = candidates.first()
                finalDetections.add(best)
                candidates = candidates.filter { calculateIoU(best.box, it.box) < iouThreshold }
            }
        }
        return finalDetections
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = max(box1.x - box1.w / 2, box2.x - box2.w / 2)
        val y1 = max(box1.y - box1.h / 2, box2.y - box2.h / 2)
        val x2 = min(box1.x + box1.w / 2, box2.x + box2.w / 2)
        val y2 = min(box1.y + box1.h / 2, box2.y + box2.h / 2)
        val intersectionArea = max(0f, x2 - x1) * max(0f, y2 - y1)
        val unionArea = (box1.w * box1.h) + (box2.w * box2.h) - intersectionArea
        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }



    private fun summarizeEntities(detections: List<ObjectInfo>): String {

        fun formatRegion(objects: List<ObjectInfo>): String {
            if (objects.isEmpty()) return ""

            val labels = objects.map { it.label }

            val grouped = labels
                .groupingBy { it }
                .eachCount()
                .map { (label, count) ->
                    if (count == 1) "a $label" else "$count ${label}s"
                }

            return when {
                grouped.size == 1 -> grouped.first()
                grouped.size == 2 -> grouped.joinToString(" and ")
                else -> grouped.dropLast(1).joinToString(", ") + ", and " + grouped.last()
            }
        }

        val leftObjects = detections.filter { it.xCenterNorm < 0.33f }
        val centerObjects = detections.filter { it.xCenterNorm in 0.33f..0.67f }
        val rightObjects = detections.filter { it.xCenterNorm > 0.67f }

        val left = formatRegion(leftObjects)
        val center = formatRegion(centerObjects)
        val right = formatRegion(rightObjects)

        val parts = mutableListOf<String>()

        if (left.isNotEmpty()) {
            parts.add("To your left, I see $left")
        }
        if (center.isNotEmpty()) {
            parts.add("In front of you, there is $center")
        }
        if (right.isNotEmpty()) {
            parts.add("To your right, I see $right")
        }

        if (parts.isEmpty()) {
            return ""
        }

        return parts.joinToString(", ") + "."
    }

    private fun getObjectPosition(xCenter: Float): String {
        return when {
            xCenter < 0.33f -> "Left"
            xCenter < 0.67f -> "Center"
            else -> "Right"
        }
    }

    private fun showFaceRegistrationDialog(bitmap: Bitmap) {

        val nameInput = EditText(this).apply {
            hint = getString(R.string.hint_person_name)
        }

        val relationInput = EditText(this).apply {
            hint = getString(R.string.hint_relation)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val padding = (20 * resources.displayMetrics.density).toInt()

            setPadding(
                padding,
                padding,
                padding,
                0
            )

            addView(nameInput)
            addView(relationInput)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_register_face))
            .setView(container)
            .setPositiveButton(getString(R.string.register)) { _, _ ->

                faceRegistrationFlow.register(
                    bitmap = bitmap,
                    name = nameInput.text.toString(),
                    relation = relationInput.text.toString(),
                    onSuccess = { personId ->

                        runOnUiThread {

                            Toast.makeText(
                                this,
                                getString(R.string.msg_face_registered, personId.toString()),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onError = { message ->

                        runOnUiThread {

                            Toast.makeText(
                                this,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            updateTTSLanguage(
                LanguageManager.getLanguage(this)
            )
        }
    }

    private fun updateTTSLanguage(language: String) {

        val locale = if (language == LanguageManager.HINDI) {
            Locale("hi", "IN")
        } else {
            Locale("en", "IN")
        }

        val result = tts.setLanguage(locale)

        if (
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Toast.makeText(
                this,
                getString(R.string.msg_tts_language_not_available),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun switchLanguage(language: String) {

        LanguageManager.setLanguage(language)

        updateTTSLanguage(language)

        updateFeatureCardsLanguage(language)

        if (language == LanguageManager.HINDI) {

            translationManager.prepareHindi(
                onReady = {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            getString(R.string.msg_hindi_translation_ready),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onError = {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            getString(R.string.msg_hindi_translation_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }

        speechManager.setLanguage(language)


    }

    private fun updateFeatureCardsLanguage(language: String? = null) {
        val hindi = if (language != null) language == LanguageManager.HINDI else isHindi()
        runOnUiThread {
            findViewById<TextView>(R.id.detectObjectsText)?.text =
                if (hindi) "वस्तु\nपहचानें" else "Detect\nObjects"
            findViewById<TextView>(R.id.recognizeCurrencyText)?.text =
                if (hindi) "मुद्रा\nपहचानें" else "Recognize\nCurrency"
            findViewById<TextView>(R.id.readTextOcrText)?.text =
                if (hindi) "टेक्स्ट पढ़ें\n(OCR)" else "Read Text\n(OCR)"
            findViewById<TextView>(R.id.detectColorText)?.text =
                if (hindi) "रंग\nपहचानें" else "Detect\nColor"
            findViewById<TextView>(R.id.registerFaceText)?.text =
                if (hindi) "चेहरा\nपंजीकृत करें" else "Register\nFace"
            findViewById<TextView>(R.id.manageFacesText)?.text =
                if (hindi) "चेहरे\nप्रबंधित करें" else "Manage\nFaces"
        }
    }

    override fun onDestroy() {
        speechManager.destroy()

        tts.stop()
        tts.shutdown()
        yoloInterpreter.close()
        placesInterpreter.close()
        currencyInterpreter.close()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun presentResult(englishText: String) {

        if (englishText.isBlank()) return

        if (!isHindi()) {

            runOnUiThread {
                resultText.text = englishText
                speakAndToast(englishText)
            }

            return
        }

        translationManager.translate(
            englishText,

            onSuccess = { hindiText ->

                runOnUiThread {
                    resultText.text = hindiText
                    speakAndToast(hindiText)
                }
            },

            onError = {

                // Fallback to English if translation fails
                runOnUiThread {
                    resultText.text = englishText
                    speakAndToast(englishText)
                }
            }
        )
    }
    private fun speakAndToast(message: String) {
        runOnUiThread {
            if (message.isBlank()) return@runOnUiThread

            // Show the message on screen
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            // Speak the message for the user
            if (::tts.isInitialized) {
                tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }
}