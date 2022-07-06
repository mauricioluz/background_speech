package com.umair.background_stt

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.ADJUST_MUTE
import android.os.IBinder
import android.util.Log
import com.umair.background_stt.models.SpeechResult
import com.umair.background_stt.speech.*
import com.umair.background_stt.speech.Speech.stopDueToDelay
import java.util.*

class SpeechListenService : Service(), stopDueToDelay {

    companion object {
        private val TAG = "SpeechListenService"
        private var context: Context? = null

        @JvmStatic
        private var feedBackProvider: TextToSpeechFeedbackProvider? = null

        @JvmStatic
        internal var isListening = true

        @JvmStatic
        internal var isSpeaking = false

        fun speak(text: String, queue: Boolean) {
            feedBackProvider?.speak(text, forceMode = true, queue = queue)
        }

        fun setSpeaker(pitch: Float, rate: Float) {
            feedBackProvider?.setSpeaker(pitch, rate)
        }

        fun doOnIntentConfirmation(text: String, positiveText: String, negativeText: String, voiceInputMessage: String, voiceInput: Boolean) {
            feedBackProvider?.setConfirmationData(text, positiveText, negativeText, voiceInputMessage, voiceInput)

            if (voiceInput) {
                feedBackProvider?.speak(text)
            } else {
                feedBackProvider?.speak("$text say: $positiveText or $negativeText")
            }
        }

        fun cancelConfirmation() {
            feedBackProvider?.cancelConfirmation(true)
        }

        fun stopSpeechListener() {
            feedBackProvider?.disposeTextToSpeech()
            Speech.getInstance().shutdown()
        }

        fun isListening(isListening: Boolean) {
            this.isListening = isListening

            if (isListening) {
                feedBackProvider?.resumeSpeechService()
            } else {
                Speech.getInstance().stopListening()
                // context?.adjustSound(AudioManager.ADJUST_RAISE)
            }
        }

        fun isListening(): Boolean {
            return isListening
        }

        fun isSpeaking(): Boolean {
            return isSpeaking;
        }

        fun startListening() {
            if (Speech.isActive()) {
                Speech.getInstance().startListening(object : SpeechDelegate {
                    override fun onStartOfSpeech() {
                    }

                    override fun onSpeechRmsChanged(value: Float) {
                    }

                    override fun onSpeechPartialResults(results: List<String>) {
                        if (results.isNotEmpty() && results.size > 1) {
                            for (partial in results) {
                                if (partial.isNotEmpty()) {
                                    if (partial.isNotEmpty()) {
                                        sendResults(partial, true)
                                    }
                                }
                            }
                        } else {
                            if (results.first().isNotEmpty()) {
                                sendResults(results.first(), true)
                            }
                        }

                    }

                    override fun onSpeechResult(result: String) {
                        if (result.isNotEmpty()) {
                            sendResults(result, false)
                        }
                    }
                })
            }
        }

        private fun sendResults(result: String, isPartial: Boolean) {

            if (feedBackProvider?.isConfirmationInProgress()!!) {
                feedBackProvider?.doOnConfirmationProvided(result)
            } else {
                BackgroundSttPlugin.eventSink?.success(SpeechResult(result, isPartial).toString())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        context = this

        feedBackProvider = TextToSpeechFeedbackProvider(this)

        BackgroundSttPlugin.binaryMessenger?.let {
            Log.i(TAG, "$TAG service running.")
            BackgroundSttPlugin.registerWith(it, this)
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Speech.isActive()) {
            Speech.getInstance().setListener(this)
            if (Speech.getInstance().isListening) {
                Speech.getInstance().stopListening()
                // muteSounds()
            } else {
                System.setProperty("rx.unsafe-disable", "True")
                try {
                    startListening()
                } catch (exc: SpeechRecognitionNotAvailable) {
                    Log.e(TAG, "${exc.message}")
                } catch (exc: GoogleVoiceTypingDisabledException) {
                    Log.e(TAG, "${exc.message}")
                }
                // muteSounds()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onSpecifiedCommandPronounced(event: String) {

        if (Speech.isActive()) {
            if (Speech.getInstance().isListening) {
                //muteSounds()
                //Speech.getInstance().stopListening()
            } else {
                try {
                    //Log.i(TAG, "$TAG onSpecifiedCommandPronounced: Restart Listening..")
                    //Speech.getInstance().stopTextToSpeech()
                    startListening()
                } catch (exc: SpeechRecognitionNotAvailable) {
                    Log.e(TAG, "${exc.message}")
                } catch (exc: GoogleVoiceTypingDisabledException) {
                    Log.e(TAG, "${exc.message}")
                }
                if (!feedBackProvider?.isConfirmationInProgress()!!) {
                    // muteSounds()
                }
            }
        }
    }

    /**
     * Function to remove the beep sound of voice recognizer.
     */
    private fun muteSounds() {
        if (isListening)
            adjustSound(ADJUST_MUTE)
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        Log.i(TAG, "$TAG onTaskRemoved")
        val service = PendingIntent.getService(applicationContext, Random().nextInt(),
                Intent(applicationContext, SpeechListenService::class.java), PendingIntent.FLAG_ONE_SHOT)
        val alarmManager = (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        alarmManager[AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000] = service
        super.onTaskRemoved(rootIntent)
    }
}