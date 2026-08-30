package com.varisahayak.data.remote.dto

import com.varisahayak.domain.model.FaceMatchStatus
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The wire contract between `process-face` and this app.
 *
 * This boundary is worth pinning because it has already failed once, silently and in
 * production: the edge function called `/enroll` and `/compare` on a face service that
 * serves neither, sent an `X-Service-Token` header the service answers with 401, and read
 * back fields it never emits. Nothing threw. Every photograph simply came back
 * SERVICE_UNAVAILABLE forever, which is indistinguishable from an outage.
 *
 * The payloads below are the exact shapes `supabase/functions/process-face/index.ts`
 * builds in `respond()`. If that function's output changes, these fail — which is the
 * point. A route mismatch cannot be caught here (it lives on the far side of the network),
 * but a *shape* mismatch can, and shape is what the old code got wrong.
 */
class FaceProcessingContractTest {

    // Mirrors the serializer supabase-kt installs: a field this app has not been taught
    // about must be ignored, never fatal, or a server-side addition bricks every APK in the
    // field that a volunteer on the route cannot update.
    private val json = Json { ignoreUnknownKeys = true }

    @Nested
    @DisplayName("responses the edge function actually emits")
    inner class Emitted {

        @Test
        fun `a successful enrolment and search carries distances keyed by report id`() {
            val dto = json.decodeFromString<FaceProcessingDto>(
                """
                {
                  "ok": true,
                  "status": "READY",
                  "message": null,
                  "face_available": true,
                  "distances": { "lf-found-42": 0.2137, "lf-found-91": 0.3812 },
                  "faces_detected": 1,
                  "candidate_count": 17
                }
                """.trimIndent(),
            )

            assertEquals(FaceMatchStatus.READY, FaceMatchStatus.fromWire(dto.status))
            assertTrue(dto.ok)
            assertTrue(dto.faceAvailable)
            assertEquals(1, dto.facesDetected)
            assertEquals(17, dto.candidateCount)

            // The keys are Lost & Found client ids on the opposite side of the board, which
            // is what LostFoundMatchingEngine.rank() indexes faceDistances by. If these
            // stopped being client ids the lookup would miss every candidate and the face
            // signal would silently vanish again.
            assertEquals(setOf("lf-found-42", "lf-found-91"), dto.distanceTo.keys)
            assertEquals(0.2137, dto.distanceTo.getValue("lf-found-42"))
        }

        @Test
        fun `an enrolled photo with nothing on the other side yields no distances`() {
            val dto = json.decodeFromString<FaceProcessingDto>(
                """
                {
                  "ok": true, "status": "READY", "message": null,
                  "face_available": true, "distances": {},
                  "faces_detected": 1, "candidate_count": 0
                }
                """.trimIndent(),
            )

            // READY with an empty board is a success, not a failure. The report is enrolled
            // and will match the moment a counterpart is filed.
            assertEquals(FaceMatchStatus.READY, FaceMatchStatus.fromWire(dto.status))
            assertTrue(dto.distanceTo.isEmpty())
        }

        @Test
        fun `every status the function can emit maps to a known enum value`() {
            // NOT_APPLICABLE covers an ITEM report, which the function answers without ever
            // calling the face service.
            val emitted = listOf(
                "READY", "NO_FACE", "MULTIPLE_FACES",
                "INVALID_IMAGE", "SERVICE_UNAVAILABLE", "NOT_APPLICABLE",
            )

            emitted.forEach { wire ->
                val status = FaceMatchStatus.fromWire(wire)
                assertEquals(
                    wire,
                    status.wireName,
                    "$wire fell through to ${status.name}; fromWire() silently swallows " +
                        "an unmapped status as NOT_APPLICABLE, which reads as 'no photo'",
                )
            }
        }

        @Test
        fun `no face and multiple faces stay distinguishable`() {
            // The deployed service collapses both into one 400; the edge function splits
            // them again with /v1/face/detect. The volunteer-facing advice is opposite
            // ("add a photo" vs "add a photo of just this person"), so a regression that
            // merged them would show the wrong instruction.
            val noFace = json.decodeFromString<FaceProcessingDto>(
                """{"ok":false,"status":"NO_FACE","message":null,"face_available":false,
                    "distances":{},"faces_detected":0,"candidate_count":0}""",
            )
            val crowd = json.decodeFromString<FaceProcessingDto>(
                """{"ok":false,"status":"MULTIPLE_FACES","message":null,"face_available":false,
                    "distances":{},"faces_detected":3,"candidate_count":0}""",
            )

            assertEquals(FaceMatchStatus.NO_FACE, FaceMatchStatus.fromWire(noFace.status))
            assertEquals(FaceMatchStatus.MULTIPLE_FACES, FaceMatchStatus.fromWire(crowd.status))
            assertEquals(0, noFace.facesDetected)
            assertEquals(3, crowd.facesDetected)

            // Both are the volunteer's to fix by retaking the photo, unlike an outage.
            assertTrue(FaceMatchStatus.fromWire(noFace.status).isRetryableByUser)
            assertTrue(FaceMatchStatus.fromWire(crowd.status).isRetryableByUser)
        }

        @Test
        fun `an outage carries a message with no internal detail and is retried by the system`() {
            val dto = json.decodeFromString<FaceProcessingDto>(
                """
                {
                  "ok": false, "status": "SERVICE_UNAVAILABLE",
                  "message": "Face matching is temporarily unavailable. The report was saved and will continue using other matching information.",
                  "face_available": false, "distances": {},
                  "faces_detected": 0, "candidate_count": 0
                }
                """.trimIndent(),
            )

            assertTrue(FaceMatchStatus.fromWire(dto.status).isRetryableBySystem)

            // Nothing from the far side may reach a volunteer's screen: no host, no route,
            // no Flask or DeepFace text, no traceback.
            val message = dto.message.orEmpty()
            listOf("http", "Traceback", "DeepFace", "mongo", "/v1/face", "Flask", "Error:")
                .forEach {
                    assertTrue(
                        !message.contains(it, ignoreCase = true),
                        "outage message leaked '$it' to the volunteer: $message",
                    )
                }
        }
    }

    @Nested
    @DisplayName("tolerance to a function that changes underneath an old build")
    inner class Tolerance {

        @Test
        fun `only status is required and everything else degrades to no signal`() {
            val dto = json.decodeFromString<FaceProcessingDto>("""{"status":"READY"}""")

            assertEquals(FaceMatchStatus.READY, FaceMatchStatus.fromWire(dto.status))
            assertTrue(dto.distanceTo.isEmpty())
            assertEquals(0, dto.facesDetected)
            assertEquals(0, dto.candidateCount)
        }

        @Test
        fun `a field this build has never heard of is ignored rather than fatal`() {
            val dto = json.decodeFromString<FaceProcessingDto>(
                """{"status":"READY","distances":{"a":0.1},"some_future_field":{"x":[1,2]}}""",
            )

            assertEquals(0.1, dto.distanceTo.getValue("a"))
        }

        @Test
        fun `an unknown status degrades to NOT_APPLICABLE rather than throwing`() {
            // A newer function emitting a status this build predates must cost the
            // volunteer a face signal, never their report.
            assertEquals(FaceMatchStatus.NOT_APPLICABLE, FaceMatchStatus.fromWire("QUARANTINED"))
            assertEquals(FaceMatchStatus.NOT_APPLICABLE, FaceMatchStatus.fromWire(null))
        }
    }
}
