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
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it // Save the Uri for later use
            imageView.setImageURI(it) // Show it on screen

            // Call OCR directly using the URI (Fixes the rotation/hardware bitmap issue)
            ocrManager.recognizeFromUri(
                context = this,
                uri = it,
                onResult = { text ->
                    speakAndToast(text)
                    ocrResultText.text = text},
                onError = { e -> speakAndToast("Read failed: ${e.message}") }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val modelPath = ModelManager.getModelPath(this)

        Log.d("MODEL", modelPath)

        val success = LLMNative.loadModel(modelPath)

        Log.d("LLM", "Loaded = $success")

        llmManager = LLMManager(this)
        llmManager.initialize()
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
        connectionStatus = findViewById(R.id.connectionStatus)
        modeStatus = findViewById(R.id.modeStatus)
        voiceButton = findViewById(R.id.voiceButton)
        speechManager = SpeechManager(this, this)
        intentClassifier = IntentClassifier()
        featureRouter = FeatureRouter()
//        statusIndicator = findViewById(R.id.statusIndicator)
//        cameraStatusIndicator = findViewById(R.id.cameraStatusIndicator)
//        cameraStatusText = findViewById(R.id.cameraStatusText)
        intervalEditText = findViewById(R.id.intervalEditText)
        autoModeSettingsCard = findViewById(R.id.autoModeSettingsCard)

        tts = TextToSpeech(this, this)

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
                if (isChecked) "Currency detection enabled" else "Currency detection disabled"
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

        registerFaceButton.setOnClickListener {

            val bitmap =
                (imageView.drawable as? BitmapDrawable)?.bitmap

            if (bitmap == null) {

                Toast.makeText(
                    this,
                    "Capture or pick an image first",
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
                            "No registered faces",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@launch
                    }

                    val personNames = persons.map { person ->
                        "${person.name} (${person.relation})"
                    }.toTypedArray()

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Registered Faces")
                        .setItems(personNames) { _, position ->

                            val selectedPerson =
                                persons[position]

                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Delete Registered Face")
                                .setMessage(
                                    "Delete ${selectedPerson.name} (${selectedPerson.relation})?"
                                )
                                .setNegativeButton(
                                    "Cancel",
                                    null
                                )
                                .setPositiveButton("Delete") { _, _ ->

                                    lifecycleScope.launch {

                                        try {

                                            faceRepository.deletePerson(
                                                selectedPerson
                                            )

                                            Toast.makeText(
                                                this@MainActivity,
                                                "${selectedPerson.name} deleted",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                        } catch (e: Exception) {

                                            Toast.makeText(
                                                this@MainActivity,
                                                "Delete failed: ${e.message}",
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
                        "Failed to load registered faces: ${e.message}",
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
                Toast.makeText(this, "No image available to process", Toast.LENGTH_SHORT).show()
            }
        }

        detectColorButton.setOnClickListener {
            val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
            if (bitmap == null) {
                speakAndToast("Please capture or select an image first.")
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
            if (selectedImageUri != null) {
                // CASE 1: Use URI if image was picked from Gallery
                ocrManager.recognizeFromUri(
                    context = this,
                    uri = selectedImageUri!!,
                    onResult = { text -> speakAndToast(text) },
                    onError = { e -> speakAndToast("Error: ${e.message}") }
                )
            } else {
                // CASE 2: Use Bitmap if image was captured from ESP32
                val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    ocrManager.recognize(
                        bitmap = bitmap,
                        onResult = { text ->
                            speakAndToast(text)
                            ocrResultText.text = text},
                        onError = { e -> speakAndToast("Error: ${e.message}") }
                    )
                } else {
                    speakAndToast("Please capture or select an image first.")
                }
            }
        }
        updateUIForMode()
    }

    override fun onCommandRecognized(command: String) {

        val intent = intentClassifier.classify(command)
        val feature = featureRouter.route(intent)

        // Keep teammate's voice color detection
        if (feature == FeatureType.COLOR) {

            val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap

            if (bitmap == null) {
                speakAndToast("Please capture or select an image first.")
                return
            }

            detectColorForCommand(bitmap, command)
            return
        }

        // Below is the working code commented for LLM promt response
        //val response = featureExecutor.execute(feature)
        //resultText.text = response
        //speakText(response)
        val prompt = PromptBuilder.build(command)
        Log.d("PROMPT", prompt)

        llmManager.generate(prompt, object : LLMCallback {

            override fun onToken(token: String) {
                // We'll use this later for streaming llama.cpp output.
                // For now, leave it empty.
            }

            override fun onComplete(response: String) {
                runOnUiThread {
                    resultText.text = response
                    speakText(response)
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    resultText.text = message
                    speakText(message)
                }
            }
        })
    }

    private fun detectColorForCommand(bitmap: Bitmap, command: String) {
        thread {
            runOnUiThread { statusText.text = "Detecting color..." }
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
                        "The ${matchingObject.label} appears ${matchingObject.color.lowercase(Locale.getDefault())}."
                    detections.isNotEmpty() -> colorResponse(ColorDetector.analyze(bitmap))
                    else -> "I could not find an object clearly enough to determine its color."
                }
            }
            runOnUiThread {
                resultText.text = response
                statusText.text = "Color detection complete."
                speakAndToast(response)
            }
        }
    }

    private fun colorResponse(result: ColorDetector.ColorResult): String =
        if (result.name == "Unknown") {
            "I could not determine the color clearly."
        } else {
            "The color appears ${result.name.lowercase(Locale.getDefault())}."
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
                    debugText.text = "Loading models..."
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
                    statusText.text = "All models loaded successfully."
                    debugText.text = "Models loaded."
                    detectButton.isEnabled = true
                    currencyToggleButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Error loading models"
                    debugText.visibility = View.VISIBLE
                    debugText.text = "Model Load Error: ${e.message}"
                }
            }
        }
    }

    private fun connectToESP32WiFi() {
        statusText.text = "Connecting to ESP32..."
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
                    statusText.text = "Connected to ESP32-CAM"
                    connectionStatus.text = "Online"
                    connectionStatus.setTextColor(
                        ContextCompat.getColor(
                            this,
                            android.R.color.holo_green_light
                        )
                    )
                    cameraStatusText.text = "Connected"
                    statusIndicator.setBackgroundResource(R.drawable.status_indicator_online)
                    cameraStatusIndicator.setBackgroundResource(R.drawable.status_indicator_online)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Connection failed: ${e.message}"
                    connectionStatus.text = "Offline"
                    connectionStatus.setTextColor(
                        ContextCompat.getColor(
                            this,
                            android.R.color.holo_red_light
                        )
                    )
                    cameraStatusText.text = "Disconnected"
                    statusIndicator.setBackgroundResource(R.drawable.status_indicator_offline)
                    cameraStatusIndicator.setBackgroundResource(R.drawable.status_indicator_offline)
                }
            }
        }
    }

    private fun captureImageFromESP32() {
        statusText.text = "Capturing image..."
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
                            if (isAutoMode) "Auto mode: Image captured"
                            else "Manual capture successful"

                        // Send the original image for processing
                        processAndSpeak(originalBitmap)
                    }

                } else {
                    runOnUiThread {
                        statusText.text = "Capture failed: HTTP ${connection.responseCode}"
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                runOnUiThread { statusText.text = "Capture error: ${e.message}" }
            }
        }
    }

    private fun processAndSpeak(bitmap: Bitmap) {

        thread {

            try {

                runOnUiThread {
                    statusText.text = "Processing..."
                    detectButton.isEnabled = false
                    debugText.visibility = View.GONE
                }

                val sceneCaption = processImage(bitmap)

                runOnUiThread {

                    faceRecognitionFlow.recognize(

                        bitmap = bitmap,

                        onResult = { faceResult ->

                            val recognizedPersons = mutableListOf<String>()

                            if (
                                faceResult != "No face detected" &&
                                faceResult != "Unknown person"
                            ) {
                                recognizedPersons.add(faceResult)
                            }

                            ContextManager.updateFaces(

                                FaceContext(

                                    persons = recognizedPersons

                                )

                            )

                            val finalCaption = when (faceResult) {

                                "No face detected" ->
                                    sceneCaption

                                "Unknown person" ->
                                    "$sceneCaption The person is unknown."

                                else ->
                                    "$sceneCaption This is $faceResult."
                            }

                            resultText.text = finalCaption

                            speakText(finalCaption)

                            statusText.text = "Processing complete."

                            detectButton.isEnabled = true
                        },

                        onError = { error ->

                            ContextManager.updateFaces(
                                FaceContext()
                            )

                            Log.e(
                                "FACE_RECOGNITION",
                                error
                            )

                            resultText.text = sceneCaption

                            speakText(sceneCaption)

                            statusText.text =
                                "Processing complete."

                            detectButton.isEnabled = true
                        }
                    )
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "Error: ${e.message}"

                    debugText.visibility =
                        View.VISIBLE

                    debugText.text =
                        "Processing Error: ${e.message}"

                    detectButton.isEnabled = true
                }
            }
        }
    }

    private fun runOCR(bitmap: Bitmap) {

        val processedBitmap =
            ImagePreprocessor().process(bitmap)

        OCRManager().recognize(

            processedBitmap,

            onResult = { text ->
                ContextManager.updateOCR(
                    OCRContext(
                        text = text,
                        available = text.isNotBlank()
                    )
                )
                runOnUiThread {
                    ocrResultText.text = text
                }
            },
            onError = {

                ContextManager.updateOCR(

                    OCRContext()

                )

                runOnUiThread {

                    ocrResultText.text = "OCR Failed"

                }

            }
        )
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
                "Auto mode enabled (${autoCaptureInterval / 1000}s interval)",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            handler.removeCallbacks(autoCaptureRunnable)
            Toast.makeText(this, "Manual mode enabled", Toast.LENGTH_SHORT).show()
        }
        updateUIForMode()
    }

    private fun updateUIForMode() {
        modeToggleButton.text = if (isAutoMode) "Manual" else "Auto"
        modeStatus.text = if (isAutoMode) "Auto" else "Manual"
        modeStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isAutoMode) android.R.color.holo_blue_light else android.R.color.holo_orange_light
            )
        )
        captureButton.isEnabled = !isAutoMode
        statusText.text = if (isAutoMode) "Auto Mode Active" else "Manual Mode Active"
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
            return if (currencyResult.isNotEmpty()) currencyResult else "No currency detected."
        }

        val (scene, confidence) = predictScene(bitmap)
        val confidencePercentage = (confidence * 100).toInt()
        val detections = detectObjects(bitmap)
        latestScene.clear()
        latestScene.addAll(detections)

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
                position = getObjectPosition(it.xCenterNorm),
                color = it.color
            )
        }.toMutableList()

        val detectedColors = mutableMapOf<String, String>()
        detections.forEach {
            detectedColors[it.label] = it.color
        }
        ContextManager.updateColors(
            ColorContext(
                colors = detectedColors
            )
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
                return counts.map { (label, count) ->
                    if (count > 1)
                        "$count notes of $label"
                    else
                        "$label note"
                }.joinToString(", ") + " detected."
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
            val color = ColorDetector.detectDominantColor(cropObject(bitmap, it.box))
            ObjectInfo(it.label, color, it.box.x, it.box)
        }
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

    private fun cropObject(bitmap: Bitmap, box: BoundingBox): Bitmap {
        val left = ((box.x - box.w / 2f) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = ((box.y - box.h / 2f) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val width =
            (box.w * bitmap.width).toInt().coerceAtLeast(1).coerceAtMost(bitmap.width - left)
        val height =
            (box.h * bitmap.height).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun summarizeEntities(detections: List<ObjectInfo>): String {
        val formatList = { objects: List<ObjectInfo> ->
            val items = objects.map { objectInfo ->
                val color = objectInfo.color.lowercase(Locale.getDefault())
                when {
                    objectInfo.label.equals("person", ignoreCase = true) && color != "unknown" ->
                        "a person wearing predominantly $color"
                    color != "unknown" -> "a $color ${objectInfo.label}"
                    else -> "a ${objectInfo.label}"
                }
            }
            when {
                items.isEmpty() -> ""
                items.size == 1 -> items.first()
                items.size == 2 -> items.joinToString(" and ")
                else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
            }
        }
        val left = formatList(detections.filter { it.xCenterNorm < 0.33f })
        val center = formatList(detections.filter { it.xCenterNorm in 0.33f..0.67f })
        val right = formatList(detections.filter { it.xCenterNorm > 0.67f })
        val parts = mutableListOf<String>()
        if (center.isNotEmpty()) parts.add("in front of you, there is $center")
        if (left.isNotEmpty()) parts.add("to your left, I see $left")
        if (right.isNotEmpty()) parts.add("and to your right is $right")
        return if (parts.isEmpty()) "" else parts.joinToString(", ") + "."
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
            hint = "Person name"
        }

        val relationInput = EditText(this).apply {
            hint = "Relation, e.g. Friend"
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
            .setTitle("Register Face")
            .setView(container)
            .setPositiveButton("Register") { _, _ ->

                faceRegistrationFlow.register(
                    bitmap = bitmap,
                    name = nameInput.text.toString(),
                    relation = relationInput.text.toString(),
                    onSuccess = { personId ->

                        runOnUiThread {

                            Toast.makeText(
                                this,
                                "Face registered. ID: $personId",
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
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale.ENGLISH
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