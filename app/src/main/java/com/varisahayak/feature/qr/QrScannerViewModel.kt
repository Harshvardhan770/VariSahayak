package com.varisahayak.feature.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.QrScanResult
import com.varisahayak.domain.model.QrToken
import com.varisahayak.domain.model.QrTokenValidator
import com.varisahayak.domain.repository.QrRepository
import com.varisahayak.domain.repository.QrResolution
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
)

/**
 * What to show after a scan. [ResolvedOffline] is a success, not a failure — the volunteer
 * continues and the token is resolved on sync.
 */
sealed interface ScanOutcome {
    data class Resolved(
        val token: QrToken,
        val subjectReference: String,
        val hasActiveIncident: Boolean,
    ) : ScanOutcome

    data class ResolvedOffline(val token: QrToken) : ScanOutcome

    data object NotRecognised : ScanOutcome

    data object Malformed : ScanOutcome

    /** The tag encodes personal data. Refused, and worth reporting to an organiser. */
    data object ContainsPersonalData : ScanOutcome
}

@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val qrRepository: QrRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrScannerUiState())
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()

    fun onCodeScanned(rawValue: String) {
        handle(QrTokenValidator.validate(rawValue))
    }

    fun onManualEntryChanged(value: String) {
        _uiState.update { it.copy(manualEntry = value) }
    }

    fun setManualEntryOpen(open: Boolean) {
        _uiState.update { it.copy(isManualEntryOpen = open) }
    }

    fun submitManualEntry() {
        handle(QrTokenValidator.validateManualEntry(_uiState.value.manualEntry))
    }

    fun toggleTorch() {
        _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    /** Clears the result so the scanner can be re-armed for another attempt. */
    fun dismissOutcome() {
        _uiState.update { it.copy(outcome = null) }
    }

    private fun handle(result: QrScanResult) {
        when (result) {
            is QrScanResult.Valid -> resolve(result.token)

            QrScanResult.NotRecognised ->
                _uiState.update { it.copy(outcome = ScanOutcome.NotRecognised) }

            QrScanResult.Malformed ->
                _uiState.update { it.copy(outcome = ScanOutcome.Malformed) }

            QrScanResult.ContainsPersonalData ->
                _uiState.update { it.copy(outcome = ScanOutcome.ContainsPersonalData) }
        }
    }

    private fun resolve(token: QrToken) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true) }

            val outcome = when (val result = qrRepository.resolve(token)) {
                is Outcome.Success -> when (val resolution = result.data) {
                    is QrResolution.Resolved -> ScanOutcome.Resolved(
                        token = resolution.token,
                        subjectReference = resolution.subjectReference,
                        hasActiveIncident = resolution.hasActiveIncident,
                    )

                    is QrResolution.Offline -> ScanOutcome.ResolvedOffline(resolution.token)

                    QrResolution.Unknown -> ScanOutcome.NotRecognised
                }

                // The repository already degrades to Offline rather than failing, so this
                // is genuinely exceptional. Proceed on the raw token regardless — nothing
                // about getting help should hinge on a lookup succeeding.
                is Outcome.Failure -> ScanOutcome.ResolvedOffline(token)
            }

            _uiState.update {
                it.copy(isResolving = false, outcome = outcome, isManualEntryOpen = false)
            }
        }
    }
}
