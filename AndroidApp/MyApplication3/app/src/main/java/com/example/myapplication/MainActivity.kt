package com.example.myapplication

import android.Manifest

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.awaitAll

class MainActivity : AppCompatActivity() {
    private lateinit var volumeManager:VolumeManager;
    private lateinit var cameraManager: CameraManager;
    private lateinit var videoManager: VideoManager;

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_MEDIA_VIDEO
    )
    private val REQUEST_CODE_PERMISSIONS = 10

    private var isRecording:Boolean=false;

    //set up

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        volumeManager = VolumeManager(applicationContext)

        cameraManager = CameraManager(this, findViewById(R.id.previewView))

        videoManager = VideoManager(this, findViewById(R.id.videoView));


        if (allPermissionsGranted()) {
            cameraManager.startCamera()
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
                //startCamera()
            } else {
                // If any permission is denied, show a toast message and close the app
                Toast.makeText(this, "All permissions are required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    //Event handlers


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
            videoUri?.let { videoManager.loadAndPlayVideo(it) }
        }
    }


     fun startOrStopRecording(view: View) {
        UpdateButton();

         cameraManager.startOrStopRecording(
             onVideoSaved = { uri ->
                 if (uri == null) {
                     Toast.makeText(this, "Something went wrong while trying to record the video", Toast.LENGTH_SHORT).show()
                 }
             },
             onClassificationDone = { result ->
                 val classificationResult = result
                 // Now you can use the result
                 Log.i("Classification Result","${classificationResult}");


                 when (classificationResult) {
                     1 -> behindVideo();
                     2 -> aheadVideo();
                     3 -> pauseVideo();
                     4 -> lowerVolume();
                     5 -> raiseVolume();
                 }
             }
         )
    }

    fun UpdateButton(){
        val button: Button = findViewById(R.id.recordButton)
        if(isRecording){
            button.text = "Record";
            isRecording=false;
        } else {
            button.text = "Stop Recording";
            isRecording=true;
        }
    }



    fun onFlipCamera(view: View){
        cameraManager.FlipCamera();
    }



    //Neural Network applicable functions

    fun pauseVideo() {
        videoManager.togglePlayPause()
    }

    fun aheadVideo() {
        videoManager.seekForward()
    }

    fun behindVideo() {
        videoManager.seekBackward()
    }


    fun lowerVolume(){
        volumeManager.lowerVolume();
    }

    fun raiseVolume(){
        volumeManager.raiseVolume();
    }
}