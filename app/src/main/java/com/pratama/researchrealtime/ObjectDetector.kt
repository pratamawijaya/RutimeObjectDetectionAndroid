package com.pratama.researchrealtime

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.Image
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op

typealias ObjectDetectorCallback = (image: List<DetectionObject>) -> Unit

/**
 *CameraX Object Detection Image Analysis Use Case
 * @param yuvToRgbConverter Image buffer YUV for camera image_420_Convert from 888 to RGB format
 * @param interpreter tflite Library for manipulating models
 * @param labels List of correct labels
 * @param resultViewSize The size of the surfaceView that displays the result
 * @Receive a list of parsing results in the param listener callback
 */

class ObjectDetector(
    private val yuvToRgbConverter: YuvToRgbConverter,
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val resultViewSize: Size,
    private val listener: ObjectDetectorCallback
) : ImageAnalysis.Analyzer {

    companion object {
        //Model input and output sizes
        private const val IMG_SIZE_X = 300
        private const val IMG_SIZE_Y = 300
        private const val MAX_DETECTION_NUM = 10

        //Since the tflite model used this time has been quantized, normalize related is 127.Not 5f but as follows
        private const val NORMALIZE_MEAN = 0f
        private const val NORMALIZE_STD = 1f

        //Detection result score threshold
        private const val SCORE_THRESHOLD = 0.5f
    }

    private var imageRotationDegrees: Int = 0

    private val tfImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(ResizeOp(IMG_SIZE_X, IMG_SIZE_Y, ResizeOp.ResizeMethod.BILINEAR))
            .add(Rot90Op(-imageRotationDegrees / 90))
            .add(NormalizeOp(NORMALIZE_MEAN, NORMALIZE_STD))
            .build()
    }


    private val tfImageBuffer = TensorImage(DataType.UINT8)

    //Bounding box of detection results[1:10:4]
    //The bounding box[top, left, bottom, right]Form of
    private val outputBoundingBoxes: Array<Array<FloatArray>> = arrayOf(
        Array(MAX_DETECTION_NUM) {
            FloatArray(4)
        }
    )

    //Discovery result class label index[1:10]
    private val outputLabels: Array<FloatArray> = arrayOf(
        FloatArray(MAX_DETECTION_NUM)
    )

    //Each score of the detection result[1:10]
    private val outputScores: Array<FloatArray> = arrayOf(
        FloatArray(MAX_DETECTION_NUM)
    )

    //Number of detected objects(This time it is set at the time of tflite conversion, so 10(Constant))
    private val outputDetectionNum: FloatArray = FloatArray(1)

    //Put together in a map to receive detection results
    private val outputMap = mapOf(
        0 to outputBoundingBoxes,
        1 to outputLabels,
        2 to outputScores,
        3 to outputDetectionNum
    )


    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        //Implementation of TODO inference code
        if (image.image == null) return

        imageRotationDegrees = image.imageInfo.rotationDegrees
        val detctedObjectList = detect(image.image!!)
        listener(detctedObjectList)
        image.close()

    }

    private fun detect(targetImage: Image): List<DetectionObject> {
        val targetBitmap =
            Bitmap.createBitmap(targetImage.width, targetImage.height, Bitmap.Config.ARGB_8888)
        yuvToRgbConverter.yuvToRgb(targetImage, targetBitmap) //Convert to rgb
        tfImageBuffer.load(targetBitmap)
        val tensorImage = tfImageProcessor.process(tfImageBuffer)


        //Performing inference with the tflite model
        interpreter.runForMultipleInputsOutputs(arrayOf(tensorImage.buffer), outputMap)

        //Format the inference result and return it as a list
        val detectedObjectList = arrayListOf<DetectionObject>()

        for (i in 0 until outputDetectionNum[0].toInt()) {
            val score = outputScores[0][i]
            val label = labels[outputLabels[0][i].toInt()]
            val boundingBox = RectF(
                outputBoundingBoxes[0][i][1] * resultViewSize.width,
                outputBoundingBoxes[0][i][0] * resultViewSize.height,
                outputBoundingBoxes[0][i][3] * resultViewSize.width,
                outputBoundingBoxes[0][i][2] * resultViewSize.height
            )

            //Add only those larger than the threshold
            if (score >= ObjectDetector.SCORE_THRESHOLD) {
                detectedObjectList.add(
                    DetectionObject(
                        score = score,
                        label = label,
                        boundingBox = boundingBox
                    )
                )
            } else {
                //The detection results are sorted in descending order of score, so if the score falls below the threshold, the loop ends.
                break
            }
        }
        return detectedObjectList.take(4)
    }
}


/**
 *Class to put the detection result
 */
data class DetectionObject(
    val score: Float,
    val label: String,
    val boundingBox: RectF
)