package com.varisahayak.domain.usecase

import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.LostFoundKind
import com.varisahayak.domain.model.LostFoundReport
import com.varisahayak.domain.model.LostFoundStatus
import com.varisahayak.domain.model.LostFoundSubjectType
import com.varisahayak.domain.model.MatchConfidence
import com.varisahayak.domain.model.SignalKind
import com.varisahayak.domain.model.SignalStrength
import com.varisahayak.domain.model.SyncState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The rules that decide whether a volunteer walks to the right child.
 *
 * The scenario driving most of these is §7.20: a parent reaches a volunteer at dusk and
 * reports a missing child. No photograph, a first name, an approximate age, and what the
 * child was wearing. If the engine cannot rank that, it is not useful on the route.
 */
class LostFoundMatchingEngineTest {

    private val engine = LostFoundMatchingEngine()

    /** 16:20, the time from the plan's worked example. */
    private val sixteenTwenty = 1_700_000_000_000L
    private val minute = 60_000L

    private fun lost(
        name: String? = "Aarav",
        age: Int? = 8,
        gender: String? = null,
        clothing: String? = "Yellow shirt, blue shorts",
        language: String? = "Marathi",
        physical: String? = null,
        location: GeoPoint? = null,
        routeSequence: Int? = null,
        occurredAt: Long? = sixteenTwenty,
    ) = LostFoundReport(
        clientId = "lost-1",
        kind = LostFoundKind.LOST,
        subjectType = LostFoundSubjectType.PERSON,
        title = "Aarav, approx 8",
        personName = name,
        approximateAge = age,
        gender = gender,
        clothingDescription = clothing,
        language = language,
        physicalDescription = physical,
        lastKnownLocation = location,
        routeSequence = routeSequence,
        occurredAtEpochMillis = occurredAt,
        reportedAtEpochMillis = sixteenTwenty,
        status = LostFoundStatus.OPEN,
        reportedBy = "volunteer-a",
        syncState = SyncState.SYNCED,
    )

    private fun found(
        clientId: String = "found-1",
        name: String? = null,
        age: Int? = 8,
        gender: String? = null,
        clothing: String? = "yellow shirt and blue shorts",
        language: String? = "Marathi",
        physical: String? = null,
        location: GeoPoint? = null,
        routeSequence: Int? = null,
        occurredAt: Long? = sixteenTwenty + 25 * minute,
        status: LostFoundStatus = LostFoundStatus.OPEN,
    ) = LostFoundReport(
        clientId = clientId,
        kind = LostFoundKind.FOUND,
        subjectType = LostFoundSubjectType.PERSON,
        title = "Child, approx 8",
        personName = name,
        approximateAge = age,
        gender = gender,
        clothingDescription = clothing,
        language = language,
        physicalDescription = physical,
        lastKnownLocation = location,
        routeSequence = routeSequence,
        occurredAtEpochMillis = occurredAt,
        reportedAtEpochMillis = sixteenTwenty + 25 * minute,
        status = status,
        reportedBy = "volunteer-b",
        syncState = SyncState.SYNCED,
    )

    private fun signal(report: com.varisahayak.domain.model.MatchScore, kind: SignalKind) =
        report.signals.first { it.kind == kind }

