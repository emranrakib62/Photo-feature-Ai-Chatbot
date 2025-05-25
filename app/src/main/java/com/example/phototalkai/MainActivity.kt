package com.example.phototalkai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var uploadedImageView: ImageView
    private lateinit var uploadButton: Button
    private lateinit var speakButton: Button
    private lateinit var featuresTextView: TextView
    private lateinit var tts: TextToSpeech

    private val detectedFeatures = mutableListOf<String>()
    private var featureIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private var speakRunnable: Runnable? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private val getImageResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                uploadedImageView.setImageBitmap(bitmap)
                analyzeImage(bitmap)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        uploadedImageView = findViewById(R.id.uploadedImageView)
        uploadButton = findViewById(R.id.uploadButton)
        speakButton = findViewById(R.id.speakButton)
        featuresTextView = findViewById(R.id.featuresTextView)

        tts = TextToSpeech(this, this)

        requestPermissionLauncher.launch(Manifest.permission.CAMERA)

        uploadButton.setOnClickListener {
            getImageResult.launch("image/*")
        }

        speakButton.setOnClickListener {
            startAutoSpeakFeatures()
        }
    }

    private fun analyzeImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        labeler.process(image)
            .addOnSuccessListener { labels ->
                detectedFeatures.clear()
                featureIndex = 0

                if (labels.isEmpty()) {
                    featuresTextView.text = "No object detected"
                } else {
                    val topLabels = labels.take(15)
                    detectedFeatures.addAll(topLabels.map { it.text })
                    featuresTextView.text = "Features detected:\n${detectedFeatures.joinToString("\n")}"
                }
            }
            .addOnFailureListener {
                featuresTextView.text = "Error detecting image"
            }
    }

    private fun startAutoSpeakFeatures() {
        featureIndex = 0
        speakRunnable?.let { handler.removeCallbacks(it) }

        speakRunnable = object : Runnable {
            override fun run() {
                if (featureIndex < detectedFeatures.size) {
                    if (!tts.isSpeaking) {
                        val feature = detectedFeatures[featureIndex]
                        tts.speak(feature, TextToSpeech.QUEUE_FLUSH, null, null)
                        featureIndex++
                    }
                    handler.postDelayed(this, 3000)
                } else {
                    Toast.makeText(this@MainActivity, "All features spoken", Toast.LENGTH_SHORT).show()
                }
            }
        }

        handler.post(speakRunnable!!)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = tts.setLanguage(Locale.US)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "TTS language not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speakRunnable?.let { handler.removeCallbacks(it) }
        tts.shutdown()
    }
}
