package com.example.voxcargo // Replace with your actual package name

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity(), RecognitionListener, TextToSpeech.OnInitListener {

    private lateinit var buttonListen: ImageButton
    private lateinit var textViewRecognizedText: TextView
    private lateinit var textViewStatus: TextView
    private val client = OkHttpClient()
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var speechRecognizerIntent: Intent
    private lateinit var tts: android.speech.tts.TextToSpeech
    private var isTtsInitialized = false

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

        tts = TextToSpeech(this, this)

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
        Log.d("SpeechRecognizer", "Partial results: $partialResults")
        if (partialResults != null) {
            val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partialText = matches[0]
                Log.d("PartialSpeech", "Partial Text: $partialText")
                // Update your UI with partialText if desired
                // textViewRecognizedText.text = partialText // Example
            }
        }
    }

    override fun onResults(results: Bundle?) {
        Log.d("Speech", "onResults called")
        if (results != null) {
            // Retrieve the list of recognized words/phrases
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                // The first match is usually the most confident one
                isTtsInitialized = true
                val recognizedText = matches[0]
                Log.d("Speech", "Recognized: $recognizedText")
                textViewRecognizedText.text = recognizedText
                textViewStatus.text =
                    getString(R.string.recognized_result) // Or "Idle", "Tap to speak" etc.
                if (!recognizedText.isNullOrBlank()) {
                    speakOut(recognizedText)
                } else {
                    // Optional: Log why it didn't auto-speak
                    if (!isTtsInitialized) {
                        android.util.Log.w("TTS_AutoSpeak", "TTS not ready when trying to auto-speak.")
                    }
                    if (recognizedText.isNullOrBlank()) {
                        android.util.Log.w("TTS_AutoSpeak", "No valid text to auto-speak.")
                    }
                }

                // You can iterate through all matches if needed:
                // for (match in matches) {
                //     Log.d("Speech", "Potential match: $match")
                // }

                val textApiUrl = "https://localhost:8080/api/transcript" // Replace with your actual URL
                sendTextToBackend(recognizedText, textApiUrl)
            } else {
                Log.d("Speech", "No speech recognized or results are empty.")
                textViewRecognizedText.text = getString(R.string.no_speech_detected)
                textViewStatus.text = getString(R.string.try_again)
                isTtsInitialized = false
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




    // This function should be called after text is recognized
    private fun sendTextToBackend(recognizedText: String, backendUrl: String) {
        if (recognizedText.isBlank()) {
            Log.w("SendText", "Recognized text is blank. Not sending.")
            // Optionally inform the user or handle differently
            return
        }

        Log.d("SendText", "Attempting to send text: \"$recognizedText\" to $backendUrl")
        // Optionally update UI to indicate sending text
        // textViewStatus.text = "Sending text..."

        // Perform network operation on a background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Create JSON Object
                val jsonObject = JSONObject()
                jsonObject.put("content", recognizedText) // Key "transcription" or whatever your backend expects
                // Add other data if needed:
                // jsonObject.put("userId", "user123")
                // jsonObject.put("timestamp", System.currentTimeMillis())

                // 2. Create Request Body
                val requestBody = jsonObject.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                // 3. Build the Request
                val request = Request.Builder()
                    .url(backendUrl)
                    // .header("Authorization", "Bearer YOUR_AUTH_TOKEN") // If you need auth
                    .post(requestBody)
                    .build()

                // 4. Execute the Request
                client.newCall(request).execute().use { response ->
                    withContext(Dispatchers.Main) { // Switch back to Main thread for UI updates
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "Unknown error"
                            Log.e("SendText", "Failed to send text: ${response.code} - $errorBody")
                            Toast.makeText(
                                this@MainActivity,
                                "Error sending text: ${response.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            // Update UI accordingly
                            // textViewStatus.text = "Error sending text."
                        } else {
                            val responseBody = response.body?.string()
                            Log.i("SendText", "Text sent successfully: ${response.code} - $responseBody")
                            Toast.makeText(
                                this@MainActivity,
                                "Text sent to backend!",
                                Toast.LENGTH_SHORT
                            ).show()
                            // TODO: Handle successful response from backend
                            // e.g., parse responseBody, update UI, etc.
                            // textViewStatus.text = "Text processed by backend."
                        }
                    }
                }
            } catch (e: org.json.JSONException) {
                Log.e("SendText", "JSONException: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error creating JSON data.", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Log.e("SendText", "IOException: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network error sending text.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("SendText", "Exception: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Unexpected error sending text.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }



    // --- TextToSpeech.OnInitListener Implementation ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.getDefault()) // Use device default language
            // Or specify: val result = tts.setLanguage(Locale("en", "US"))

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language specified is not supported (${Locale.getDefault()})!")
                Toast.makeText(this, "TTS language not supported.", Toast.LENGTH_SHORT).show()
                isTtsInitialized = false // Explicitly set to false
            } else {
                Log.i("TTS", "TextToSpeech Initialized successfully with language: ${Locale.getDefault()}.")
                isTtsInitialized = true // <<< TTS IS NOW READY >>>

                // Set up an utterance progress listener to know when speaking starts/stops/errors
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d("TTS_Listener", "Started speaking: $utteranceId")
                        // You could update UI here, e.g., show a "speaking" icon
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d("TTS_Listener", "Finished speaking: $utteranceId")
                        // You could update UI here
                    }

                    @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, 0)"))
                    override fun onError(utteranceId: String?) {
                        Log.e("TTS_Listener", "Error speaking (deprecated): $utteranceId")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e("TTS_Listener", "Error speaking: $utteranceId, ErrorCode: $errorCode")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "TTS Error: ${getTtsErrorText(errorCode)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        } else {
            Log.e("TTS", "TextToSpeech Initialization Failed! Status: $status")
            Toast.makeText(this, "TTS initialization failed.", Toast.LENGTH_LONG).show()
            isTtsInitialized = false // Explicitly set to false
        }
    }

    // --- Core Text-To-Speech Speak Method ---
    private fun speakOut(textToSpeak: String) {
        if (!isTtsInitialized) {
            Log.w("TTS_SpeakOut", "TTS not initialized yet. Cannot speak: \"$textToSpeak\"")
            Toast.makeText(this, "TTS is not ready to speak.", Toast.LENGTH_SHORT).show()
            return // Crucial: Do not proceed if TTS is not ready
        }

        if (textToSpeak.isBlank()) {
            Log.w("TTS_SpeakOut", "No text provided to speak.")
            // Toast.makeText(this, "Nothing to speak.", Toast.LENGTH_SHORT).show() // Optional
            return
        }

        // Generate a unique utterance ID for tracking this speech request
        val utteranceId = this.hashCode().toString() + "_" + System.currentTimeMillis()

        // QUEUE_FLUSH: Clears any ongoing or pending speech and plays the new text immediately.
        // QUEUE_ADD: Adds the new text to the end of the speaking queue.
        // Use QUEUE_FLUSH for immediate response to recognized text.
        val result = tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        if (result == TextToSpeech.ERROR) {
            Log.e("TTS_SpeakOut", "Error queuing/speaking text: \"$textToSpeak\"")
            Toast.makeText(this, "Error occurred while trying to speak.", Toast.LENGTH_SHORT).show()
        } else {
            Log.i("TTS_SpeakOut", "Successfully queued text to speak: \"$textToSpeak\" with ID: $utteranceId")
        }
    }

    private fun getTtsErrorText(errorCode: Int): String { // Defined around line 357 in your full code
        return when (errorCode) {
            TextToSpeech.ERROR_SYNTHESIS -> "TTS Synthesis Error"
            TextToSpeech.ERROR_SERVICE -> "TTS Service Failure"
            TextToSpeech.ERROR_OUTPUT -> "TTS Output Error"
            TextToSpeech.ERROR_NETWORK -> "TTS Network Error"
            TextToSpeech.ERROR_NETWORK_TIMEOUT -> "TTS Network Timeout"
            TextToSpeech.ERROR_INVALID_REQUEST -> "TTS Invalid Request"
            TextToSpeech.ERROR_NOT_INSTALLED_YET -> "TTS Not Installed Yet"
            else -> "Unknown TTS error"
        }
    }

}
