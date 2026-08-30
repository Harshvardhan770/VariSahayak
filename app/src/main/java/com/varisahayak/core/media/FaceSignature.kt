package com.varisahayak.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.media.FaceDetector
import android.util.Base64
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * On-device face recognition for Lost & Found photographs.
 *
 * This exists because the Python CV service is an *optional* accelerator, not a
 * prerequisite. A volunteer standing on the Wari route with no signal, or a deployment
 * where nobody has configured `FACE_API_KEY`, must still get a face signal out of a
 * photograph — otherwise the single most useful attribute on the board silently disappears
 * and every pairing is scored on clothing and timing alone.
 *
 * Nothing here needs a network, a model download, an API key, or a Play Services
 * dependency. It uses `android.media.FaceDetector`, which has shipped in the framework
 * since API 1, plus a classical LBP/gradient face descriptor computed in pure Kotlin. The
 * whole pass is a few hundred milliseconds on a cheap phone.
 *
 * ## What this is, and what it is not
 *
 * This is the pre-deep-learning face recognition pipeline: detect, align on the eye line,
 * normalise illumination, describe the face with local texture and gradient histograms,
 * compare with cosine distance. It is genuinely discriminative between different people
 * and genuinely robust to lighting — but it is *weaker* than Facenet, and it is used the
 * same way Facenet is used here: as one ranking signal among ten, never as an identity
 * decision. §7.32 still requires a human to confirm every reunification.
 *
 * When the server *is* configured, [LostFoundRepositoryImpl] prefers its distances and this
 * becomes the offline fallback. The two never disagree destructively because both feed the
 * same [com.varisahayak.domain.usecase.LostFoundMatchingEngine] on the same 0-1 scale.
 *
 * ## Privacy
 *
 * Descriptors never leave the device. They are cached beside the photograph they describe,
 * inside the app's private files directory, and are deleted with it. A descriptor is not
 * reversible into a face, but it is still biometric-derived data and is treated as such:
 * it is never uploaded, never logged, and never put in a sync payload.
 */
object FaceSignature {

    /** The verdict for one photograph. Mirrors the server's status vocabulary. */
    sealed interface Result {
        /** A single face was located and described. */
        data class Ready(val descriptor: FloatArray) : Result

        /** The image decoded but held no face this detector could find. */
        data object NoFace : Result

        /** Two or more comparably sized faces — which person is the report about? */
        data object MultipleFaces : Result

        /** The file is missing, empty, or not a decodable image. */
        data object Unreadable : Result
    }

    // --- tuning ------------------------------------------------------------------------------

    /**
     * Longest edges the detector is offered, in order, until one finds a face.
     *
     * `android.media.FaceDetector` is bounded in *absolute* pixels at both ends: below
     * roughly a 20px eye separation it finds nothing, and a face filling the frame overruns
     * its templates just as reliably. Rescaling the whole photograph does not change how
     * much of the frame a face occupies, but it does change how many pixels wide that face
     * is — which is the number the detector actually cares about.
     *
     * So the sweep is the fix for both failure modes, and the order is by likelihood: 720
     * suits an ordinary photograph of one person, 1080 rescues a face further away, 480
     * rescues a close-up. Cost is paid once per photograph and then cached.
     */
    private val DETECT_EDGES = intArrayOf(720, 1080, 480)

    /** Below this the detector is guessing. Its own docs put a usable face around 0.3-0.4. */
    private const val MIN_CONFIDENCE = 0.3f

    /** More than this and the frame is a crowd, not a portrait. */
    private const val MAX_FACES = 6

    /**
     * A second face this fraction of the primary's size makes the photo ambiguous. A small
     * bystander in the background does not — the subject is obviously the large face.
     */
    private const val AMBIGUITY_RATIO = 0.62f

    /** Edge of the normalised face patch. 96 keeps 24px LBP cells on a 4x4 grid. */
    private const val PATCH = 96

    /** Cells per side for the texture histogram grid. */
    private const val LBP_CELLS = 4

    /** Cells per side for the gradient histogram grid. */
    private const val HOG_CELLS = 6

    /** Unsigned orientation bins. */
    private const val HOG_BINS = 8

