package com.example.myapplication;

import android.content.Context;
import android.media.AudioManager;


class VolumeHelper(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun raiseVolume() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun lowerVolume() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun getMaxVolume(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    fun getCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun setVolume(volumeLevel: Int) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (volumeLevel in 0..maxVolume) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                volumeLevel,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }
}
