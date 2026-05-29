package com.example.yolo26localposeanalyzer.data.repository

// data/repository/ObjectDetectionRepository.kt

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.example.yolo26localposeanalyzer.data.datasource.LocalModelDataSource
import com.example.yolo26localposeanalyzer.domain.model.DetectedObject
import com.example.yolo26localposeanalyzer.utils.Constants
import com.example.yolo26localposeanalyzer.utils.Constants.MODEL_INPUT_SIZE
import java.nio.ByteBuffer
import androidx.core.graphics.scale
import com.example.yolo26localposeanalyzer.domain.model.LetterboxResult
import com.example.yolo26localposeanalyzer.utils.YOLOPostprocessor
import java.nio.ByteOrder
import kotlin.math.min
import androidx.core.graphics.createBitmap
import com.example.yolo26localposeanalyzer.domain.model.DetectedPose
import org.tensorflow.lite.Tensor

class ObjectDetectionRepository(
    private val modelDataSource: LocalModelDataSource
) {

    init {
        //modelDataSource.loadModel(Constants.MODEL_PATH_26N_POSE_16)
        //modelDataSource.loadModel(Constants.MODEL_PATH_26N_POSE_32)
        //modelDataSource.loadModel(Constants.MODEL_PATH_26N_POSE_INT8)
        modelDataSource.loadModel(Constants.MODEL_PATH_26N_POSE_INTEGER_QUANT)
        //modelDataSource.loadModel(Constants.MODEL_PATH_26N_POSE_FULL_INTEGER_QUANT)
    }

    /**
     * Initialize model
     */
    fun initialize() {
         //modelDataSource.loadModel(Constants.MODEL_PATH)
    }

    /**
     * Detect objects in bitmap
     */
    suspend fun detectObjects(bitmap: Bitmap): List<DetectedObject> {
        val inputTensor = modelDataSource.getInputTensor()
        val outputTensor = modelDataSource.getOutputTensor()
        val dataType = inputTensor.dataType()

        bitmap
        return try {
            // Preprocess image
            val result = prepareInput(
                bitmap,
                inputTensor
                )

            //make the input buffer dynamic
            /* modelDataSource.getInputShape()?.get(1) -> input tensor
            * send input tensor to createBuffer()
            * same for output buffer.
            * try to print bitmap shape/size/details/memory size
            * compare with input buffer. if it fits. how much info/data is lost
            * */
            val inputBuffer = result.first

            Log.d(Constants.TFModelDebugTag, "ObjectDetectionRepository:detectObjects: $inputBuffer.")

            // Prepare output buffer
            // Output shape: [1, 300, 6]
            /*
            * don't hard code 300
            * make it dynamic
            * get shape from the model itself
            * modelDataSource.getOutputShape()*/

            val outputBuffer = Array(1) {
                Array(300) {
                    FloatArray(6)
                }
            }

            // Run inference
            modelDataSource.runInference(inputBuffer, outputBuffer)

            //return statement
            // Postprocess results
            YOLOPostprocessor.parseOutputShape300x6(
                outputBuffer,
                result.second
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun detectPose(bitmap: Bitmap): List<DetectedPose> {

        val inputTensor = modelDataSource.getInputTensor()
        val outputTensor = modelDataSource.getOutputTensor()
        val dataType = inputTensor.dataType()

        bitmap
        return try {
            // Preprocess image
            val result = prepareInput(bitmap, inputTensor)

            //make the input buffer dynamic
            /* modelDataSource.getInputShape()?.get(1) -> input tensor
            * send input tensor to createBuffer()
            * same for output buffer.
            * try to print bitmap shape/size/details/memory size
            * compare with input buffer. if it fits. how much info/data is lost
            * */
            val inputBuffer = result.first

            // Prepare output buffer
            // Output shape: [1, 300, 57]
            /*
            * don't hard code 300
            * make it dynamic
            * get shape from the model itself
            * modelDataSource.getOutputShape()*/

            val outputBuffer = Array(1) {
                Array(300) {
                    FloatArray(57)
                }
            }


            // Run inference
            modelDataSource.runInference(inputBuffer, outputBuffer)

            //return statement
            // Postprocess results
            YOLOPostprocessor.parseOutputShape300x57(
                outputBuffer,
                result.second
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            bitmap.recycle()
        }
    }


    fun prepareInput(bitmap: Bitmap,inputTensor: Tensor): Pair<ByteBuffer, LetterboxResult> {
        //val resizedBitmap = bitmap.scale(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        val inputSize = inputTensor.shape()[1]
        val result = letterbox(bitmap, inputSize)
        // Create ByteBuffer for input
        val byteBuffer = createBuffer(inputTensor)

        // Convert bitmap to normalized float values (0-1 range)
        /*val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        result.bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0,
            MODEL_INPUT_SIZE, MODEL_INPUT_SIZE
        )*/
        val pixels = IntArray(inputSize * inputSize)
        result.bitmap.getPixels(pixels, 0, inputSize, 0, 0,
            inputSize, inputSize
        )

        for (pixel in pixels) {
            val r = ((pixel shr 16 and 0xFF) / 255.0f)
            val g = ((pixel shr 8 and 0xFF) / 255.0f)
            val b = ((pixel and 0xFF) / 255.0f)

            // YOLOv8 expects RGB format, normalized to [0,1]
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        //resizedBitmap.recycle()
        return Pair(byteBuffer, result)
    }

    private fun createBuffer(tensor: Tensor): java.nio.ByteBuffer {
        val numBytes = tensor.numBytes()
        return java.nio.ByteBuffer.allocateDirect(numBytes).apply {
            order(java.nio.ByteOrder.nativeOrder())
        }
    }

    fun letterbox(bitmap: Bitmap, size: Int = 640): LetterboxResult {
        val width = bitmap.width
        val height = bitmap.height

        val scale = min(size / width.toFloat(), size / height.toFloat())

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        val resized = bitmap.scale(newWidth, newHeight)

        val output = createBitmap(size, size)
        val canvas = Canvas(output)

        val padX = (size - newWidth) / 2f
        val padY = (size - newHeight) / 2f

        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(resized, padX, padY, null)

        return LetterboxResult(output, scale, padX, padY)
    }

    /**
     * Clean up resources
     */
    fun close() {
        modelDataSource.close()
    }
}