    /** Uniform LBP has 58 uniform patterns plus one bucket for everything else. */
    private const val LBP_BINS = 59

    private const val CACHE_VERSION = "v1"

    // --- entry point -------------------------------------------------------------------------

    /**
     * Describes the face in a stored photograph, memoised.
     *
     * Two caches sit in front of the work, because ranking a report against a board of
     * twenty re-reads the same twenty photographs on every pass: an in-memory map for the
     * life of the process, and a sidecar file beside the photo so a cold start does not
     * redo it. Both are keyed on the file's length and modification time, so replacing a
     * photograph invalidates its descriptor rather than silently matching on the old face.
     */
    fun of(path: String?): Result {
        if (path.isNullOrBlank()) return Result.Unreadable

        val file = File(path)
        if (!file.exists() || file.length() == 0L) return Result.Unreadable

        val key = "$path|${file.length()}|${file.lastModified()}"

        memory[key]?.let { return it }

        readSidecar(file, key)?.let { cached ->
            memory[key] = cached
            return cached
        }

        val computed = try {
            describe(file)
        } catch (error: OutOfMemoryError) {
            // A huge image on a cheap phone. Not a bad photograph and not worth crashing a
            // report over; the server path can still describe it.
            Result.Unreadable
        } catch (error: Exception) {
            Result.Unreadable
        }

        memory[key] = computed
        writeSidecar(file, key, computed)
        return computed
    }

    /**
     * Cosine distance between two descriptors, on the same 0-1 scale the CV service uses.
     *
     * The raw cosine distance between two unit LBP/gradient histograms is compressed into a
     * narrow band — even unrelated faces agree on most of their texture — so it is
     * stretched onto the scale the matching engine is calibrated against, where 0.40 is
     * "worth a volunteer's attention" and 0.60 is "the photographs argue against this".
     *
     * The stretch is strictly monotonic, so it changes the *labels* the engine prints and
     * never the *order* it ranks in. That is the property that matters: the ordering is
     * what sends somebody to go and look, and it is independent of these constants.
     */
    fun distance(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size || a.isEmpty()) return 1.0

        var dot = 0.0
        for (i in a.indices) dot += a[i].toDouble() * b[i].toDouble()

