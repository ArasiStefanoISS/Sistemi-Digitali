package com.example.myapplication

import android.Manifest

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.camera.video.MediaStoreOutputOptions
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView

class MainActivity : AppCompatActivity() {
    private lateinit var volumeHelper:VolumeHelper;
    private lateinit var videoView:VideoView;

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_MEDIA_VIDEO
    )
    private val REQUEST_CODE_PERMISSIONS = 10


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        volumeHelper = VolumeHelper(applicationContext)

        videoView=findViewById<VideoView>(R.id.videoView)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Both permissions granted: Start Camera & Play Video
                startCamera()
            } else {
                // If any permission is denied, show a toast message and close the app
                Toast.makeText(this, "All permissions are required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }



    private val PICK_VIDEO_REQUEST = 1

    fun onPickVideo(view: View) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "video/*"
        startActivityForResult(intent, PICK_VIDEO_REQUEST)
    }





    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_VIDEO_REQUEST && resultCode == RESULT_OK && data != null) {
            val videoUri: Uri? = data.data
            if (videoUri != null) {
               // val videoView = findViewById<VideoView>(R.id.videoView)

                val mediaController = MediaController(this)
                mediaController.setAnchorView(videoView)
                videoView.setMediaController(mediaController)

                videoView.setVideoURI(videoUri)
                videoView.start()
            }
        }
    }



    fun pauseVideo(){
        if(videoView.isPlaying){
            videoView.pause()
        } else {
            videoView.start()
        }

    }


    fun aheadVideo(){
        val newTime = videoView.currentPosition + 10_000
        if (newTime < videoView.duration) {
            videoView.seekTo(newTime)
        } else {
            videoView.seekTo(videoView.duration)
        }
    }

    fun behindVideo(){
        val newTime = videoView.currentPosition - 10_000
        if (newTime > 0) {
            videoView.seekTo(newTime)
        } else {
            videoView.seekTo(0)
        }
    }



    fun lowerVolume(){
        volumeHelper.lowerVolume();
    }

    fun raiseVolume(){
        volumeHelper.raiseVolume();
    }




    private var videoCapture: VideoCapture<Recorder>? = null

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<PreviewView>(R.id.previewView).surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private var currentRecording: Recording? = null

    fun startOrStopRecording(view: View) {
        val videoCapture = videoCapture ?: return

        if (currentRecording != null) {
            currentRecording?.stop()
            currentRecording = null
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
        } else {
            val name = "video_${System.currentTimeMillis()}.mp4"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                // Save to Movies/MyAppVideos
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/MyAppVideos")
                }
            }

            val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(contentValues)
                .build()

            currentRecording = videoCapture.output
                .prepareRecording(this, mediaStoreOutputOptions)
                .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (!recordEvent.hasError()) {
                                Toast.makeText(this, "Video saved to ${recordEvent.outputResults.outputUri}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "Error saving video: ${recordEvent.error}", Toast.LENGTH_SHORT).show()
                            }
                            currentRecording = null
                        }
                    }
                }
        }
    }


}