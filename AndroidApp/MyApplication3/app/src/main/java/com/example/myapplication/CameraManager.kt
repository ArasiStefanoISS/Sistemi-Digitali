package com.example.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.media.MediaMetadataRetriever
import android.widget.Toast;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.*;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.graphics.Color
import com.example.myapplication.ml.Model
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CameraManager(private val context: Context, private val previewView: PreviewView) {
    private var videoCapture:VideoCapture<Recorder>?=null;
    private var currentRecording:Recording?=null;
    private val model = Model.newInstance(context);

    private var lensFacing = CameraSelector.LENS_FACING_BACK;

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

    fun startOrStopRecording(onVideoSaved:(Uri?) ->Unit?, onClassificationDone: (Int?) -> Unit?) {

        val videoCapture = videoCapture ?: return ;

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
                            onClassificationDone(null);
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (!recordEvent.hasError()) {
                                val uri = recordEvent.outputResults.outputUri
                                onVideoSaved(uri)
                                val result=ExtractFrames(uri);

                                val byteBuffer:ByteBuffer=bitmapsToByteBuffer(result,112,112)

                                val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 30, 112, 112, 3), DataType.FLOAT32)
                                inputFeature0.loadBuffer(byteBuffer)

                                val outputs = model.process(inputFeature0)
                                val outputFeature0 = outputs.outputFeature0AsTensorBuffer

                                val modelResult: MutableList<Float> = mutableListOf(outputFeature0.getFloatValue(0),outputFeature0.getFloatValue(1),outputFeature0.getFloatValue(2),outputFeature0.getFloatValue(3),outputFeature0.getFloatValue(4))

                                Log.i("Result","Result: ${modelResult}")

                                onClassificationDone(modelResult.indexOf(modelResult.maxOrNull())+1);

                            } else {
                                Toast.makeText(context, "Error saving video: ${recordEvent.error}", Toast.LENGTH_SHORT).show()
                                onClassificationDone(null);
                            }
                            currentRecording = null
                        }
                    }
                }
        }
    }



    fun FlipCamera(){
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }

            startCamera()

    }

    fun ExtractFrames(uri: Uri): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frameDiffs = mutableListOf<Pair<Bitmap, Double>>()  // Pair of resized frame and its motion score
        val targetWidth = 112
        val targetHeight = 112

        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return emptyList()

            val totalFrames = 100
            val intervalMs = durationMs / totalFrames

            var lastFrame: Bitmap? = null

            for (i in 0 until totalFrames) {
                val timeUs = i * intervalMs * 1000
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue

                // Resize the frame to 112x112
                val resizedFrame = Bitmap.createScaledBitmap(frame, targetWidth, targetHeight, true)

                val diffScore = if (lastFrame != null) {
                    calculateFrameDifference(lastFrame!!, resizedFrame)
                } else {
                    0.0
                }

                frameDiffs.add(Pair(resizedFrame, diffScore))
                lastFrame = resizedFrame
            }

        } catch (e: Exception) {
            Log.e("MotionDetect", "Error processing video", e)
        } finally {
            retriever.release()
        }

        return frameDiffs
            .sortedByDescending { it.second }  // sort by motion score
            .take(30)                          // take top 30
            .map { it.first }                  // return bitmaps only
    }


    fun calculateFrameDifference(b1: Bitmap, b2: Bitmap): Double {
        val scaledB1 = Bitmap.createScaledBitmap(b1, 64, 64, true)
        val scaledB2 = Bitmap.createScaledBitmap(b2, 64, 64, true)

        var diff = 0L

        for (x in 0 until scaledB1.width) {
            for (y in 0 until scaledB1.height) {
                val pixel1 = scaledB1.getPixel(x, y)
                val pixel2 = scaledB2.getPixel(x, y)

                val r1 = (pixel1 shr 16) and 0xFF
                val g1 = (pixel1 shr 8) and 0xFF
                val b1c = pixel1 and 0xFF

                val r2 = (pixel2 shr 16) and 0xFF
                val g2 = (pixel2 shr 8) and 0xFF
                val b2c = pixel2 and 0xFF

                diff += kotlin.math.abs(r1 - r2)
                diff += kotlin.math.abs(g1 - g2)
                diff += kotlin.math.abs(b1c - b2c)
            }
        }

        return diff.toDouble() / (scaledB1.width * scaledB1.height)
    }


    fun bitmapsToByteBuffer(
        bitmaps: List<Bitmap>,
        modelInputWidth: Int,
        modelInputHeight: Int,
        isQuantized: Boolean = false  // true if your model uses uint8 instead of float32
    ): ByteBuffer {

        val inputSize = modelInputWidth * modelInputHeight * 3  // Assuming RGB
        val bytesPerChannel = if (isQuantized) 1 else 4
        val buffer = ByteBuffer.allocateDirect(bitmaps.size * inputSize * bytesPerChannel)
        buffer.order(ByteOrder.nativeOrder())

        for (bitmap in bitmaps) {
            val resized = Bitmap.createScaledBitmap(bitmap, modelInputWidth, modelInputHeight, true)
            for (y in 0 until modelInputHeight) {
                for (x in 0 until modelInputWidth) {
                    val pixel = resized.getPixel(x, y)

                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    if (isQuantized) {
                        // uint8 values
                        buffer.put(r.toByte())
                        buffer.put(g.toByte())
                        buffer.put(b.toByte())
                    } else {
                        // float32 values normalized to [0, 1]
                        buffer.putFloat(r / 255.0f)
                        buffer.putFloat(g / 255.0f)
                        buffer.putFloat(b / 255.0f)
                    }
                }
            }
        }

        buffer.rewind()
        return buffer
    }
}