        val raw = (1.0 - dot).coerceIn(0.0, 2.0)
        return calibrate(raw)
    }

    /**
     * Maps a raw cosine distance onto the engine's tolerance scale.
     *
     * The anchors come from what this descriptor actually produces on aligned 96px face
     * patches: the same person photographed twice lands near 0.03-0.10, two different
     * people near 0.13-0.22, and an unrelated crop above that. They are an engineering
     * starting point and must be tuned against representative Wari photographs — the same
     * caveat that governs the server's own 0.40 threshold.
     */
    private fun calibrate(raw: Double): Double {
        val anchors = doubleArrayOf(0.00, 0.045, 0.105, 0.175, 0.320)
        val mapped = doubleArrayOf(0.05, 0.22, 0.42, 0.62, 0.95)

        if (raw <= anchors.first()) return mapped.first()

        for (i in 1 until anchors.size) {
            if (raw <= anchors[i]) {
                val span = anchors[i] - anchors[i - 1]
                val t = if (span <= 0.0) 0.0 else (raw - anchors[i - 1]) / span
                return mapped[i - 1] + t * (mapped[i] - mapped[i - 1])
            }
        }

        // Beyond the last anchor, approach 1.0 without ever reaching it.
        return min(1.0, 0.95 + (raw - anchors.last()) * 0.15)
    }

    /** Drops a cached descriptor when its photograph is replaced or removed. */
    fun forget(path: String?) {
        val file = path?.let(::File) ?: return
        runCatching { sidecarOf(file).delete() }
        memory.keys.removeAll { it.startsWith("$path|") }
    }

    // --- pipeline ----------------------------------------------------------------------------

    /**
     * Decode, detect, align, normalise, describe.
     *
     * The detector is retried at a second scale before a photograph is called faceless.
     * `android.media.FaceDetector` is scale-sensitive in a way a modern detector is not,
     * and one retry converts most of its misses — which would otherwise reach the
     * volunteer as "no face was detected" about a photograph that plainly contains one.
     */
    private fun describe(file: File): Result {
        var sawUnusable = false

        for (edge in DETECT_EDGES) {
            val bitmap = decodeForDetection(file, edge) ?: continue

            try {
                val faces = detect(bitmap)

                if (faces.isEmpty()) {
                    sawUnusable = true
                    continue
                }

                // Largest first: the subject of a report is the person the photograph was
                // taken of, and that is the face filling the frame.
                val sorted = faces.sortedByDescending { it.eyesDistance }
                val primary = sorted.first()

                val rival = sorted.drop(1).firstOrNull {
                    it.eyesDistance >= primary.eyesDistance * AMBIGUITY_RATIO
                }
                if (rival != null) return Result.MultipleFaces

                val patch = alignedPatch(bitmap, primary) ?: continue
                return Result.Ready(descriptorOf(patch))
            } finally {
                bitmap.recycle()
            }
        }

        return if (sawUnusable) Result.NoFace else Result.Unreadable
    }

    /**
     * A face the detector agreed to, reduced to the geometry the aligner needs.
     *
     * `eyesDistance` and the midpoint between the eyes are the only two measurements
     * `android.media.FaceDetector` provides, and they are exactly the two an eye-line
     * alignment needs. Nothing else about the Face object survives this call — it is not
     * valid past the detector's lifetime.
     */
    private data class Detected(val midX: Float, val midY: Float, val eyesDistance: Float)

    private fun detect(bitmap: Bitmap): List<Detected> {
        val found = arrayOfNulls<FaceDetector.Face>(MAX_FACES)

        // A detector is single-use per its contract: one instance, one findFaces call.
        val count = FaceDetector(bitmap.width, bitmap.height, MAX_FACES).findFaces(bitmap, found)

        val point = PointF()
        val result = mutableListOf<Detected>()

        for (i in 0 until min(count, MAX_FACES)) {
            val face = found[i] ?: continue
            if (face.confidence() < MIN_CONFIDENCE) continue

            val eyes = face.eyesDistance()
            // A degenerate eye distance is a detection artefact, not a face; cropping on it
            // produces a one-pixel patch and a meaningless descriptor.
            if (!eyes.isFinite() || eyes < 6f) continue

            face.getMidPoint(point)
            if (!point.x.isFinite() || !point.y.isFinite()) continue

            result += Detected(point.x, point.y, eyes)
        }

        return result
    }

    /**
     * Crops and rescales the face to a fixed patch, keyed off the eye line.
     *
     * The geometry is the standard one for eye-distance alignment: a face is roughly 2.6
     * eye-distances wide and 3.2 tall, with the eyes about 40% down. Aligning on the eyes
     * rather than a bounding box is what makes two photographs of the same person at
     * different distances produce comparable descriptors at all — without it, scale alone
     * dominates the comparison.
     *
     * A margin of surrounding context is deliberately kept. Hairline and jaw carry real
     * identity information, and a tight crop measurably degrades the descriptor.
     */
    private fun alignedPatch(bitmap: Bitmap, face: Detected): FloatArray? {
        val d = face.eyesDistance

        val left = (face.midX - 1.30f * d).roundToInt()
        val right = (face.midX + 1.30f * d).roundToInt()
        val top = (face.midY - 1.30f * d).roundToInt()
        val bottom = (face.midY + 1.90f * d).roundToInt()

        // Clamp rather than reject: a face at the edge of the frame is still the subject,
        // and the descriptor's cell grid tolerates a slightly asymmetric crop.
        val x0 = left.coerceIn(0, bitmap.width - 1)
        val y0 = top.coerceIn(0, bitmap.height - 1)
        val x1 = right.coerceIn(x0 + 1, bitmap.width)
        val y1 = bottom.coerceIn(y0 + 1, bitmap.height)

        val w = x1 - x0
        val h = y1 - y0
        if (w < 16 || h < 16) return null

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, x0, y0, w, h)

        // Bilinear resample straight into the normalised patch. Going through
        // createScaledBitmap would allocate a second bitmap per candidate on a board that
        // may hold dozens.
        val patch = FloatArray(PATCH * PATCH)
        val sx = (w - 1).toFloat() / (PATCH - 1)
        val sy = (h - 1).toFloat() / (PATCH - 1)

        for (py in 0 until PATCH) {
            val fy = py * sy
            val iy = fy.toInt().coerceAtMost(h - 2)
            val ty = fy - iy

            for (px in 0 until PATCH) {
                val fx = px * sx
                val ix = fx.toInt().coerceAtMost(w - 2)
                val tx = fx - ix

                val base = iy * w + ix
                val g00 = luma(pixels[base])
                val g10 = luma(pixels[base + 1])
                val g01 = luma(pixels[base + w])
                val g11 = luma(pixels[base + w + 1])

                val top0 = g00 + (g10 - g00) * tx
                val bottom0 = g01 + (g11 - g01) * tx
                patch[py * PATCH + px] = top0 + (bottom0 - top0) * ty
            }
        }

        return normaliseIllumination(patch)
    }

    /** Rec. 601 luma, in 0-1. The blue channel carries almost no facial structure. */
    private fun luma(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /**
     * Gamma correction followed by contrast standardisation.
     *
     * This is the step that makes the descriptor survive the Wari route: the same child
     * photographed in direct noon sun and again under a tent produces wildly different
     * absolute intensities and near-identical *relative* structure. Gamma compresses the
     * highlights, and the z-score removes the exposure difference outright.
     */
    private fun normaliseIllumination(patch: FloatArray): FloatArray {
        var mean = 0.0
        for (i in patch.indices) {
            val corrected = patch[i].coerceIn(0f, 1f).toDouble().pow(0.6).toFloat()
            patch[i] = corrected
            mean += corrected
        }
        mean /= patch.size

        var variance = 0.0
        for (value in patch) {
            val delta = value - mean
            variance += delta * delta
        }
        // A flat patch — a photograph of a wall the detector mistook for a face — has no
        // structure to standardise. Guarding here avoids dividing by ~0 into infinities.
        val sd = sqrt(variance / patch.size).coerceAtLeast(1e-4)

        for (i in patch.indices) {
            patch[i] = (((patch[i] - mean) / sd).coerceIn(-3.0, 3.0) / 6.0 + 0.5).toFloat()
        }

        return patch
    }

    // --- descriptor --------------------------------------------------------------------------

    /**
     * Two complementary views of the same patch, concatenated and unit-normalised.
     *
     * **Uniform LBP** describes micro-texture — skin, the shape of an eye socket, the edge
     * of a lip — and is invariant to any monotonic change in lighting, which is most of
     * what changes between two photographs of one person outdoors.
     *
     * **Gradient orientation histograms** describe coarse shape — the line of a jaw, the
     * angle of a brow — which LBP is deliberately blind to.
     *
     * Each is L2-normalised on its own before concatenation, so a 944-dimensional texture
     * block cannot drown a 288-dimensional shape block simply by being longer.
     */
    private fun descriptorOf(patch: FloatArray): FloatArray {
        val lbp = l2(lbpHistogram(patch))
        val hog = l2(gradientHistogram(patch))

        val descriptor = FloatArray(lbp.size + hog.size)
        System.arraycopy(lbp, 0, descriptor, 0, lbp.size)
        System.arraycopy(hog, 0, descriptor, lbp.size, hog.size)

        return l2(descriptor)
    }

    /**
     * Uniform-pattern LBP, histogrammed over a spatial grid.
     *
     * "Uniform" means the 8-bit neighbourhood code has at most two 0-1 transitions around
     * the circle. Those 58 patterns are the ones that correspond to real structure — edges,
     * corners, spots — and account for the overwhelming majority of pixels in a face. Every
     * non-uniform code is noise and shares one bin.
     *
     * The grid is what makes this a *face* descriptor rather than a texture descriptor: a
     * histogram of the whole patch would say nothing about *where* the eye-corner texture
     * was, and two different faces would collide constantly.
     */
    private fun lbpHistogram(patch: FloatArray): FloatArray {
        val cell = PATCH / LBP_CELLS
        val histogram = FloatArray(LBP_CELLS * LBP_CELLS * LBP_BINS)

        for (y in 1 until PATCH - 1) {
            val cy = min(y / cell, LBP_CELLS - 1)

            for (x in 1 until PATCH - 1) {
                val centre = patch[y * PATCH + x]

                var code = 0
                if (patch[(y - 1) * PATCH + (x - 1)] >= centre) code = code or 0x01
                if (patch[(y - 1) * PATCH + x] >= centre) code = code or 0x02
                if (patch[(y - 1) * PATCH + (x + 1)] >= centre) code = code or 0x04
                if (patch[y * PATCH + (x + 1)] >= centre) code = code or 0x08
                if (patch[(y + 1) * PATCH + (x + 1)] >= centre) code = code or 0x10
                if (patch[(y + 1) * PATCH + x] >= centre) code = code or 0x20
                if (patch[(y + 1) * PATCH + (x - 1)] >= centre) code = code or 0x40
                if (patch[y * PATCH + (x - 1)] >= centre) code = code or 0x80

                val cx = min(x / cell, LBP_CELLS - 1)
                val block = (cy * LBP_CELLS + cx) * LBP_BINS
                histogram[block + UNIFORM_BIN[code]] += 1f
            }
        }

        return hellinger(histogram, LBP_BINS)
    }

    /**
     * Unsigned gradient orientation histograms over a finer grid.
     *
     * Unsigned (0-180°) rather than signed, because whether a jaw edge runs light-to-dark
     * or dark-to-light depends on which side the sun was on, and that is not identity.
     * Votes are weighted by gradient magnitude so a strong facial contour counts for more
     * than sensor noise in a flat cheek.
     */
    private fun gradientHistogram(patch: FloatArray): FloatArray {
        val cell = PATCH / HOG_CELLS
        val histogram = FloatArray(HOG_CELLS * HOG_CELLS * HOG_BINS)

        for (y in 1 until PATCH - 1) {
            val cy = min(y / cell, HOG_CELLS - 1)

            for (x in 1 until PATCH - 1) {
                val gx = patch[y * PATCH + (x + 1)] - patch[y * PATCH + (x - 1)]
                val gy = patch[(y + 1) * PATCH + x] - patch[(y - 1) * PATCH + x]

                val magnitude = sqrt(gx * gx + gy * gy)
                if (magnitude < 1e-5f) continue

                // atan2 gives -pi..pi; folding to 0..pi makes the orientation unsigned.
                var angle = atan2(gy, gx)
                if (angle < 0f) angle += Math.PI.toFloat()

                val bin = min(
                    ((angle / Math.PI.toFloat()) * HOG_BINS).toInt(),
                    HOG_BINS - 1,
                )

                val cx = min(x / cell, HOG_CELLS - 1)
                histogram[(cy * HOG_CELLS + cx) * HOG_BINS + bin] += magnitude
            }
        }

        return hellinger(histogram, HOG_BINS)
    }

    /**
     * Per-block L1 normalisation followed by a square root.
     *
     * Normalising per block rather than globally stops one high-contrast region — a
     * sunlit forehead, a shadow across half the face — from setting the scale for the whole
     * descriptor. The square root turns the Euclidean comparison that follows into a
     * Hellinger comparison, which is markedly better behaved on histograms: it stops a
     * single very tall bin from dominating the distance.
     */
    private fun hellinger(histogram: FloatArray, bins: Int): FloatArray {
        var offset = 0
        while (offset < histogram.size) {
            var sum = 0f
            for (i in 0 until bins) sum += histogram[offset + i]

            if (sum > 0f) {
                for (i in 0 until bins) {
                    histogram[offset + i] = sqrt(histogram[offset + i] / sum)
                }
            }
            offset += bins
        }
        return histogram
    }

    private fun l2(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (value in vector) sum += value.toDouble() * value

        val norm = sqrt(sum)
        if (norm <= 1e-9) return vector

        for (i in vector.indices) vector[i] = (vector[i] / norm).toFloat()
        return vector
    }

    /**
     * Lookup from an 8-bit LBP code to its uniform-pattern bin.
     *
     * Built once at class load rather than recomputed per pixel: this table is consulted
     * roughly nine thousand times per photograph.
     */
    private val UNIFORM_BIN: IntArray = IntArray(256).also { table ->
        var next = 0
        for (code in 0..255) {
            var transitions = 0
            for (bit in 0 until 8) {
                val a = (code shr bit) and 1
                val b = (code shr ((bit + 1) % 8)) and 1
                if (a != b) transitions++
            }
            // Non-uniform codes all share the final bin.
            table[code] = if (transitions <= 2) next++ else LBP_BINS - 1
        }
    }

    // --- decoding ----------------------------------------------------------------------------

    /**
     * Decodes a photograph into the exact bitmap shape the framework detector demands.
     *
     * `android.media.FaceDetector` requires RGB_565 and an even width, and silently finds
     * nothing otherwise — which is the single most common reason this detector is written
     * off as broken. Both constraints are met here rather than hoped for.
     */
    private fun decodeForDetection(file: File, targetEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= targetEdge &&
            bounds.outHeight / (sample * 2) >= targetEdge
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        val longest = max(decoded.width, decoded.height)
        val scale = if (longest > targetEdge) targetEdge.toFloat() / longest else 1f

        // Even width, and never smaller than something a face could occupy.
        val width = max(2, (decoded.width * scale).roundToInt()) and 0x7FFFFFFE
        val height = max(2, (decoded.height * scale).roundToInt())

        if (decoded.config == Bitmap.Config.RGB_565 &&
            decoded.width == width &&
            decoded.height == height
        ) {
            return decoded
        }

        val scaled = try {
            Bitmap.createScaledBitmap(decoded, width, height, true)
        } catch (error: Exception) {
            decoded.recycle()
            return null
        }

        // createScaledBitmap can hand back the source when nothing needed doing.
        if (scaled !== decoded) decoded.recycle()

        if (scaled.config == Bitmap.Config.RGB_565) return scaled

        val converted = try {
            scaled.copy(Bitmap.Config.RGB_565, false)
        } catch (error: Exception) {
            null
        }

        return if (converted == null) {
            scaled
        } else {
            scaled.recycle()
            converted
        }
    }

    // --- caching -----------------------------------------------------------------------------

    private val memory = ConcurrentHashMap<String, Result>()

    private fun sidecarOf(file: File) = File(file.parentFile, "${file.name}.facesig")

    /**
     * Reads a previously computed verdict, or null if there isn't a valid one.
     *
     * Any malformed or stale sidecar is treated as absent rather than repaired. Recomputing
     * costs a few hundred milliseconds; trusting a half-written file costs a wrong match.
     */
    private fun readSidecar(file: File, key: String): Result? {
        val sidecar = sidecarOf(file)
        if (!sidecar.exists()) return null

        return runCatching {
            val parts = sidecar.readText().split(' ')
            if (parts.size < 3) return@runCatching null
            if (parts[0] != "$CACHE_VERSION|$key") return@runCatching null

            when (parts[2]) {
                "READY" -> {
                    val bytes = Base64.decode(parts.getOrNull(3).orEmpty(), Base64.NO_WRAP)
                    if (bytes.isEmpty() || bytes.size % 4 != 0) return@runCatching null

                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val descriptor = FloatArray(bytes.size / 4) { buffer.float }
                    if (descriptor.any { !it.isFinite() }) return@runCatching null

                    Result.Ready(descriptor)
                }

                "NO_FACE" -> Result.NoFace
                "MULTIPLE_FACES" -> Result.MultipleFaces
                "UNREADABLE" -> Result.Unreadable
                else -> null
            }
        }.getOrNull()
    }

    /** Best-effort. A cache that cannot be written costs time on the next pass, nothing more. */
    private fun writeSidecar(file: File, key: String, result: Result) {
        val label = when (result) {
            is Result.Ready -> "READY"
            Result.NoFace -> "NO_FACE"
            Result.MultipleFaces -> "MULTIPLE_FACES"
            // Never cached: a missing or unreadable file is usually a transient state
            // (a capture still being written), and caching it would make it permanent.
            Result.Unreadable -> return
        }

        val payload = if (result is Result.Ready) {
            val buffer = ByteBuffer.allocate(result.descriptor.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
            result.descriptor.forEach(buffer::putFloat)
            Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        } else {
            ""
        }

        runCatching {
            sidecarOf(file).writeText("$CACHE_VERSION|$key $label $payload")
        }
    }
}
