package com.example.voxcargo // Replace with your actual package name

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.text
import androidx.core.content.ContextCompat
import com.example.voxcargo.R
import java.util.Locale

class MainActivity : AppCompatActivity(), RecognitionListener {

    private lateinit var buttonListen: ImageButton
    private lateinit var textViewRecognizedText: TextView
    private lateinit var textViewStatus: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechRecognizerIntent: Intent

    private var isListening = false

    // Activity Result Launcher for permission request
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("Permission", "RECORD_AUDIO permission granted")
                initializeSpeechRecognizer()
                startListening()
            } else {
                Log.e("Permission", "RECORD_AUDIO permission denied")
                textViewStatus.text = getString(R.string.permission_denied)
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonListen = findViewById(R.id.buttonListen)
        textViewRecognizedText = findViewById(R.id.textViewRecognizedText)
        textViewStatus = findViewById(R.id.textViewStatus)

        textViewStatus.text = getString(R.string.idle)

        buttonListen.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                checkAndStartListening()
            }
        }

        // Check for SpeechRecognizer availability
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("Speech", "Speech recognition is not available on this device.")
            textViewStatus.text = getString(R.string.speech_not_available)
            buttonListen.isEnabled = false
            Toast.makeText(this, getString(R.string.speech_not_available), Toast.LENGTH_LONG).show()
        } else {
            // Initialize only if available, permission will be checked before starting
            initializeSpeechRecognizer()
        }
    }

    private fun initializeSpeechRecognizer() {
        if (speechRecognizer == null) { // Initialize only once
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(this)
        }

        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
            ) // Use device default language
            //putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // Or specify a language
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speak_now))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Enable partial results
        }
    }

    private fun checkAndStartListening() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted
                startListening()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Explain to the user why the permission is needed (optional)
                Toast.makeText(
                    this,
                    "Audio recording permission is required to recognize speech.",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            else -> {
                // Directly request the permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("Speech", "Cannot start. Speech recognition not available.")
            textViewStatus.text = getString(R.string.speech_not_available)
            return
        }
        if (speechRecognizer == null) { // Ensure it's initialized
            initializeSpeechRecognizer()
        }
        speechRecognizer?.startListening(speechRecognizerIntent)
        // Note: Actual listening start is signaled by onReadyForSpeech
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        // Note: Actual stop is signaled by onEndOfSpeech or onError
    }

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d("Speech", "Ready for speech")
        isListening = true
        textViewStatus.text = getString(R.string.listening)
        textViewRecognizedText.text = "" // Clear previous text
    }

    override fun onBeginningOfSpeech() {
        Log.d("Speech", "Beginning of speech")
        textViewStatus.text = getString(R.string.listening) // Reinforce status
    }

    override fun onRmsChanged(rmsdB: Float) {
        // You can use this to show voice activity visualizer if needed
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        Log.d("Speech", "Buffer received")
    }

    override fun onEndOfSpeech() {
        Log.d("Speech", "End of speech")
        isListening = false
        textViewStatus.text = getString(R.string.processing)
    }

    override fun onError(error: Int) {
        val errorMessage = getErrorText(error)
        Log.e("Speech", "Error: $errorMessage (code: $error)")
        textViewRecognizedText.text = "Error: $errorMessage"
        textViewStatus.text = getString(R.string.error)
        isListening = false
        // Optionally, re-initialize or prompt user
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun onPartialResults(partialResults: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun onResults(results: Bundle?) {
        Log.d("Speech", "onResults called")
        if (results != null) {
            // Retrieve the list of recognized words/phrases
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                // The first match is usually the most confident one
                val recognizedText = matches[0]
                Log.d("Speech", "Recognized: $recognizedText")
                textViewRecognizedText.text = recognizedText
                textViewStatus.text =
                    getString(R.string.recognized_result) // Or "Idle", "Tap to speak" etc.

                // You can iterate through all matches if needed:
                // for (match in matches) {
                //     Log.d("Speech", "Potential match: $match")
                // }

                // TODO: Add any further actions you want to take with the recognizedText
                // For example, process the command, send it to another part of your app, etc.

            } else {
                Log.d("Speech", "No speech recognized or results are empty.")
                textViewRecognizedText.text = getString(R.string.no_speech_detected)
                textViewStatus.text = getString(R.string.try_again)
            }
        } else {
            Log.d("Speech", "Results bundle is null.")
            textViewRecognizedText.text = getString(R.string.no_results_bundle)
            textViewStatus.text = getString(R.string.error_occurred) // Or a more specific error
        }
        // Reset listening state as results are final for this session
        isListening = false
        // Note: onEndOfSpeech should have already set the status to "Processing"
        // You might want to update the status here to something like "Idle" or "Result shown"
        // depending on your desired UX flow. For instance:
        // textViewStatus.text = getString(R.string.idle)
    }

    // Helper method to get error text (you likely already have this from your previous code)
    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown speech recognition error"
        }
    }
}
