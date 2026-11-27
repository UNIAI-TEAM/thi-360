package vn.unicom.thi360.feature.account

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AvatarImagePreparerInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val testContext
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun oversized8000x6000ImageIsDownscaledToWithin4096PxAnd5MbJpeg() = runBlocking {
        val dir = File(context.cacheDir, "avatar-captures").apply { mkdirs() }
        val oversizedFile = File.createTempFile("oversized_", ".png", dir)
        try {
            oversizedFile.writeBytes(createSolidPngBytes(width = 8000, height = 6000))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", oversizedFile)

            val result = AndroidAvatarImagePreparer(context.contentResolver).prepare(uri)
            assertTrue("Expected Prepared result, got $result", result is AvatarPrepareResult.Prepared)
            val prepared = (result as AvatarPrepareResult.Prepared).avatar

            assertEquals("image/jpeg", prepared.mimeType)
            assertTrue("Output bytes ${prepared.bytes.size} exceeded 5 MB", prepared.bytes.size <= 5 * 1024 * 1024)
            val decoded = BitmapFactory.decodeByteArray(prepared.bytes, 0, prepared.bytes.size)
            try {
                assertTrue("Decoded width ${decoded.width} exceeded 4096", decoded.width <= 4096)
                assertTrue("Decoded height ${decoded.height} exceeded 4096", decoded.height <= 4096)
                assertEquals(4096, maxOf(decoded.width, decoded.height))
            } finally {
                decoded.recycle()
            }
        } finally {
            oversizedFile.delete()
        }
    }

    @Test
    fun exifRotatedJpegIsNormalizedToUprightOrientation() = runBlocking {
        val dir = File(context.cacheDir, "avatar-captures").apply { mkdirs() }
        val rotatedFile = File.createTempFile("rotated_", ".jpg", dir)
        try {
            val bitmap = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
            }
            val rawJpeg = ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                bitmap.recycle()
                baos.toByteArray()
            }
            rotatedFile.writeBytes(rawJpeg)
            val exif = ExifInterface(rotatedFile.absolutePath)
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            exif.saveAttributes()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", rotatedFile)
            val result = AndroidAvatarImagePreparer(context.contentResolver).prepare(uri)
            assertTrue("Expected Prepared result, got $result", result is AvatarPrepareResult.Prepared)
            val prepared = (result as AvatarPrepareResult.Prepared).avatar

            assertEquals("image/jpeg", prepared.mimeType)
            val decoded = BitmapFactory.decodeByteArray(prepared.bytes, 0, prepared.bytes.size)
            try {
                assertEquals("Rotated 160x80 must become width 80", 80, decoded.width)
                assertEquals("Rotated 160x80 must become height 160", 160, decoded.height)
            } finally {
                decoded.recycle()
            }
        } finally {
            rotatedFile.delete()
        }
    }

    @Test
    fun heicAssetDecodesToJpegWithinLimits() = runBlocking {
        val dir = File(context.cacheDir, "avatar-captures").apply { mkdirs() }
        val heicFile = File.createTempFile("sample_", ".heic", dir)
        try {
            testContext.assets.open("sample_avatar.heic").use { input ->
                heicFile.outputStream().use { output -> input.copyTo(output) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", heicFile)

            val result = AndroidAvatarImagePreparer(context.contentResolver).prepare(uri)
            assertTrue("Expected Prepared result for HEIC, got $result", result is AvatarPrepareResult.Prepared)
            val prepared = (result as AvatarPrepareResult.Prepared).avatar

            assertEquals("image/jpeg", prepared.mimeType)
            assertTrue(prepared.bytes.isNotEmpty())
            assertTrue(prepared.bytes.size <= 5 * 1024 * 1024)
            val decoded = BitmapFactory.decodeByteArray(prepared.bytes, 0, prepared.bytes.size)
            try {
                assertTrue(decoded.width in 1..4096)
                assertTrue(decoded.height in 1..4096)
            } finally {
                decoded.recycle()
            }
        } finally {
            heicFile.delete()
        }
    }

    private fun createSolidPngBytes(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        dos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val ihdr = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply {
                writeInt(width)
                writeInt(height)
                writeByte(8)
                writeByte(0)
                writeByte(0)
                writeByte(0)
                writeByte(0)
            }
        }.toByteArray()
        writePngChunk(dos, "IHDR", ihdr)

        val deflater = Deflater(Deflater.BEST_SPEED)
        val compressed = ByteArrayOutputStream()
        val row = ByteArray(width + 1)
        val buf = ByteArray(8192)
        for (y in 0 until height) {
            deflater.setInput(row)
            while (!deflater.needsInput()) {
                val n = deflater.deflate(buf)
                if (n > 0) compressed.write(buf, 0, n)
            }
        }
        deflater.finish()
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            if (n > 0) compressed.write(buf, 0, n)
        }
        deflater.end()
        writePngChunk(dos, "IDAT", compressed.toByteArray())
        writePngChunk(dos, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun writePngChunk(dos: DataOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        dos.writeInt(data.size)
        dos.write(typeBytes)
        dos.write(data)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        dos.writeInt(crc.value.toInt())
    }
}