    @Nested
    @DisplayName("a missing attribute is no signal, never a mismatch")
    inner class MissingIsNotMismatch {

        @Test
        fun `neither side having a photo does not count against the pairing`() {
            val withoutFaces = engine.score(lost(), found(), faceDistance = null)

            assertEquals(
                SignalStrength.NO_SIGNAL,
                signal(withoutFaces, SignalKind.FACE).strength,
            )
            // The plan's headline case: strong on everything else, so it must still rank.
            assertEquals(MatchConfidence.HIGH, withoutFaces.confidence)
        }

        @Test
        fun `one side having a photo is not a face mismatch`() {
            // Lost has no photo, Found does. There is no distance to compute, and that is
            // "cannot compare" — not "these are different people".
            val score = engine.score(lost(), found(), faceDistance = null)

            assertEquals(SignalStrength.NO_SIGNAL, signal(score, SignalKind.FACE).strength)
            assertNotEquals(SignalStrength.CONTRADICTS, signal(score, SignalKind.FACE).strength)
        }

        @Test
        fun `an absent signal does not dilute the score`() {
            // Same evidence, but one report additionally lacks a language. The pairing is
            // no weaker for it: absent signals leave the denominator, they do not drag the
            // average down.
            val bothLanguages = engine.score(lost(), found())
            val oneLanguage = engine.score(lost(language = null), found())

            assertTrue(
                oneLanguage.overall >= bothLanguages.overall - 0.05,
                "dropping a signal dropped the score from ${bothLanguages.overall} " +
                    "to ${oneLanguage.overall}",
            )
        }

        @Test
        fun `a report with almost nothing recorded still scores rather than throwing`() {
            val bare = lost(
                name = null, age = null, clothing = null,
                language = null, occurredAt = null,
            )
            val alsoBare = found(
                name = null, age = null, clothing = null,
                language = null, occurredAt = null,
            )

            val score = engine.score(bare, alsoBare)

            assertEquals(0.0, score.overall)
            assertEquals(MatchConfidence.LOW, score.confidence)
            assertTrue(score.contributing.isEmpty())
        }
    }

    @Nested
    @DisplayName("the plan's worked example, section 7.23")
    inner class WorkedExample {

        @Test
        fun `Aarav is surfaced from description alone, with no photograph`() {
            val score = engine.score(
                lost(routeSequence = 31),
                found(routeSequence = 33),
            )

            assertEquals(MatchConfidence.HIGH, score.confidence)
            assertTrue(score.percent >= 70, "scored only ${score.percent}%")
        }

        @Test
        fun `the explanation names the signals that actually fired`() {
            val score = engine.score(lost(routeSequence = 31), found(routeSequence = 33))
            val supporting = score.signals.filter { it.strength == SignalStrength.SUPPORTS }

            assertTrue(supporting.any { it.kind == SignalKind.AGE })
            assertTrue(supporting.any { it.kind == SignalKind.CLOTHING })
            assertTrue(supporting.any { it.kind == SignalKind.TIME })
            assertTrue(supporting.any { it.kind == SignalKind.ROUTE_PROGRESSION })
            // And it says the photo could not be compared, rather than staying silent.
            assertEquals(SignalStrength.NO_SIGNAL, signal(score, SignalKind.FACE).strength)
        }

        @Test
        fun `every signal carries a non-empty explanation`() {
            // §7.30: never present an unexplained score.
            val score = engine.score(lost(routeSequence = 31), found(routeSequence = 33))

            score.signals.forEach {
                assertTrue(it.explanation.isNotBlank(), "${it.kind} had no explanation")
            }
        }

        @Test
        fun `clothing matches despite different word order and phrasing`() {
            val score = engine.score(
                lost(clothing = "Yellow shirt, blue shorts"),
                found(clothing = "blue shorts and a yellow shirt"),
            )

            assertEquals(SignalStrength.SUPPORTS, signal(score, SignalKind.CLOTHING).strength)
        }

        @Test
        fun `a transliterated name still matches`() {
            // "Aarav" and "Arav" are one child spelled by two people.
            val score = engine.score(lost(name = "Aarav"), found(name = "Arav"))

            assertNotEquals(SignalStrength.CONTRADICTS, signal(score, SignalKind.NAME).strength)
        }
    }

