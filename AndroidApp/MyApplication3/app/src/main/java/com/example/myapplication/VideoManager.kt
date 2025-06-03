package com.example.myapplication

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.View
import android.widget.MediaController
import android.widget.VideoView

class VideoManager (private val context: Context, private val videoView: VideoView, private val maxHeightPx:Int =1400) {



    /*fun loadAndPlayVideo(uri: Uri) {
        val mediaController = MediaController(context)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp: MediaPlayer ->
            val videoWidth = mp.videoWidth
            val videoHeight = mp.videoHeight

            if (videoWidth == 0 || videoHeight == 0) {
                videoView.start()
                return@setOnPreparedListener
            }

            val parent = videoView.parent as View
            val parentWidth = parent.width

            val aspectRatio = videoHeight.toFloat() / videoWidth.toFloat()
            var calculatedHeight = (parentWidth * aspectRatio).toInt()

            // Cap height
            if (calculatedHeight > maxHeightPx) {
                calculatedHeight = maxHeightPx
            }

            val layoutParams = videoView.layoutParams
            layoutParams.width = parentWidth
            layoutParams.height = calculatedHeight
            videoView.layoutParams = layoutParams

            videoView.start()
        }
    }*/


    fun loadAndPlayVideo(uri: Uri) {
        val mediaController = MediaController(context)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp: MediaPlayer ->

            val videoWidth = mp.videoWidth
            val videoHeight = mp.videoHeight

            if (videoWidth == 0 || videoHeight == 0) {
                videoView.start()
                return@setOnPreparedListener
            }

            val parent = videoView.parent as View
            val parentWidth = parent.width

            val aspectRatio = videoHeight.toFloat() / videoWidth.toFloat()
            var calculatedHeight = (parentWidth * aspectRatio).toInt()

            // Calculate 40% of screen height in pixels
            val displayMetrics = context.resources.displayMetrics
            val maxHeightPx = (displayMetrics.heightPixels * 0.4).toInt()

            // Cap height to 40% of screen height
            if (calculatedHeight > maxHeightPx) {
                calculatedHeight = maxHeightPx
            }

            val layoutParams = videoView.layoutParams
            layoutParams.width = parentWidth
            layoutParams.height = calculatedHeight
            videoView.layoutParams = layoutParams

            videoView.start()
        }
    }



    fun togglePlayPause() {
        if (videoView.isPlaying) {
            videoView.pause()
        } else {
            videoView.start()
        }
    }

    fun seekForward(ms: Int = 10_000) {
        val newTime = videoView.currentPosition + ms
        videoView.seekTo(newTime.coerceAtMost(videoView.duration))
    }

    fun seekBackward(ms: Int = 10_000) {
        val newTime = videoView.currentPosition - ms
        videoView.seekTo(newTime.coerceAtLeast(0))
    }
}