package com.rtnaudiolib

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.facebook.react.modules.core.PermissionListener
import java.io.IOException
import java.util.*
import kotlin.math.log10

//Imp - this should reflect proper name
import com.rtnaudiolib.NativeARPModuleSpec;

class NativeARPModule(private val reactContext: ReactApplicationContext) :
NativeARPModuleSpec(reactContext), PermissionListener {
    private var audioFileURL = ""
    private var subsDurationMillis = 500
    private var _meteringEnabled = false
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recorderRunnable: Runnable? = null
    private var mTask: TimerTask? = null
    private var mTimer: Timer? = null
    private var pausedRecordTime = 0L
    private var totalPausedRecordTime = 0L
    var recordHandler: Handler? = Handler(Looper.getMainLooper())

    override fun getName() = NAME

    override fun startRecorder(path: String, audioSet: ReadableMap?, meteringEnabled: Boolean, promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // On devices that run Android 10 (API level 29) or higher
                // your app can contribute to well-defined media collections such as MediaStore.Downloads without requesting any storage-related permissions
                // https://developer.android.com/about/versions/11/privacy/storage#permissions-target-11
                if (Build.VERSION.SDK_INT < 29 &&
                        (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED))  {
                    ActivityCompat.requestPermissions((currentActivity)!!, arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
                    promise.reject("No permission granted.", "Try again after adding permission.")
                    return
                } else if (ActivityCompat.checkSelfPermission(reactContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((currentActivity)!!, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
                    promise.reject("No permission granted.", "Try again after adding permission.")
                    return
                }
            }
        } catch (ne: NullPointerException) {
            Log.w(NAME /*tag*/, ne.toString())
            promise.reject("No permission granted.", "Try again after adding permission.")
            return
        }
        audioFileURL = if (((path == "DEFAULT"))) "${reactContext.cacheDir}/$defaultFileName" else path
        _meteringEnabled = meteringEnabled

        if (mediaRecorder == null) {
            mediaRecorder = MediaRecorder(reactContext)
        }

        if (audioSet != null) {
            mediaRecorder!!.setAudioSource(if (audioSet.hasKey("AudioSourceAndroid")) audioSet.getInt("AudioSourceAndroid") else MediaRecorder.AudioSource.MIC)
            mediaRecorder!!.setOutputFormat(if (audioSet.hasKey("OutputFormatAndroid")) audioSet.getInt("OutputFormatAndroid") else MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder!!.setAudioEncoder(if (audioSet.hasKey("AudioEncoderAndroid")) audioSet.getInt("AudioEncoderAndroid") else MediaRecorder.AudioEncoder.AAC)
            mediaRecorder!!.setAudioSamplingRate(if (audioSet.hasKey("AudioSamplingRateAndroid")) audioSet.getInt("AudioSamplingRateAndroid") else 48000)
            mediaRecorder!!.setAudioEncodingBitRate(if (audioSet.hasKey("AudioEncodingBitRateAndroid")) audioSet.getInt("AudioEncodingBitRateAndroid") else 128000)
            mediaRecorder!!.setAudioChannels(if (audioSet.hasKey("AudioChannelsAndroid")) audioSet.getInt("AudioChannelsAndroid") else 2)
        } else {
            mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder!!.setAudioEncodingBitRate(128000)
            mediaRecorder!!.setAudioSamplingRate(48000)
        }
        mediaRecorder!!.setOutputFile(audioFileURL)

        try {
            mediaRecorder!!.prepare()
            totalPausedRecordTime = 0L
            mediaRecorder!!.start()
            val systemTime = SystemClock.elapsedRealtime()
            recorderRunnable = object : Runnable {
                override fun run() {
                    val time = SystemClock.elapsedRealtime() - systemTime - totalPausedRecordTime
                    val obj = Arguments.createMap()
                    obj.putDouble("currentPosition", time.toDouble())
                    if (_meteringEnabled) {
                        var maxAmplitude = 0
                        if (mediaRecorder != null) {
                            maxAmplitude = mediaRecorder!!.maxAmplitude
                        }
                        var dB = -160.0
                        val maxAudioSize = 32767.0
                        if (maxAmplitude > 0) {
                            dB = 20 * log10(maxAmplitude / maxAudioSize)
                        }
                        obj.putInt("currentMetering", dB.toInt())
                    }
                    sendEvent(reactContext, "rn-recordback", obj)
                    recordHandler!!.postDelayed(this, subsDurationMillis.toLong())
                }
            }
            (recorderRunnable as Runnable).run()
            promise.resolve("file:///$audioFileURL")
        } catch (e: Exception) {
            Log.e(NAME /*tag*/, "Exception: ", e)
            promise.reject("startRecord", e.message)
        }
    }

    override fun resumeRecorder(promise: Promise) {
        if (mediaRecorder == null) {
            promise.reject("resumeRecorder", "Recorder is null.")
            return
        }

        try {
            mediaRecorder!!.resume()
            totalPausedRecordTime += SystemClock.elapsedRealtime() - pausedRecordTime;
            recorderRunnable?.let { recordHandler!!.postDelayed(it, subsDurationMillis.toLong()) }
            promise.resolve("Recorder resumed.")
        } catch (e: Exception) {
            Log.e(NAME /*tag*/, "Recorder resume: " + e.message)
            promise.reject("resumeRecorder", e.message)
        }
    }

    override fun pauseRecorder(promise: Promise) {
        if (mediaRecorder == null) {
            promise.reject("pauseRecorder", "Recorder is null.")
            return
        }

        try {
            mediaRecorder!!.pause()
            pausedRecordTime = SystemClock.elapsedRealtime();
            recorderRunnable?.let { recordHandler!!.removeCallbacks(it) };
            promise.resolve("Recorder paused.")
        } catch (e: Exception) {
            Log.e(NAME /*tag*/, "pauseRecorder exception: " + e.message)
            promise.reject("pauseRecorder", e.message)
        }
    }

    override fun stopRecorder(promise: Promise) {
        if (recordHandler != null) {
            recorderRunnable?.let { recordHandler!!.removeCallbacks(it) }
        }

        if (mediaRecorder == null) {
            promise.reject("stopRecord", "recorder is null.")
            return
        }

        try {
            mediaRecorder!!.stop()
        } catch (stopException: RuntimeException) {
            stopException.message?.let { Log.d(NAME /*tag*/,"" + it) }
            promise.reject("stopRecord", stopException.message)
        }

        mediaRecorder!!.release()
        mediaRecorder = null
        promise.resolve("file:///$audioFileURL")
    }

    override fun setVolume(volume: Double, promise: Promise) {
        if (mediaPlayer == null) {
            promise.reject("setVolume", "player is null.")
            return
        }

        val mVolume = volume.toFloat()
        mediaPlayer!!.setVolume(mVolume, mVolume)
        promise.resolve("set volume")
    }


    override fun startPlayer(path: String, httpHeaders: ReadableMap?, promise: Promise) {
        if (mediaPlayer != null) {
            val isPaused = !mediaPlayer!!.isPlaying && mediaPlayer!!.currentPosition > 1

            if (isPaused) {
                mediaPlayer!!.start()
                promise.resolve("player resumed.")
                return
            }

            Log.e(NAME /*tag*/, "Player is already running. Stop it first.")
            promise.reject("startPlay", "Player is already running. Stop it first.")
            return
        } else {
            mediaPlayer = MediaPlayer()
        }

        try {
            if ((path == "DEFAULT")) {
                mediaPlayer!!.setDataSource("${reactContext.cacheDir}/$defaultFileName")
            } else {
                if (httpHeaders != null) {
                    val headers: MutableMap<String, String?> = HashMap<String, String?>()
                    val iterator = httpHeaders.keySetIterator()
                    while (iterator.hasNextKey()) {
                        val key = iterator.nextKey()
                        headers.put(key, httpHeaders.getString(key))
                    }
                    mediaPlayer!!.setDataSource(currentActivity!!.applicationContext, Uri.parse(path), headers)
                } else {
                    mediaPlayer!!.setDataSource(path)
                }
            }

            mediaPlayer!!.setOnPreparedListener { mp ->
                Log.d(NAME /*tag*/, "mediaplayer prepared and start")
                mp.start()
                /**
                 * Set timer task to send event to RN.
                 */
                mTask = object : TimerTask() {
                    override fun run() {
                        val obj = Arguments.createMap()
                        obj.putInt("duration", mp.duration)
                        obj.putInt("currentPosition", mp.currentPosition)
                        sendEvent(reactContext, "rn-playback", obj)
                    }
                }

                mTimer = Timer()
                mTimer!!.schedule(mTask, 0, subsDurationMillis.toLong())
                val resolvedPath = if (((path == "DEFAULT"))) "${reactContext.cacheDir}/$defaultFileName" else path
                promise.resolve(resolvedPath)
            }

            /**
             * Detect when finish playing.
             */
            mediaPlayer!!.setOnCompletionListener { mp ->
                /**
                 * Send last event
                 */
                val obj = Arguments.createMap()
                obj.putInt("duration", mp.duration)
                obj.putInt("currentPosition", mp.duration)
                sendEvent(reactContext, "rn-playback", obj)
                /**
                 * Reset player.
                 */
                Log.d(NAME /*tag*/, "Plays completed.")
                mTimer!!.cancel()
                mp.stop()
                mp.release()
                mediaPlayer = null
            }

            mediaPlayer!!.prepare()
        } catch (e: IOException) {
            Log.e(NAME /*tag*/, "startPlay() io exception")
            promise.reject("startPlay", e.message)
        } catch (e: NullPointerException) {
            Log.e(NAME /*tag*/, "startPlay() null exception")
        }
    }


    override fun resumePlayer(promise: Promise) {
        if (mediaPlayer == null) {
            promise.reject("resume", "mediaPlayer is null on resume.")
            return
        }

        if (mediaPlayer!!.isPlaying) {
            promise.reject("resume", "mediaPlayer is already running.")
            return
        }

        try {
            mediaPlayer!!.seekTo(mediaPlayer!!.currentPosition)
            mediaPlayer!!.start()
            promise.resolve("resume player")
        } catch (e: Exception) {
            Log.e(NAME /*tag*/, "mediaPlayer resume: " + e.message)
            promise.reject("resume", e.message)
        }
    }


    override fun pausePlayer(promise: Promise) {
        if (mediaPlayer == null) {
            promise.reject("pausePlay", "mediaPlayer is null on pause.")
            return
        }

        try {
            mediaPlayer!!.pause()
            promise.resolve("pause player")
        } catch (e: Exception) {
            Log.e(NAME /*tag*/, "pausePlay exception: " + e.message)
            promise.reject("pausePlay", e.message)
        }
    }


    override fun seekToPlayer(time: Double, promise: Promise) {
        if (mediaPlayer == null) {
            promise.reject("seekTo", "mediaPlayer is null on seek.")
            return
        }

        mediaPlayer!!.seekTo(time.toInt())
        promise.resolve("pause player")
    }

    private fun sendEvent(reactContext: ReactContext,
                          eventName: String,
                          params: WritableMap?) {
        reactContext
                .getJSModule<RCTDeviceEventEmitter>(RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
    }


    override fun stopPlayer(promise: Promise) {
        if (mTimer != null) {
            mTimer!!.cancel()
        }

        if (mediaPlayer == null) {
            promise.resolve("Already stopped player")
            return
        }

        try {
            mediaPlayer!!.release()
            mediaPlayer = null
            promise.resolve("stopped player")
        } catch (e: Exception) {
            Log.e(NAME /*tag*/, "stopPlay exception: " + e.message)
            promise.reject("stopPlay", e.message)
        }
    }


    override fun setSubscriptionDuration(sec: Double, promise: Promise) {
        subsDurationMillis = (sec * 1000).toInt()
        promise.resolve("setSubscriptionDuration: $subsDurationMillis")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        var requestRecordAudioPermission: Int = 200

        when (requestCode) {
            requestRecordAudioPermission -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) return true
        }

        return false
    }

    companion object {
        //private var tag = "RNAudioRecorderPlayer"
        const val NAME = "NativeARPModule"
        private var defaultFileName = "sound.mp4"
    }
}
