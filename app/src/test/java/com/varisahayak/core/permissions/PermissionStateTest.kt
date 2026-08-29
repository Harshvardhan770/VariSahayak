package com.varisahayak.core.permissions

import android.Manifest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The permanent-denial rule is easy to get subtly wrong, and getting it wrong either traps
 * the user on a button that silently does nothing or sends a first-time user straight to
 * the system settings screen.
 */
class PermissionStateTest {

    private val locationPermissions = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @Test
    @DisplayName("a fresh install is not permanently denied even though no rationale is available")
    fun `not permanently denied before first request`() {
        val state = PermissionState(
            granted = emptySet(),
            shouldShowRationale = false,
            hasBeenRequested = false,
        )

        assertFalse(state.isPermanentlyDenied(locationPermissions))
    }

    @Test
    @DisplayName("denied once with a rationale available is a normal denial, not permanent")
    fun `denied with rationale is not permanent`() {
        val state = PermissionState(
            granted = emptySet(),
            shouldShowRationale = true,
            hasBeenRequested = true,
        )

        assertFalse(state.isPermanentlyDenied(locationPermissions))
    }

    @Test
    @DisplayName("requested, denied, and no rationale offered means don't-ask-again")
    fun `denied without rationale after request is permanent`() {
        val state = PermissionState(
            granted = emptySet(),
            shouldShowRationale = false,
            hasBeenRequested = true,
        )

        assertTrue(state.isPermanentlyDenied(locationPermissions))
    }

    @Test
    @DisplayName("coarse-only counts as granted, so nothing is reported as denied")
    fun `coarse grant is not denied`() {
        val state = PermissionState(
            granted = setOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            shouldShowRationale = false,
            hasBeenRequested = true,
        )

        assertFalse(state.isPermanentlyDenied(locationPermissions))
        assertTrue(state.isAnyGranted)
        assertFalse(state.isGranted(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test
    fun `fine grant reports granted`() {
        val state = PermissionState(
            granted = setOf(Manifest.permission.ACCESS_FINE_LOCATION),
            shouldShowRationale = false,
            hasBeenRequested = true,
        )

        assertTrue(state.isGranted(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(state.isAnyGranted)
    }
}
