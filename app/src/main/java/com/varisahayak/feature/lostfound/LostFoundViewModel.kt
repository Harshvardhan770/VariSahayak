package com.varisahayak.feature.lostfound

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.location.LocationProvider
import com.varisahayak.domain.model.FaceMatchStatus
import com.varisahayak.domain.model.LostFoundKind
import com.varisahayak.domain.model.LostFoundReport
import com.varisahayak.domain.model.LostFoundSubjectType
import com.varisahayak.domain.model.QrLocation
import com.varisahayak.domain.model.QrLocationResolution
import com.varisahayak.domain.repository.LostFoundRepository
import com.varisahayak.domain.repository.QrLocationRepository
import com.varisahayak.domain.repository.ReportDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which side of the board is on screen. */
enum class BoardFilter { ALL, LOST, FOUND }

/**
 * The report form.
 *
 * Every field but [title] is optional, and that is a product decision rather than an
 * oversight: a parent who reaches a volunteer at dusk with no photograph and half a
 * description must still be able to file something the matching engine can work with.
 */
data class ReportFormState(
    val kind: LostFoundKind = LostFoundKind.LOST,
    val subjectType: LostFoundSubjectType = LostFoundSubjectType.PERSON,
    val title: String = "",
    val description: String = "",
    val personName: String = "",
    val approximateAge: String = "",
    val gender: String = "",
    val clothingDescription: String = "",
    val physicalDescription: String = "",
    val language: String = "",
    val condition: String = "",
    val additionalNotes: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val photoLocalPath: String? = null,
) {
    /** The only hard requirement. Everything else can be filled in later. */
    val canSubmit: Boolean get() = title.isNotBlank()
}

