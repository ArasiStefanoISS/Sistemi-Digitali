package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button
import android.widget.Toast;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.*;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

class CameraManager(private val context: Context, private val previewView: PreviewView) {
    private var videoCapture:VideoCapture<Recorder>?=null;
    private var currentRecording:Recording?=null;

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also{
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    context as androidx.lifecycle.LifecycleOwner, cameraSelector, preview, videoCapture
                )
            } catch (exc:Exception){
                Log.e("VideoManager", "Use case binding failed", exc)
            }
        },ContextCompat.getMainExecutor(context))
    }

    fun startOrStopRecording(onVideoSaved:(Uri?) ->Unit)
    {
        val videoCapture = videoCapture ?:return

        if (currentRecording != null) {
            currentRecording ?.stop()
            currentRecording = null
            Toast.makeText(context, "Recording stopped", Toast.LENGTH_SHORT).show()
        } else {
            val name = "video_${System.currentTimeMillis()}.mp4"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/MyAppVideos")
                }
            }

            val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(contentValues)
                .build()

            currentRecording = videoCapture.output
                .prepareRecording(context as androidx.activity.ComponentActivity, mediaStoreOutputOptions)
                .start(ContextCompat.getMainExecutor(context)) {
                        recordEvent ->
                    when(recordEvent) {
                        is VideoRecordEvent.Start -> {
                            Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (!recordEvent.hasError()) {
                                val uri = recordEvent.outputResults.outputUri
                                Toast.makeText(context, "Video saved to $uri", Toast.LENGTH_LONG).show()
                                onVideoSaved(uri)
                            } else {
                                Toast.makeText(context, "Error saving video: ${recordEvent.error}", Toast.LENGTH_SHORT).show()
                            }
                            currentRecording = null
                        }
                    }
                }
        }
    }



    private var lensFacing = CameraSelector.LENS_FACING_BACK

    fun FlipCamera(){
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }

            // Rebind all use cases with the new lensFacing
            startCamera()

    }
}