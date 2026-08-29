package com.varisahayak.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Photographs for Lost & Found reports.
 *
 * Everything here writes to the app's private files directory and nowhere else. A picture
 * of a missing child must not reach the device gallery: MediaStore entries are backed up,
 * synced to whatever cloud account is signed in, and visible to every app with media
 * permission. These files are visible to this app alone and are deleted when it is.
 *
 * Images are normalised on the way in — rotated upright and scaled down — because the two
 * things that most often waste a face-matching call are a portrait photo lying on its side
 * and a 12-megapixel original that times out on the way to the server.
 */
object PhotoCapture {

    /** The longest edge kept. Well above what Facenet needs, small enough to upload. */
    private const val MAX_EDGE_PX = 1280

    private const val JPEG_QUALITY = 85

    private const val DIRECTORY = "lostfound"

    private fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * Creates an empty file and the content URI a camera app may write to.
     *
     * The URI is what `ActivityResultContracts.TakePicture` needs; the [File] is what we
     * keep afterwards. Both refer to the same path, so nothing has to be copied out of a
     * cache directory once the capture returns.
     */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val file = File(directory(context), "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return file to uri
    }

    /**
     * Copies a gallery selection into private storage, normalised.
     *
     * A copy rather than keeping the picked URI, because a `PickVisualMedia` grant does
     * not survive a process restart — and a report filed offline may not be uploaded for
     * hours. Holding a URI we would lose the right to read is how a photo silently
     * disappears between filing and syncing.
     */
    fun importFromUri(context: Context, uri: Uri): String? {
        val destination = File(directory(context), "${UUID.randomUUID()}.jpg")

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            } ?: return null

            normalise(destination)
            destination.absolutePath
        } catch (error: Exception) {
            destination.delete()
            null
        }
    }

    /**
     * Rotates a captured file upright and scales it down, in place.
     *
     * Returns false when the file holds nothing decodable, which is the caller's cue to
     * discard it: an empty file is what a cancelled or failed capture leaves behind, and a
     * report must not carry a path to one.
     */
    fun normalise(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return false

        // Read before overwriting: re-encoding drops the EXIF, so the rotation has to be
        // baked into the pixels here or it is lost.
        val rotated = applyExifRotation(file, decoded)

        return try {
            FileOutputStream(file).use { output ->
                rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            true
        } catch (error: Exception) {
            false
        } finally {
            if (rotated !== decoded) rotated.recycle()
            decoded.recycle()
        }
    }

    /** Deletes a photo the volunteer removed from a form before submitting. */
    fun discard(path: String?) {
        val file = path?.let(::File) ?: return
        if (file.exists()) runCatching { file.delete() }
    }

    /**
     * A small bitmap for the form's preview.
     *
     * Decoded at a sample size rather than in full: this runs on the main thread inside a
     * composition, and decoding a 1280px JPEG there is a visible stutter on a cheap phone.
     */
    fun thumbnail(path: String, maxEdgePx: Int = 320): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdgePx)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    /** Base64 of the stored file, for the face-matching call. Null if it cannot be read. */
    fun readBytes(path: String): ByteArray? = runCatching { File(path).readBytes() }.getOrNull()

    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int = MAX_EDGE_PX): Int {
        var sample = 1
        while (width / (sample * 2) >= maxEdge || height / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        return sample
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            when (
                ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap

        return runCatching {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(degrees) },
                true,
            )
        }.getOrDefault(bitmap)
    }
}
