package com.example.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

enum class AudioDevice {
    EARPIECE, SPEAKER, BLUETOOTH
}

class AudioController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isMuted = false
    private var currentDevice: AudioDevice = AudioDevice.EARPIECE

    private var audioFocusRequest: AudioFocusRequest? = null
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d("AudioController", "Audio focus changed: $focusChange")
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED)
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    currentDevice = AudioDevice.BLUETOOTH
                } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                    currentDevice = AudioDevice.EARPIECE // Fallback
                    setAudioDevice(currentDevice)
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun setMode(mode: Int) {
        audioManager.mode = mode
    }

    private fun requestAudioFocus() {
        Log.d("AudioController", "Requesting audio focus (AUDIOFOCUS_GAIN_TRANSIENT)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
                
            audioFocusRequest = focusRequest
            val result = audioManager.requestAudioFocus(focusRequest)
            Log.d("AudioController", "Audio focus request result (O+): $result")
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            Log.d("AudioController", "Audio focus request result (Legacy): $result")
        }
    }

    private fun abandonAudioFocus() {
        Log.d("AudioController", "Abandoning audio focus")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                val result = audioManager.abandonAudioFocusRequest(it)
                Log.d("AudioController", "Audio focus abandon result (O+): $result")
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.abandonAudioFocus(focusChangeListener)
            Log.d("AudioController", "Audio focus abandon result (Legacy): $result")
        }
    }

    fun startCall() {
        requestAudioFocus()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = isMuted
    }

    fun stopCall() {
        abandonAudioFocus()
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isMicrophoneMute = false
        audioManager.isSpeakerphoneOn = false
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
    }

    fun setAudioDevice(device: AudioDevice) {
        currentDevice = device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyModernCommunicationDevice(device)
        } else {
            applyLegacyAudioDevice(device)
        }
    }

    /**
     * Android 12+ (API 31+: el enrutado de comunicación exige `setCommunicationDevice`.
     * Los métodos legacy (`isSpeakerphoneOn`, `startBluetoothSco` no cambian la ruta del stream
     * de comunicación en estas versiones y el altavoz/Bluetooth seguirían por el auricular..
     */
    private fun applyModernCommunicationDevice(device: AudioDevice) {
        val commDevices = audioManager.availableCommunicationDevices
        when (device) {
            AudioDevice.EARPIECE -> {
                val earpiece = commDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (earpiece != null) {
                    audioManager.setCommunicationDevice(earpiece)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            }
            AudioDevice.SPEAKER -> {
                val speaker = commDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
            }
            AudioDevice.BLUETOOTH -> {
                val scoDevice = commDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                if (scoDevice != null) {
                    audioManager.setCommunicationDevice(scoDevice)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyAudioDevice(device: AudioDevice) {
        when (device) {
            AudioDevice.EARPIECE -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            AudioDevice.SPEAKER -> {
                audioManager.isSpeakerphoneOn = true
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            AudioDevice.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
        }
    }

    fun toggleMute(mute: Boolean) {
        isMuted = mute
        audioManager.isMicrophoneMute = isMuted
    }
    
    fun release() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.e("AudioController", "Error unregistering receiver", e)
        }
        stopCall()
    }
}
