package com.varisahayak.feature.lostfound

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.core.common.getOrNull
import com.varisahayak.app.navigation.Destination
import com.varisahayak.core.location.LocationProvider
import com.varisahayak.core.media.PhotoCapture
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
 * Nothing here is mandatory, which is a product decision rather than an oversight: a parent
 * who reaches a volunteer at dusk with no photograph and half a description must still be
 * able to file something the matching engine can work with.
 *
 * Most of it is tapped rather than typed. The structured fields are not a cosmetic change —
 * `LostFoundMatchingEngine` compares gender and language by exact string equality, so while
 * they were free text "M", "male" and "boy" were three different genders and the signal
 * almost never fired. See [ReportOption].
 */
data class ReportFormState(
    val kind: LostFoundKind = LostFoundKind.LOST,
    val subjectType: LostFoundSubjectType = LostFoundSubjectType.PERSON,

    // --- the fast path: one photo and four taps ---
    val photoLocalPath: String? = null,
    val personName: String = "",
    val approximateAge: String = "",
    val gender: GenderOption? = null,
    val clothingColours: Set<ClothingColour> = emptySet(),
    val clothingDetail: String = "",
    val language: LanguageOption? = null,

    // Found side only: triage for whoever comes to help.
    val condition: ConditionOption? = null,

    // Lost side only: who to call. The single most valuable field for actually reuniting.
    val guardianPhone: String = "",

    // --- behind "more details", for when there is time ---
    val guardianName: String = "",
    val physicalDescription: String = "",
    val additionalNotes: String = "",
    val isExpanded: Boolean = false,
) {
    /** Age as the engine wants it: a number, or nothing. */
    val ageOrNull: Int? get() = approximateAge.toIntOrNull()

    /**
     * Clothing as one string, colours first.
     *
     * The engine compares clothing as a bag of words with Jaccard overlap, so the tapped
     * colours are what actually produce a match between two hurried descriptions. The free
     * text is what distinguishes one child in a yellow shirt from another.
     */
    fun clothingDescription(colourWords: List<String>): String =
        (colourWords + clothingDetail.trim()).filter { it.isNotBlank() }.joinToString(" ")

    /**
     * True once the report says *anything* identifying.
     *
     * Not a validation rule so much as a guard against an empty submission: a report with no
     * photo, no name, no age, no clothing and no description cannot be matched against
     * anything and would only add noise to the board.
     */
    val canSubmit: Boolean
        get() = photoLocalPath != null ||
            personName.isNotBlank() ||
            approximateAge.isNotBlank() ||
            gender != null ||
            clothingColours.isNotEmpty() ||
            clothingDetail.isNotBlank() ||
            physicalDescription.isNotBlank() ||
            additionalNotes.isNotBlank()
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
    /** True while the photograph is being sent for face processing. */
    val isProcessingPhoto: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LostFoundViewModel @Inject constructor(
    private val lostFoundRepository: LostFoundRepository,
    private val qrLocationRepository: QrLocationRepository,
    private val locationProvider: LocationProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * How this screen was opened.
     *
     * `Destination.LostAndFound` has always carried these three, and nothing read them: the
     * dashboard's Found Person action and the QR scanner's "report a found person" both
     * navigated here with `kind = "FOUND"` and a scanned help point, and both landed on the
     * board with no form open and the location thrown away. §7.15 asks for Found Person to
     * be a first-class action, and an action that opens a list is not one.
     */
    private val route = savedStateHandle.toRoute<Destination.LostAndFound>()

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

        // Attach the scanned sign before opening the form, so the volunteer sees the help
        // point they are standing at already filled in rather than appearing a moment later.
        // resolve() reads the local cache first, so this still works in a dead spot - the
        // scanner that sent us here has already cached it.
        route.qrLocationToken?.let(::attachScannedLocation)

        // Straight onto the right form. The board is still behind the dialog, so dismissing
        // it leaves the volunteer exactly where the old behaviour would have put them.
        route.kind?.let { openReport(LostFoundKind.fromWire(it)) }
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
                isProcessingPhoto = false,
            )
        }
    }

    /** Cancelling discards the photograph too; nothing else will ever reference the file. */
    fun closeReport() {
        PhotoCapture.discard(_uiState.value.form.photoLocalPath)
        _uiState.update {
            it.copy(isReportOpen = false, error = null, form = it.form.copy(photoLocalPath = null))
        }
    }

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
                        field = "form",
                        message = "Add a photo, or at least one detail about the person.",
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
                // Derived, not asked for. A title was the one mandatory field on this form,
                // and it was pure friction: everything a useful title contains is already
                // in the structured fields, so composing it here removes a typing step from
                // the critical path without losing anything from the board.
                title = deriveTitle(form),
                description = "",
                personName = form.personName,
                approximateAge = form.ageOrNull,
                gender = form.gender?.wireValue,
                clothingDescription = form.clothingDescription(colourWords(form)),
                physicalDescription = form.physicalDescription,
                language = form.language?.wireValue,
                condition = form.condition?.wireValue,
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
                is Outcome.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            isReportOpen = false,
                            form = ReportFormState(),
                            scannedLocation = null,
                            unresolvedToken = null,
                            justReportedClientId = result.data.clientId,
                            // Null while a photo is still being processed; the confirmation
                            // shows the generic "saved" line until a verdict arrives.
                            photoNotice = photoNoticeFor(result.data.faceMatchStatus),
                            isProcessingPhoto = result.data.photoLocalPath != null,
                        )
                    }

                    // After the report is saved and the dialog is closed, never before. The
                    // report is already safe on disk, so this can take as long as it takes
                    // and fail as badly as it likes without costing the volunteer anything.
                    if (result.data.photoLocalPath != null) {
                        processPhoto(result.data.clientId)
                    }
                }

                is Outcome.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }

    // --- photograph -----------------------------------------------------------------------

    /**
     * Attaches a captured or imported photograph.
     *
     * A replacement deletes the file it replaces. Without that, every retaken shot would
     * leave an orphan in private storage that nothing ever cleans up.
     */
    fun onPhotoCaptured(path: String) {
        val previous = _uiState.value.form.photoLocalPath
        if (previous != null && previous != path) PhotoCapture.discard(previous)
        updateForm { it.copy(photoLocalPath = path) }
    }

    /** Removes the photo and the file behind it. */
    fun clearPhoto() {
        PhotoCapture.discard(_uiState.value.form.photoLocalPath)
        updateForm { it.copy(photoLocalPath = null) }
    }

    /**
     * Sends the photograph for face processing and reports the verdict.
     *
     * Runs after the report is saved, never as part of saving it. A failure here changes a
     * status field and a line of wording; it can never cost a volunteer their report.
     */
    private fun processPhoto(clientId: String) {
        viewModelScope.launch {
            val status = lostFoundRepository.submitPhotoForMatching(clientId)
                .getOrNull() ?: FaceMatchStatus.SERVICE_UNAVAILABLE

            _uiState.update {
                // Only if the volunteer is still looking at the confirmation this belongs
                // to. They may already have moved on, and a notice about a report they have
                // stopped thinking about would be confusing rather than helpful.
                if (it.justReportedClientId != clientId) {
                    it.copy(isProcessingPhoto = false)
                } else {
                    it.copy(isProcessingPhoto = false, photoNotice = photoNoticeFor(status))
                }
            }
        }
    }

    // --- derived values -------------------------------------------------------------------

    /** Clothing colours as the words the matching engine tokenises. */
    private fun colourWords(form: ReportFormState): List<String> =
        ClothingColour.entries
            .filter { it in form.clothingColours }
            .map { it.wireValue }

    /**
     * Builds the board's headline for a report from what was actually filled in.
     *
     * Falls through progressively: a name if there is one, otherwise an age-and-gender
     * description, otherwise the clothing, otherwise a bare label. Every branch produces
     * something a volunteer scanning the list can tell apart from its neighbours, which is
     * all a title was ever for.
     */
    private fun deriveTitle(form: ReportFormState): String {
        form.personName.trim().takeIf { it.isNotBlank() }?.let { return it }

        val descriptor = listOfNotNull(
            form.ageOrNull?.let { "about $it" },
            form.gender?.wireValue,
        ).joinToString(" ")

        if (descriptor.isNotBlank()) {
            return descriptor.replaceFirstChar(Char::uppercase)
        }

        val clothing = form.clothingDescription(colourWords(form))
        if (clothing.isNotBlank()) return "Wearing $clothing"

        return when (form.kind) {
            LostFoundKind.LOST -> "Missing person"
            LostFoundKind.FOUND -> "Found person"
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
        _uiState.update {
            it.copy(justReportedClientId = null, photoNotice = null, isProcessingPhoto = false)
        }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 250L
    }
}
