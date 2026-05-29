package com.example.yolo26localposeanalyzer


import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.MappedByteBuffer
import kotlin.system.measureTimeMillis

class TFLiteModelAnalyzer {

    companion object {
        private const val TAG = "TFLITE_ANALYZER"
    }

    fun runFullAnalysis(context: Context, modelPath: String) {
        Log.d(TAG, "===== START ANALYSIS =====")

        val compatList = CompatibilityList()

        Log.d(TAG, "=== DEVICE INFO ===")
        Log.d(TAG, "GPU Supported: ${compatList.isDelegateSupportedOnThisDevice}")
        Log.d(TAG, "Best GPU Options: ${compatList.bestOptionsForThisDevice}")

        val configurations = listOf(
            Triple(true, false, "GPU"),
            Triple(false, true, "NNAPI"),
            Triple(false, false, "CPU")
        )

        configurations.forEach { (useGpu, useNNAPI, name) ->
            try {
                testConfiguration(context, modelPath, useGpu, useNNAPI, name)
            } catch (e: Exception) {
                Log.e(TAG, "❌ $name FAILED: ${e.message}")
                e.printStackTrace()
            }
        }

        Log.d(TAG, "===== END ANALYSIS =====")
    }

    private fun testConfiguration(
        context: Context,
        modelPath: String,
        useGpu: Boolean,
        useNNAPI: Boolean,
        label: String
    ) {
        val options = Interpreter.Options()

        if (useGpu) {
            try {
                val compatList = CompatibilityList()
                val delegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                options.addDelegate(delegate)
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ GPU init failed: ${e.message}")
                return
            }
        } else if (useNNAPI) {
            options.setUseNNAPI(true)
        } else {
            options.setNumThreads(4)
            options.setUseXNNPACK(false)
        }

        val modelBuffer = loadModelFile(context, modelPath)
        val interpreter = Interpreter(modelBuffer, options)

        Log.d(TAG, "\n=== TESTING: $label ===")

        printModelInfo(interpreter)

        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)

        val input = createBuffer(inputTensor)
        val output = createBuffer(outputTensor)

        // Warm-up
        repeat(3) {
            interpreter.run(input, output)
        }

        // Benchmark
        val iterations = 20
        val timeMs = measureTimeMillis {
            repeat(iterations) {
                interpreter.run(input, output)
            }
        }

        val avg = timeMs.toFloat() / iterations

        Log.d(TAG, "Avg inference: $avg ms")
        Log.d(TAG, "FPS: ${1000f / avg}")

        interpreter.close()
    }

    private fun printModelInfo(interpreter: Interpreter) {
        Log.d(TAG, "=== MODEL INFO ===")

        for (i in 0 until interpreter.inputTensorCount) {
            val tensor = interpreter.getInputTensor(i)
            LogTensorInfo("Input[$i]", tensor)
        }

        for (i in 0 until interpreter.outputTensorCount) {
            val tensor = interpreter.getOutputTensor(i)
            LogTensorInfo("Output[$i]", tensor)
        }
    }

    private fun LogTensorInfo(name: String, tensor: Tensor) {
        val shape = tensor.shape().contentToString()
        val type = tensor.dataType()
        val quant = tensor.quantizationParams()

        Log.d(TAG, "$name")
        Log.d(TAG, " Shape: $shape")
        Log.d(TAG, " Type: $type")
        Log.d(TAG, " Quant: scale=${quant.scale}, zeroPoint=${quant.zeroPoint}")
    }

    private fun createBuffer(tensor: Tensor): java.nio.ByteBuffer {
        val numBytes = tensor.numBytes()
        return java.nio.ByteBuffer.allocateDirect(numBytes).apply {
            order(java.nio.ByteOrder.nativeOrder())
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = fileDescriptor.createInputStream()
        val channel = inputStream.channel

        return channel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }
}