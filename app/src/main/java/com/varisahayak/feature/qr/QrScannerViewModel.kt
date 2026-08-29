package com.varisahayak.feature.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.location.LocationProvider
import com.varisahayak.domain.model.GeoPoint
import com.varisahayak.domain.model.QrLocation
import com.varisahayak.domain.model.QrLocationResolution
import com.varisahayak.domain.model.QrLocationValidator
import com.varisahayak.domain.repository.QrLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QrScannerUiState(
    val isResolving: Boolean = false,
    val manualEntry: String = "",
    val isManualEntryOpen: Boolean = false,
    val torchEnabled: Boolean = false,
    val outcome: ScanOutcome? = null,
    /** The volunteer's own position, captured alongside the scan when permitted. */
    val deviceLocation: GeoPoint? = null,
)

/**
 * What to show after a scan.
 *
 * [ResolvedOffline] is a success, not a failure: the volunteer proceeds against the raw
 * token and the location is attached on sync. Refusing to continue because a lookup could
 * not reach the server would mean refusing to let somebody report an emergency.
 */
sealed interface ScanOutcome {
    /** A known, active location on the route. */
    data class Resolved(val location: QrLocation) : ScanOutcome

    data class ResolvedOffline(val token: String) : ScanOutcome

    /** Ours by prefix, but no such location — or it has been withdrawn. */
    data object UnknownLocation : ScanOutcome

    /** A valid QR code, but not one of ours. */
    data object NotRecognised : ScanOutcome

    /** Ours by prefix, wrong shape. A scuffed sign — offer manual entry. */
    data object Malformed : ScanOutcome

    /**
     * The sign encodes personal data.
     *
     * A location code never should, so this is a tripwire for a badly-produced batch.
     * Refused loudly rather than quietly stored.
     */
    data object ContainsPersonalData : ScanOutcome
}

/**
 * Scans the fixed QR signs installed along the route.
 *
 * A code identifies a *place*, never a person. What the volunteer gets back is a location
 * they can then attach to an emergency report, a Found Person report, or a Lost Person
 * report — the scan itself does nothing except establish where they are standing.
 */
@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val qrLocationRepository: QrLocationRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrScannerUiState())
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()

    fun onCodeScanned(rawValue: String) = resolve(rawValue, manual = false)

    fun onManualEntryChanged(value: String) = _uiState.update { it.copy(manualEntry = value) }

    fun setManualEntryOpen(open: Boolean) = _uiState.update { it.copy(isManualEntryOpen = open) }

    /**
     * The manual fallback, and it is not optional.
     *
     * A sign that is dirty, sun-bleached, or bent is common on a walking route, and a
     * volunteer standing next to somebody who needs help must not be stopped by it.
     */
    fun submitManualEntry() = resolve(_uiState.value.manualEntry, manual = true)

    fun toggleTorch() = _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }

    /** Clears the result so the scanner can be re-armed for another attempt. */
    fun dismissOutcome() = _uiState.update { it.copy(outcome = null) }

    private fun resolve(rawValue: String, manual: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true) }

            // Format first, so an obviously wrong code fails fast without a round trip.
            val format = if (manual) {
                QrLocationValidator.validateManualEntry(rawValue)
            } else {
                QrLocationValidator.validate(rawValue)
            }

            if (format is QrLocationValidator.Format.ContainsPersonalData) {
                _uiState.update {
                    it.copy(isResolving = false, outcome = ScanOutcome.ContainsPersonalData)
                }
                return@launch
            }

            val payload = (format as? QrLocationValidator.Format.Valid)?.token
            if (payload == null) {
                _uiState.update {
                    it.copy(
                        isResolving = false,
                        outcome = when (format) {
                            is QrLocationValidator.Format.Malformed -> ScanOutcome.Malformed
                            else -> ScanOutcome.NotRecognised
                        },
                    )
                }
                return@launch
            }

            // Best-effort, and only from a permission the app already has. A scan is not a
            // reason to start tracking a volunteer.
            val deviceLocation = locationProvider.currentFix().pointOrNull

            val outcome = when (val resolution = qrLocationRepository.resolve(payload)) {
                is QrLocationResolution.Resolved -> {
                    qrLocationRepository.recordScan(
                        token = resolution.location.token,
                        deviceLocation = deviceLocation,
                    )
                    ScanOutcome.Resolved(resolution.location)
                }

                is QrLocationResolution.Offline -> ScanOutcome.ResolvedOffline(resolution.token)
                QrLocationResolution.Unknown -> ScanOutcome.UnknownLocation
                QrLocationResolution.Malformed -> ScanOutcome.Malformed
                QrLocationResolution.NotOurs -> ScanOutcome.NotRecognised
            }

            _uiState.update {
                it.copy(
                    isResolving = false,
                    outcome = outcome,
                    isManualEntryOpen = false,
                    deviceLocation = deviceLocation,
                )
            }
        }
    }
}
