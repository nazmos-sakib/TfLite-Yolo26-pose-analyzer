package com.example.yolo26localposeanalyzer.domain.model

import android.graphics.RectF

data class DetectedPose(
    val boundingBox: RectF,
    val label: String,
    val confidence: Float,
    val classId: Int,
    val keyPoints: List<Keypoint>
){
    override fun toString(): String {
        return "DetectedObject(" +
                "label='$label', " +
                "confidence=${"%.2f".format(confidence)}, " +
                "classId=$classId, " +
                "boundingBox=[left=${boundingBox.left}, top=${boundingBox.top}, right=${boundingBox.right}, bottom=${boundingBox.bottom}], " +
                "keypoints:" + keyPoints.toString() +
                ")"

    }
}
