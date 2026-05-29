package com.example.yolo26localposeanalyzer

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yolo26localposeanalyzer.utils.Constants

import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class ModelAnalysisTestRunner {

    @Test
    fun analyzeModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val analyzer = TFLiteModelAnalyzer()

        analyzer.runFullAnalysis(
            context,
            Constants.MODEL_PATH_26N_POSE_32
        )
    }
}