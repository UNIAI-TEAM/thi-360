package vn.unicom.thi360.feature.account

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AvatarImagePreparer {
    suspend fun prepare(uri: Uri): AvatarPrepareResult
}

class AndroidAvatarImagePreparer(
    private val contentResolver: ContentResolver,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : AvatarImagePreparer {

    override suspend fun prepare(uri: Uri): AvatarPrepareResult = withContext(Dispatchers.IO) {
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && sdkInt >= Build.VERSION_CODES.P) {
                decodeWithImageDecoder(uri)
            } else {
                decodeWithBitmapFactory(uri)
            }
        } catch (_: Throwable) {
            null
        } ?: return@withContext AvatarPrepareResult.Unreadable

        try {
            val encoded = encodeWithinLimits(bitmap)
            if (encoded == null) {
                AvatarPrepareResult.Unreadable
            } else {
                AvatarPrepareResult.Prepared(
                    PreparedAvatar(
                        bytes = encoded,
                        mimeType = "image/jpeg",
                        fileName = "avatar.jpg",
                    ),
                )
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val srcWidth = info.size.width
            val srcHeight = info.size.height
            val longest = max(srcWidth, srcHeight)
            if (longest > MAX_DIMENSION_PX && srcWidth > 0 && srcHeight > 0) {
                val scale = MAX_DIMENSION_PX.toDouble() / longest.toDouble()
                val targetW = (srcWidth * scale).roundToInt().coerceIn(1, MAX_DIMENSION_PX)
                val targetH = (srcHeight * scale).roundToInt().coerceIn(1, MAX_DIMENSION_PX)
                decoder.setTargetSize(targetW, targetH)
            }
        }
    }

    @SuppressLint("ExifInterface")
    private fun decodeWithBitmapFactory(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        val orientation = contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                android.media.ExifInterface(stream).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        } ?: android.media.ExifInterface.ORIENTATION_NORMAL

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(srcWidth, srcHeight, MAX_DIMENSION_PX)
        }
        val decoded = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val oriented = applyExifOrientation(decoded, orientation)
        if (oriented !== decoded) {
            decoded.recycle()
        }

        val scaled = scaleDownIfNeeded(oriented, MAX_DIMENSION_PX)
        if (scaled !== oriented) {
            oriented.recycle()
        }
        return scaled
    }

    private fun encodeWithinLimits(source: Bitmap): ByteArray? {
        val current = scaleDownIfNeeded(source, MAX_DIMENSION_PX)
        val ownsCurrent = current !== source
        var working = current
        var ownsWorking = ownsCurrent
        try {
            repeat(MAX_DOWNSCALE_STEPS) {
                var quality = INITIAL_JPEG_QUALITY
                while (quality >= MIN_JPEG_QUALITY) {
                    val bytes = compressJpeg(working, quality)
                    if (bytes.size <= MAX_BYTES) {
                        return bytes
                    }
                    quality -= QUALITY_STEP
                }
                val nextW = (working.width * DOWNSCALE_FACTOR).roundToInt().coerceAtLeast(1)
                val nextH = (working.height * DOWNSCALE_FACTOR).roundToInt().coerceAtLeast(1)
                val resized = working.scale(nextW, nextH)
                if (ownsWorking && resized !== working) {
                    working.recycle()
                }
                working = resized
                ownsWorking = resized !== source
            }
            val finalBytes = compressJpeg(working, MIN_JPEG_QUALITY)
            return if (finalBytes.size <= MAX_BYTES) finalBytes else null
        } finally {
            if (ownsWorking && !working.isRecycled) {
                working.recycle()
            }
            if (ownsCurrent && current !== working && !current.isRecycled) {
                current.recycle()
            }
        }
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toDouble() / longest.toDouble()
        val targetW = (bitmap.width * scale).roundToInt().coerceIn(1, maxSide)
        val targetH = (bitmap.height * scale).roundToInt().coerceIn(1, maxSide)
        return bitmap.scale(targetW, targetH)
    }

    companion object {
        const val MAX_DIMENSION_PX = 4_096
        const val MAX_BYTES = 5 * 1024 * 1024
        private const val INITIAL_JPEG_QUALITY = 92
        private const val MIN_JPEG_QUALITY = 40
        private const val QUALITY_STEP = 8
        private const val MAX_DOWNSCALE_STEPS = 5
        private const val DOWNSCALE_FACTOR = 0.85

        internal fun computeInSampleSize(width: Int, height: Int, maxSide: Int): Int {
            var sample = 1
            var longest = max(width, height)
            while (longest / 2 > maxSide) {
                sample *= 2
                longest /= 2
            }
            return sample
        }

        @SuppressLint("ExifInterface")
        internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.setRotate(180f)
                    matrix.postScale(-1f, 1f)
                }
                android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
