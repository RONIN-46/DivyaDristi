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

private data class ObjectInfo(
    val label: String,
    val color: String,
    val xCenterNorm: Float,
    val box: BoundingBox
)

private data class Detection(val box: BoundingBox, val label: String, val confidence: Float)
private data class BoundingBox(val x: Float, val y: Float, val w: Float, val h: Float)

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView
    private lateinit var debugText: TextView
    private lateinit var detectButton: Button
    private lateinit var connectButton: Button
    private lateinit var modeToggleButton: Button
    private lateinit var captureButton: Button
    private lateinit var ocrButton: Button
    private lateinit var pickButton: Button
    private lateinit var ocrResultText: TextView
    private lateinit var statusText: TextView
    private lateinit var currencyToggleButton: Switch
    private lateinit var tts: TextToSpeech
    private lateinit var yoloInterpreter: Interpreter
    private lateinit var placesInterpreter: Interpreter
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

    private lateinit var currencyInterpreter: Interpreter
    private lateinit var currencyLabels: List<String>
    private var isCurrencyDetectionEnabled = false

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

    // Launcher for selecting an image from the gallery
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                try {
                    val inputStream = contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    imageView.setImageBitmap(bitmap)
                    processAndSpeak(bitmap) // Process image immediately
                    statusText.text = "Gallery image loaded."
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        imageView = findViewById(R.id.imageView)
        resultText = findViewById(R.id.resultText)
        debugText = findViewById(R.id.debugText)
        detectButton = findViewById(R.id.detectButton)
        connectButton = findViewById(R.id.connectButton)
        modeToggleButton = findViewById(R.id.modeToggleButton)
        captureButton = findViewById(R.id.captureButton)
        ocrButton = findViewById(R.id.ocrButton)
        pickButton = findViewById(R.id.pickButton)
        ocrResultText = findViewById(R.id.ocrResultText)
        statusText = findViewById(R.id.statusText)
        currencyToggleButton = findViewById(R.id.currencyToggleButton)
        connectionStatus = findViewById(R.id.connectionStatus)
        modeStatus = findViewById(R.id.modeStatus)
//        statusIndicator = findViewById(R.id.statusIndicator)
//        cameraStatusIndicator = findViewById(R.id.cameraStatusIndicator)
//        cameraStatusText = findViewById(R.id.cameraStatusText)
        intervalEditText = findViewById(R.id.intervalEditText)
        autoModeSettingsCard = findViewById(R.id.autoModeSettingsCard)

        tts = TextToSpeech(this, this)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.INTERNET
            ),
            1
        )

        loadModels()

        currencyToggleButton.setOnCheckedChangeListener { _, isChecked ->
            isCurrencyDetectionEnabled = isChecked
            val message = if (isChecked) "Currency detection enabled" else "Currency detection disabled"
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

        connectButton.setOnClickListener { connectToESP32WiFi() }
        modeToggleButton.setOnClickListener { toggleCaptureMode() }
        captureButton.setOnClickListener { if (!isAutoMode) captureImageFromESP32() }
        
        pickButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        detectButton.setOnClickListener {
            val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                processAndSpeak(bitmap)
            } else {
                Toast.makeText(this, "No image available to process", Toast.LENGTH_SHORT).show()
            }
        }
        
        ocrButton.setOnClickListener {
            val bitmap = (imageView.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val processedBitmap = ImagePreprocessor().process(bitmap)
                OCRManager().recognize(
                    processedBitmap,
                    onResult = { text ->
                        runOnUiThread {
                            ocrResultText.text = text
                            if (text.isNotBlank()) speakText(text)
                        }
                    },
                    onError = { runOnUiThread { ocrResultText.text = "OCR Failed" } }
                )
            } else {
                Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show()
            }
        }
        updateUIForMode()
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
                    connectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    cameraStatusText.text = "Connected"
                    statusIndicator.setBackgroundResource(R.drawable.status_indicator_online)
                    cameraStatusIndicator.setBackgroundResource(R.drawable.status_indicator_online)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Connection failed: ${e.message}"
                    connectionStatus.text = "Offline"
                    connectionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
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
                        imageView.setImageBitmap(processedBitmap)
                        statusText.text = if (isAutoMode) "Auto mode: Image captured" else "Manual capture successful"
                        processAndSpeak(processedBitmap) // Automatically process capture
                    }
                } else {
                    runOnUiThread { statusText.text = "Capture failed: HTTP ${connection.responseCode}" }
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

                val caption = processImage(bitmap)
                runOnUiThread {
                    resultText.text = caption
                    speakText(caption)
                    statusText.text = "Processing complete."
                    detectButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Error: ${e.message}"
                    debugText.visibility = View.VISIBLE
                    debugText.text = "Processing Error: ${e.message}"
                    detectButton.isEnabled = true
                }
            }
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
            Toast.makeText(this, "Auto mode enabled (${autoCaptureInterval / 1000}s interval)", Toast.LENGTH_SHORT).show()
        } else {
            handler.removeCallbacks(autoCaptureRunnable)
            Toast.makeText(this, "Manual mode enabled", Toast.LENGTH_SHORT).show()
        }
        updateUIForMode()
    }

    private fun updateUIForMode() {
        modeToggleButton.text = if (isAutoMode) "Manual" else "Auto"
        modeStatus.text = if (isAutoMode) "Auto" else "Manual"
        modeStatus.setTextColor(ContextCompat.getColor(this, if (isAutoMode) android.R.color.holo_blue_light else android.R.color.holo_orange_light))
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

        val objectSummary = if (detections.isNotEmpty()) summarizeEntities(detections) else "I don't see any other major objects."
        return "$sceneDescription $objectSummary"
    }

    private fun detectCurrency(bitmap: Bitmap): String {
        try {
            val resized = Bitmap.createScaledBitmap(bitmap, currencyInputSize, currencyInputSize, true)
            val byteBuffer = ByteBuffer.allocateDirect(1 * currencyInputSize * currencyInputSize * 3 * 4).apply { order(ByteOrder.nativeOrder()) }
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
                        currencyDetections.add(Detection(BoundingBox(output[0][0][i], output[0][1][i], output[0][2][i], output[0][3][i]), currencyLabels[classIndex], confidence))
                    }
                }
            }
            val finalDetections = nonMaxSuppression(currencyDetections, 0.5f)
            if (finalDetections.isNotEmpty()) {
                val counts = finalDetections.groupingBy { it.label }.eachCount()
                return counts.map { (l, c) -> if (c > 1) "$c notes of $l" else "$l note" }.joinToString(", ") + " detected."
            }
            return ""
        } catch (e: Exception) { return "" }
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
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply { order(ByteOrder.nativeOrder()) }
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
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply { order(ByteOrder.nativeOrder()) }
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
                detections.add(Detection(BoundingBox(output[0][0][i], output[0][1][i], output[0][2][i], output[0][3][i]), labels[classIndex], confidence))
            }
        }
        return nonMaxSuppression(detections).map {
            val color = ColorDetector.detectDominantColor(cropObject(bitmap, it.box))
            ObjectInfo(it.label, color, it.box.x, it.box)
        }
    }

    private fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float = 0.5f): List<Detection> {
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
        val width = (box.w * bitmap.width).toInt().coerceAtLeast(1).coerceAtMost(bitmap.width - left)
        val height = (box.h * bitmap.height).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun summarizeEntities(detections: List<ObjectInfo>): String {
        val formatList = { map: Map<String, Int> ->
            val items = map.map { (label, count) -> if (count > 1) "$count ${label}s" else "a $label" }
            when {
                items.isEmpty() -> ""
                items.size == 1 -> items.first()
                items.size == 2 -> items.joinToString(" and ")
                else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
            }
        }
        val left = formatList(detections.filter { it.xCenterNorm < 0.33f }.groupingBy { it.label }.eachCount())
        val center = formatList(detections.filter { it.xCenterNorm in 0.33f..0.67f }.groupingBy { it.label }.eachCount())
        val right = formatList(detections.filter { it.xCenterNorm > 0.67f }.groupingBy { it.label }.eachCount())
        val parts = mutableListOf<String>()
        if (center.isNotEmpty()) parts.add("in front of you, there is $center")
        if (left.isNotEmpty()) parts.add("to your left, I see $left")
        if (right.isNotEmpty()) parts.add("and to your right is $right")
        return if (parts.isEmpty()) "" else parts.joinToString(", ") + "."
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale.ENGLISH
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
        yoloInterpreter.close()
        placesInterpreter.close()
        currencyInterpreter.close()
        handler.removeCallbacksAndMessages(null)
    }
}