package com.ormo.manager.domain

import kotlin.math.min

/** 고를 수 있는 사진 크기(앨범). 올리기 전에 [PHOTO_OUTPUT_SIZE]로 줄인다. */
const val MAX_PHOTO_BYTES = 10L * 1024 * 1024
const val PHOTO_OUTPUT_SIZE = 1080
const val MAX_PHOTO_ZOOM = 4f

private val SUPPORTED_TYPES = setOf("image/jpeg", "image/png", "image/heic", "image/heif")

enum class PhotoProblem { TOO_BIG, UNSUPPORTED, UNREADABLE }

/** 앨범에서 고른 파일을 쓸 수 있는지. 크기를 알 수 없으면 크기는 따지지 않는다. */
fun checkPhoto(mimeType: String?, sizeBytes: Long?): PhotoProblem? = when {
    mimeType?.lowercase() !in SUPPORTED_TYPES -> PhotoProblem.UNSUPPORTED
    sizeBytes != null && sizeBytes > MAX_PHOTO_BYTES -> PhotoProblem.TOO_BIG
    else -> null
}

/**
 * 원본([width]x[height]) 위에서 잘라낼 정사각형. 좌표는 원본 픽셀이다.
 * 확대·이동해도 사각형은 원본 밖으로 나가지 않는다.
 */
data class CropWindow(
    val width: Int,
    val height: Int,
    val zoom: Float = 1f,
    val centerX: Float = width / 2f,
    val centerY: Float = height / 2f,
) {
    val side: Float get() = min(width, height) / zoom
    val left: Float get() = centerX - side / 2
    val top: Float get() = centerY - side / 2

    fun pan(dx: Float, dy: Float): CropWindow = copy(centerX = centerX + dx, centerY = centerY + dy).inside()

    fun zoomBy(factor: Float): CropWindow = copy(zoom = (zoom * factor).coerceIn(1f, MAX_PHOTO_ZOOM)).inside()

    private fun inside(): CropWindow {
        val half = side / 2
        return copy(
            centerX = centerX.coerceIn(half, width - half),
            centerY = centerY.coerceIn(half, height - half),
        )
    }
}
