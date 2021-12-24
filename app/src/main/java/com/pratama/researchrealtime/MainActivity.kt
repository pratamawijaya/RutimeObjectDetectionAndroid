package com.pratama.researchrealtime

import android.Manifest
import android.content.res.AssetFileDescriptor
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    lateinit var cameraExecutor: ExecutorService

    lateinit var cameraView: PreviewView
    lateinit var resultView: SurfaceView

    companion object {

        const val RC_CAMERA = 1

        private const val MODEL_FILE_NAME = "model_ssd_mobilenet.tflite"
        private const val LABEL_FILE_NAME = "coco_dataset.txt"
    }

    //Interpreter with wrappers for working with tflite models
    private val interpreter: Interpreter by lazy {
        Interpreter(loadModel())
    }

    //Model correct label list
    private val labels: List<String> by lazy {
        loadLabels()
    }

    //Converter to convert YUV image of camera to RGB
    private val yuvToRgbConverter: YuvToRgbConverter by lazy {
        YuvToRgbConverter(this)
    }

    private lateinit var overlaySurfaceView: OverlaySurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraView = findViewById(R.id.cameraView)
        resultView = findViewById(R.id.resultView)
        overlaySurfaceView = OverlaySurfaceView(resultView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupCameraWithPermissionCheck()
    }

    //Get model correct label data from assets
    private fun loadLabels(fileName: String = MainActivity.LABEL_FILE_NAME): List<String> {
        var labels = listOf<String>()
        var inputStream: InputStream? = null
        try {
            inputStream = assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            labels = reader.readLines()
        } catch (e: Exception) {
            Toast.makeText(this, "txt file read error", Toast.LENGTH_SHORT).show()
            finish()
        } finally {
            inputStream?.close()
        }
        return labels
    }

    //Load tflite model from assets
    private fun loadModel(fileName: String = MainActivity.MODEL_FILE_NAME): ByteBuffer {
        lateinit var modelBuffer: ByteBuffer
        var file: AssetFileDescriptor? = null
        try {
            file = assets.openFd(fileName)
            val inputStream = FileInputStream(file.fileDescriptor)
            val fileChannel = inputStream.channel
            modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                file.startOffset,
                file.declaredLength
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Model file read error", Toast.LENGTH_SHORT).show()
            finish()
        } finally {
            file?.close()
        }
        return modelBuffer
    }

    @AfterPermissionGranted(RC_CAMERA)
    private fun setupCameraWithPermissionCheck() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)) {
            setupCamera()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "butuh akses kamera",
                RC_CAMERA,
                Manifest.permission.CAMERA
            )
        }
    }


    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        if (requestCode == RC_CAMERA) {
            setupCamera()
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }


    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            //Preview use case
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(cameraView.surfaceProvider) }

            //Use rear camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            //Image analysis(This time object detection)Use case
            val imageAnalyzer = ImageAnalysis.Builder()
//                .setTargetRotation(cameraView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Show only the preview image of the latest camera
                .build()
                .also {
                    it.setAnalyzer(
                        cameraExecutor, ObjectDetector(
                            yuvToRgbConverter = yuvToRgbConverter,
                            interpreter,
                            labels,
                            Size(resultView.width, resultView.height)
                        ) { detectedObjectList ->
                            overlaySurfaceView.draw(detectedObjectList)
                        }
                    )
                }


            try {
                cameraProvider.unbindAll()

                //Bind each use case to cameraX
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            } catch (exc: Exception) {
                Log.e("ERROR: Camera", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}