    @Nested
    @DisplayName("face similarity is one signal, never decisive")
    inner class FaceIsNotDecisive {

        @Test
        fun `a strong face match alone does not reach high confidence`() {
            val bare = lost(name = null, age = null, clothing = null, language = null)
            val alsoBare = found(name = null, age = null, clothing = null, language = null)

            val score = engine.score(bare, alsoBare, faceDistance = 0.05)

            assertNotEquals(
                MatchConfidence.HIGH,
                score.confidence,
                "a photograph alone must not be enough to declare a high-confidence match",
            )
        }

        @Test
        fun `a face at the tolerance boundary supports the pairing`() {
            val score = engine.score(
                lost(),
                found(),
                faceDistance = LostFoundMatchingEngine.FACE_MATCH_TOLERANCE,
            )

            assertEquals(SignalStrength.SUPPORTS, signal(score, SignalKind.FACE).strength)
        }

        @Test
        fun `a distant face argues against the pairing`() {
            val score = engine.score(lost(), found(), faceDistance = 0.95)

            assertEquals(SignalStrength.CONTRADICTS, signal(score, SignalKind.FACE).strength)
        }

        @Test
        fun `a face mismatch does not by itself veto a pairing supported by everything else`() {
            // Face is weighted heavily but is not a veto: a bad photo of the right child
            // must not erase four agreeing signals. It demotes confidence instead.
            val score = engine.score(
                lost(routeSequence = 31),
                found(routeSequence = 33),
                faceDistance = 0.9,
            )

            assertTrue(score.overall > 0.0)
            assertNotEquals(MatchConfidence.HIGH, score.confidence)
        }
    }

    @Nested
    @DisplayName("time and place")
    inner class TimeAndPlace {

        @Test
        fun `found before reported missing is a contradiction`() {
            val score = engine.score(
                lost(occurredAt = sixteenTwenty),
                found(occurredAt = sixteenTwenty - 120 * minute),
            )

            assertEquals(SignalStrength.CONTRADICTS, signal(score, SignalKind.TIME).strength)
        }

        @Test
        fun `a few minutes of clock drift is not a contradiction`() {
            // "Last seen at about 16:20" is an estimate. A found time a little earlier is
            // a rounding difference, not a physical impossibility.
            val score = engine.score(
                lost(occurredAt = sixteenTwenty),
                found(occurredAt = sixteenTwenty - 5 * minute),
            )

            assertNotEquals(SignalStrength.CONTRADICTS, signal(score, SignalKind.TIME).strength)
        }

        @Test
        fun `a nearby find supports the pairing`() {
            val near = GeoPoint(18.5204, 73.8567)
            val alsoNear = GeoPoint(18.5210, 73.8570)

            val score = engine.score(lost(location = near), found(location = alsoNear))

            assertEquals(SignalStrength.SUPPORTS, signal(score, SignalKind.LOCATION).strength)
        }

        @Test
        fun `a find hundreds of kilometres away argues against the pairing`() {
            val pune = GeoPoint(18.5204, 73.8567)
            val nagpur = GeoPoint(21.1458, 79.0882)

            val score = engine.score(lost(location = pune), found(location = nagpur))

            assertEquals(SignalStrength.CONTRADICTS, signal(score, SignalKind.LOCATION).strength)
        }

        @Test
        fun `found a little further along the route is expected, not suspicious`() {
            val score = engine.score(lost(routeSequence = 31), found(routeSequence = 33))
            val progression = signal(score, SignalKind.ROUTE_PROGRESSION)

            assertEquals(SignalStrength.SUPPORTS, progression.strength)
            assertTrue(progression.explanation.contains("ahead"))
        }

        @Test
        fun `found a long way back along the route argues against the pairing`() {
            val score = engine.score(lost(routeSequence = 31), found(routeSequence = 12))

            assertEquals(
                SignalStrength.CONTRADICTS,
                signal(score, SignalKind.ROUTE_PROGRESSION).strength,
            )
        }

        @Test
        fun `doubling back a single point is plausible`() {
            val score = engine.score(lost(routeSequence = 31), found(routeSequence = 30))

            assertEquals(
                SignalStrength.NEUTRAL,
                signal(score, SignalKind.ROUTE_PROGRESSION).strength,
            )
        }
    }

