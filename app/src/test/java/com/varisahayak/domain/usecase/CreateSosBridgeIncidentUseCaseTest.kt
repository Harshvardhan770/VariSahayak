package com.varisahayak.domain.usecase

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.Incident
import com.varisahayak.domain.model.IncidentCategory
import com.varisahayak.domain.model.IncidentPriority
import com.varisahayak.domain.model.IncidentStatus
import com.varisahayak.domain.model.QrToken
import com.varisahayak.domain.model.SyncState
import com.varisahayak.domain.repository.IncidentRepository
import com.varisahayak.domain.repository.QrRepository
import com.varisahayak.domain.repository.QrResolution
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CreateSosBridgeIncidentUseCaseTest {

    private val incidentRepository = mockk<IncidentRepository>()
    private val qrRepository = mockk<QrRepository>(relaxed = true)
    private val useCase = CreateSosBridgeIncidentUseCase(incidentRepository, qrRepository)

    private val token = QrToken("VS1:7ZK2M9QW4XB3HN5PRT8VCD6JFG")

    @Test
    @DisplayName("the request is flagged as an SOS so it pins to the critical band")
    fun `marks the incident as sos`() = runTest {
        val captured = slot<Boolean>()
        coEvery {
            incidentRepository.createIncident(any(), any(), any(), any(), any(), capture(captured), any())
        } returns Outcome.Success(anIncident())

        useCase(token, "Elderly pilgrim collapsed", location = null)

        assertTrue(captured.captured)
    }

    @Test
    @DisplayName("the opaque token is carried, and no personal note is ever attached")
    fun `carries token and no personal data`() = runTest {
        val tokenSlot = slot<String?>()
        val noteSlot = slot<String?>()
        coEvery {
            incidentRepository.createIncident(
                any(), any(), any(), any(), captureNullable(noteSlot), any(), captureNullable(tokenSlot),
            )
        } returns Outcome.Success(anIncident())

        useCase(token, "Needs water urgently", location = null)

        assertEquals(token.value, tokenSlot.captured)
        assertNull(noteSlot.captured)
    }

    @Test
    fun `records the resolution for the audit trail`() = runTest {
        coEvery {
            incidentRepository.createIncident(any(), any(), any(), any(), any(), any(), any())
        } returns Outcome.Success(anIncident(clientId = "incident-1"))

        useCase(token, "Lost child", location = null)

        coVerify { qrRepository.recordResolution(token, "incident-1") }
    }

    @Test
    @DisplayName("a failed audit write cannot undo a help request")
    fun `audit failure does not fail the request`() = runTest {
        coEvery {
            incidentRepository.createIncident(any(), any(), any(), any(), any(), any(), any())
        } returns Outcome.Success(anIncident())
        coEvery { qrRepository.recordResolution(any(), any()) } returns
            Outcome.Failure(AppError.Offline())

        val result = useCase(token, "Needs help", location = null)

        assertTrue(result is Outcome.Success)
    }

    @Test
    @DisplayName("no audit row is written when the incident itself was not created")
    fun `does not record resolution on failure`() = runTest {
        coEvery {
            incidentRepository.createIncident(any(), any(), any(), any(), any(), any(), any())
        } returns Outcome.Failure(AppError.Unknown())

        useCase(token, "Needs help", location = null)

        coVerify(exactly = 0) { qrRepository.recordResolution(any(), any()) }
    }

    @Test
    fun `rejects a blank description`() = runTest {
        val result = useCase(token, "   ", location = null)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AppError.Validation)
        coVerify(exactly = 0) {
            incidentRepository.createIncident(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("creation succeeds with no location — a help request is never gated on GPS")
    fun `succeeds without a location`() = runTest {
        val locationSlot = slot<GeoPoint?>()
        coEvery {
            incidentRepository.createIncident(any(), any(), captureNullable(locationSlot), any(), any(), any(), any())
        } returns Outcome.Success(anIncident())

        val result = useCase(token, "Needs help", location = null)

        assertTrue(result is Outcome.Success)
        assertNull(locationSlot.captured)
    }

    private fun anIncident(clientId: String = "incident-x") = Incident(
        clientId = clientId,
        category = IncidentCategory.OTHER,
        description = "test",
        reporterId = "volunteer-1",
        reportedAtEpochMillis = 0L,
        status = IncidentStatus.PENDING_SYNC,
        priority = IncidentPriority.CRITICAL,
        syncState = SyncState.PENDING,
        isSos = true,
    )
}