data class LostFoundUiState(
    val query: String = "",
    val filter: BoardFilter = BoardFilter.ALL,
    val isReportOpen: Boolean = false,
    val form: ReportFormState = ReportFormState(),
    val scannedLocation: QrLocation? = null,
    /** Set when a scan resolved to nothing; the report is still filed against the token. */
    val unresolvedToken: String? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val justReportedClientId: String? = null,
    val candidateCount: Int = 0,
    val unsyncedCount: Int = 0,
    /** Shown after submit when the photo could not be used for face matching. */
    val photoNotice: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LostFoundViewModel @Inject constructor(
    private val lostFoundRepository: LostFoundRepository,
    private val qrLocationRepository: QrLocationRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LostFoundUiState())
    val uiState: StateFlow<LostFoundUiState> = _uiState.asStateFlow()

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(BoardFilter.ALL)

    init {
        viewModelScope.launch {
            lostFoundRepository.observeCandidateCount().collect { count ->
                _uiState.update { it.copy(candidateCount = count) }
            }
        }
        viewModelScope.launch {
            lostFoundRepository.observeUnsyncedCount().collect { count ->
                _uiState.update { it.copy(unsyncedCount = count) }
            }
        }
    }

    /**
     * Reads from the local database, so search works with no connectivity — which is
     * exactly when a volunteer on the route is looking for a match.
     */
    val reports: StateFlow<List<LostFoundReport>> = combine(
        query
            .debounce(SEARCH_DEBOUNCE_MILLIS)
            .flatMapLatest { text ->
                if (text.isBlank()) {
                    lostFoundRepository.observeAll()
                } else {
                    lostFoundRepository.search(text)
                }
            },
        filter,
    ) { all, side ->
        when (side) {
            BoardFilter.ALL -> all
            BoardFilter.LOST -> all.filter { it.kind == LostFoundKind.LOST }
            BoardFilter.FOUND -> all.filter { it.kind == LostFoundKind.FOUND }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChanged(value: String) {
        _uiState.update { it.copy(query = value) }
        query.value = value
    }

    fun onFilterChanged(value: BoardFilter) {
        _uiState.update { it.copy(filter = value) }
        filter.value = value
        // Re-emit so the flatMapLatest above picks the new filter up.
        query.value = query.value
    }

    /**
     * Opens the form.
     *
     * [kind] is passed by the caller so the Found Person action on the dashboard lands
     * directly on a Found form — §7.15 asks for that to be prominent, and a volunteer
     * holding a lost child should not have to change a dropdown first.
     */
    fun openReport(kind: LostFoundKind) {
        _uiState.update {
            it.copy(
                isReportOpen = true,
                form = ReportFormState(kind = kind),
                error = null,
                justReportedClientId = null,
                photoNotice = null,
            )
        }
    }

    fun closeReport() = _uiState.update { it.copy(isReportOpen = false, error = null) }

    fun updateForm(mutate: (ReportFormState) -> ReportFormState) =
        _uiState.update { it.copy(form = mutate(it.form), error = null) }

    /**
     * Attaches a scanned QR sign as the report's fixed reference location.
     *
     * An unresolved token is kept rather than discarded: a report filed in a dead spot
     * against a raw token is reconciled on sync, and refusing it would mean refusing help.
     */
    fun attachScannedLocation(rawPayload: String) {
        viewModelScope.launch {
            when (val resolution = qrLocationRepository.resolve(rawPayload)) {
                is QrLocationResolution.Resolved -> _uiState.update {
                    it.copy(scannedLocation = resolution.location, unresolvedToken = null)
                }

                is QrLocationResolution.Offline -> _uiState.update {
                    it.copy(scannedLocation = null, unresolvedToken = resolution.token)
                }

                QrLocationResolution.Unknown,
                QrLocationResolution.Malformed,
                QrLocationResolution.NotOurs,
                -> _uiState.update {
                    it.copy(
                        scannedLocation = null,
                        unresolvedToken = null,
                        error = AppError.Validation(
                            field = "qr",
                            message = "That code was not recognised. You can still file the " +
                                "report without it.",
                        ),
                    )
                }
            }
        }
    }

    fun clearScannedLocation() =
        _uiState.update { it.copy(scannedLocation = null, unresolvedToken = null) }

    fun submitReport() {
        val state = _uiState.value
        val form = state.form

        if (!form.canSubmit) {
            _uiState.update {
                it.copy(
                    error = AppError.Validation(
                        field = "title",
                        message = "Add a short description of who or what is missing.",
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            // Best-effort. A report is never blocked on a fix: "last seen somewhere near
            // here" filed now beats a precise report filed twenty minutes later.
            val deviceLocation = locationProvider.currentFix().pointOrNull

            val details = ReportDetails(
                kind = form.kind,
                subjectType = form.subjectType,
                title = form.title,
                description = form.description,
                personName = form.personName,
                approximateAge = form.approximateAge.toIntOrNull(),
                gender = form.gender,
                clothingDescription = form.clothingDescription,
                physicalDescription = form.physicalDescription,
                language = form.language,
                condition = form.condition,
                additionalNotes = form.additionalNotes,
                guardianName = form.guardianName,
                guardianPhone = form.guardianPhone,
                qrLocationToken = state.scannedLocation?.token ?: state.unresolvedToken,
                qrLocationName = state.scannedLocation?.locationName,
                deviceLocation = deviceLocation,
                lastKnownLocation = state.scannedLocation?.point ?: deviceLocation,
                routeSegment = state.scannedLocation?.routeSegment,
                routeSequence = state.scannedLocation?.routeSequence,
                photoLocalPath = form.photoLocalPath,
            )

            when (val result = lostFoundRepository.report(details)) {
                is Outcome.Success -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isReportOpen = false,
                        form = ReportFormState(),
                        scannedLocation = null,
                        unresolvedToken = null,
                        justReportedClientId = result.data.clientId,
                        photoNotice = photoNoticeFor(result.data.faceMatchStatus),
                    )
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }

    /**
     * Volunteer-facing wording for a photo that cannot contribute a face signal.
     *
     * Never an error, and never a technical one. The report is saved either way; this only
     * tells the volunteer whether a better picture would help.
     */
    private fun photoNoticeFor(status: FaceMatchStatus): String? = when (status) {
        FaceMatchStatus.NO_FACE ->
            "No face was detected in the photo. The report was saved and will still be " +
                "matched on the other details."

        FaceMatchStatus.MULTIPLE_FACES ->
            "The photo shows more than one person. The report was saved — add a photo of " +
                "just this person if you can."

        FaceMatchStatus.INVALID_IMAGE ->
            "That photo could not be read. The report was saved without it."

        FaceMatchStatus.SERVICE_UNAVAILABLE ->
            "Photo matching is unavailable right now. The report was saved and will be " +
                "matched on the other details."

        else -> null
    }

    fun dismissConfirmation() =
        _uiState.update { it.copy(justReportedClientId = null, photoNotice = null) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