    @Nested
    @DisplayName("ranking a pool")
    inner class Ranking {

        @Test
        fun `only the opposite side is ever paired`() {
            // Two Lost reports for the same child are duplicates, not a match.
            val anotherLost = lost().copy(clientId = "lost-2")
            val ranked = engine.rank(lost(), listOf(anotherLost, found()))

            assertEquals(listOf("found-1"), ranked.map { it.report.clientId })
        }

        @Test
        fun `closed reports drop out of the pool`() {
            val reunited = found(clientId = "found-closed", status = LostFoundStatus.REUNITED)
            val ranked = engine.rank(lost(), listOf(reunited, found()))

            assertEquals(listOf("found-1"), ranked.map { it.report.clientId })
        }

        @Test
        fun `items are never matched against people`() {
            val bag = found(clientId = "found-bag")
                .copy(subjectType = LostFoundSubjectType.ITEM)

            assertTrue(engine.rank(lost(), listOf(bag)).isEmpty())
        }

        @Test
        fun `multiple plausible candidates are all returned, ranked`() {
            // §7.28: several children may wear yellow shirts. Rank them; do not hide them.
            val close = found(clientId = "found-close", age = 8, routeSequence = 33)
            val plausible = found(clientId = "found-plausible", age = 9, routeSequence = 35)

            val ranked = engine.rank(lost(routeSequence = 31), listOf(plausible, close))

            assertEquals(2, ranked.size)
            assertEquals("found-close", ranked.first().report.clientId)
            assertTrue(ranked[0].score.overall >= ranked[1].score.overall)
        }

        @Test
        fun `ranking is deterministic, so a decision can be reproduced`() {
            val pool = listOf(
                found(clientId = "b"),
                found(clientId = "a"),
                found(clientId = "c"),
            )

            assertEquals(
                engine.rank(lost(), pool).map { it.report.clientId },
                engine.rank(lost(), pool.reversed()).map { it.report.clientId },
            )
        }

        @Test
        fun `an implausible pairing is not surfaced at all`() {
            val unrelated = found(
                clientId = "found-unrelated",
                age = 45,
                clothing = "green sari",
                language = "Hindi",
                occurredAt = sixteenTwenty - 500 * minute,
                routeSequence = 4,
            )

            assertTrue(engine.rank(lost(routeSequence = 31), listOf(unrelated)).isEmpty())
        }

        @Test
        fun `an empty pool ranks to an empty list`() {
            assertTrue(engine.rank(lost(), emptyList()).isEmpty())
        }

        @Test
        fun `a found report can search the lost side too`() {
            // §7.21C requires the reverse flow, not just Lost -> Found.
            val ranked = engine.rank(found(routeSequence = 33), listOf(lost(routeSequence = 31)))

            assertEquals(listOf("lost-1"), ranked.map { it.report.clientId })
        }
    }

    @Nested
    @DisplayName("confidence reflects how much evidence there was")
    inner class Confidence {

        @Test
        fun `a high score on thin evidence is not high confidence`() {
            val thinLost = lost(
                name = null, age = 8, clothing = null,
                language = null, occurredAt = null,
            )
            val thinFound = found(
                name = null, age = 8, clothing = null,
                language = null, occurredAt = null,
            )

            val score = engine.score(thinLost, thinFound)

            assertTrue(score.overall > 0.5)
            assertNotEquals(MatchConfidence.HIGH, score.confidence)
        }

        @Test
        fun `two contradictions demote a pairing to low confidence`() {
            val score = engine.score(
                lost(name = "Aarav", age = 8, routeSequence = 31),
                found(name = "Priya", age = 30, routeSequence = 33),
            )

            assertEquals(MatchConfidence.LOW, score.confidence)
        }

        @Test
        fun `score always stays within zero and one`() {
            listOf(null, 0.0, 0.4, 0.6, 1.0, 2.0).forEach { distance ->
                val score = engine.score(lost(routeSequence = 31), found(routeSequence = 33), distance)
                assertTrue(score.overall in 0.0..1.0, "score ${score.overall} out of range")
                assertTrue(score.percent in 0..100)
            }
        }
    }

    @Nested
    @DisplayName("both sides remain usable independently")
    inner class Independence {

        @Test
        fun `a found report with no name and no photo is still matchable`() {
            // The commonest real case: a frightened child who will not speak, and a
            // volunteer with no time to take a picture.
            val silent = found(name = null, language = null)

            val ranked = engine.rank(lost(routeSequence = 31), listOf(silent))

            assertTrue(ranked.isNotEmpty())
        }

        @Test
        fun `a report is never considered a match for itself`() {
            assertTrue(engine.rank(lost(), listOf(lost())).isEmpty())
        }
    }
}
