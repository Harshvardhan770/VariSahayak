package com.varisahayak.core.common

/**
 * The four states every screen in this app must handle, per the Definition of Done:
 * loading, error, empty, and offline.
 *
 * [Offline] carries [data] because being offline never means having nothing to show — the
 * local database is the source of truth and keeps working.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<T>(val data: T) : UiState<T>

    data object Empty : UiState<Nothing>

    data class Offline<T>(val data: T?) : UiState<T>

    data class Error(val error: AppError) : UiState<Nothing>
}
