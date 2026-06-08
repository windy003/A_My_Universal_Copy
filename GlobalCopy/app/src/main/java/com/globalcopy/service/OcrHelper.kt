package com.globalcopy.service

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.globalcopy.service.GlobalCopyAccessibilityService.TextNodeInfo

/**
 * 基于 ML Kit 的离线 OCR 工具。
 * 中文识别模型同时支持拉丁/英文字符，足以覆盖中英文混排场景。
 */
object OcrHelper {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 对截屏 Bitmap 进行文字识别。
     * 截屏分辨率与无障碍节点使用的屏幕像素坐标一致，
     * 因此识别框 boundingBox 可直接作为屏幕坐标使用。
     *
     * @param bitmap   全屏截图
     * @param onResult 识别成功，返回按行拆分的文本块（文本 + 屏幕坐标）
     * @param onError  识别失败
     */
    fun recognize(
        bitmap: Bitmap,
        onResult: (List<TextNodeInfo>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val nodes = mutableListOf<TextNodeInfo>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val box: Rect = line.boundingBox ?: continue
                        val text = line.text
                        if (text.isNotBlank() && box.width() > 1 && box.height() > 1) {
                            nodes.add(TextNodeInfo(text, box))
                        }
                    }
                }
                onResult(nodes)
            }
            .addOnFailureListener { e -> onError(e) }
    }
}
