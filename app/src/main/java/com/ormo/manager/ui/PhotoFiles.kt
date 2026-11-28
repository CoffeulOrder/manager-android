package com.ormo.manager.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.ormo.manager.domain.CropWindow
import com.ormo.manager.domain.PHOTO_OUTPUT_SIZE
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 자르기 화면에 올릴 사진의 긴 쪽 상한. 이보다 크면 줄여서 읽는다. */
private const val DECODE_LIMIT = PHOTO_OUTPUT_SIZE * 2

/** 파일 크기(바이트). 알 수 없으면 null */
fun photoSize(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
    }
}.getOrNull()

/** 카메라가 사진을 써 넣을 임시 파일. 앱 밖에는 이 파일만 잠깐 연다. */
fun cameraOutputUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera/capture.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

/** 사진을 읽어 촬영 방향대로 세운다. 읽지 못하면 null */
fun decodePhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    fun open() = checkNotNull(context.contentResolver.openInputStream(uri))

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > DECODE_LIMIT) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = checkNotNull(open().use { BitmapFactory.decodeStream(it, null, options) })

    val degrees = runCatching {
        open().use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrDefault(0f)
    if (degrees == 0f) {
        bitmap
    } else {
        val rotate = Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotate, true)
    }
}.getOrNull()

/** 고른 정사각형만 잘라 긴 쪽 1080 이하의 JPEG로 만든다. */
fun cropToJpeg(source: Bitmap, window: CropWindow): ByteArray {
    val side = window.side.roundToInt().coerceIn(1, min(source.width, source.height))
    val left = window.left.roundToInt().coerceIn(0, source.width - side)
    val top = window.top.roundToInt().coerceIn(0, source.height - side)
    val cropped = Bitmap.createBitmap(source, left, top, side, side)
    val size = min(side, PHOTO_OUTPUT_SIZE)
    val scaled = if (size == side) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
    return ByteArrayOutputStream().use {
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, it)
        it.toByteArray()
    }
}
