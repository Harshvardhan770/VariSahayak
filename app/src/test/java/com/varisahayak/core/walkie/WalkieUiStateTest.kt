package com.varisahayak.core.walkie

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The radio's state rules.
 *
 * Two of these encode decisions that are easy to "fix" back into bugs, so they are pinned
 * here rather than left to a comment:
 *
 *  - Mics overlap. Hearing somebody must never disable push-to-talk. The obvious-looking
 *    guard (`floor !is Receiving`) would mean a volunteer standing over a collapsed pilgrim
 *    could not interrupt a routine transmission.
 *  - Push-to-talk is enabled by the transport and by nothing else. Any path that lets the
 *    button go live while the radio is not connected hands somebody a control that swallows
 *    speech, which is the single worst thing this screen can do.
 */
class WalkieUiStateTest {

    private val connected = WalkieUiState(
        channel = WalkieChannels.DEFAULT,
        availableChannels = WalkieChannels.ALL,
        connection = WalkieConnection.Connected,
        isSimulated = false,
    )

    // -----------------------------------------------------------------------------------
    // Floor derivation
    // -----------------------------------------------------------------------------------

    @Test
    fun `idle when nobody is talking`() {
        assertEquals(WalkieFloor.Idle, connected.floor)
        assertFalse(connected.isActive)
    }

    @Test
    fun `receiving carries every speaker, in order`() {
        val state = connected.copy(speakers = listOf("Amit", "Priya", "Rahul"))

        assertEquals(WalkieFloor.Receiving(listOf("Amit", "Priya", "Rahul")), state.floor)
        assertTrue(state.isReceiving)
        assertTrue(state.isActive)
    }

    @Test
    fun `own mic outranks the people you can hear`() {
        // Both are true at once under overlap. The UI shows your own state first: being
        // wrong about whether your mic is open is the costlier mistake.
        val state = connected.copy(isMicOpen = true, speakers = listOf("Amit"))

        assertEquals(WalkieFloor.Transmitting, state.floor)
        assertTrue(state.isTransmitting)
        assertFalse(state.isReceiving)
        // The speakers are still carried, so the widget can name who is being talked over.
        assertEquals(listOf("Amit"), state.speakers)
    }

    // -----------------------------------------------------------------------------------
    // Transmit permission
    // -----------------------------------------------------------------------------------

    @Test
    fun `hearing someone does not take the button away`() {
        val state = connected.copy(speakers = listOf("Amit", "Priya"))

        assertTrue(state.canTransmit)
    }

    @Test
    fun `already transmitting stays transmittable`() {
        assertTrue(connected.copy(isMicOpen = true).canTransmit)
    }

    @Test
    fun `no transport means no push to talk`() {
        for (connection in listOf(
            WalkieConnection.NotConfigured,
            WalkieConnection.Connecting,
            WalkieConnection.Disconnected,
        )) {
            assertFalse(
                connected.copy(connection = connection).canTransmit,
                "canTransmit must be false while $connection",
            )
        }
    }

    // -----------------------------------------------------------------------------------
    // Channel roster
    // -----------------------------------------------------------------------------------

    @Test
    fun `all three channels are offered and comm-1 is the default`() {
        // These ids are LiveKit room names and are also the allowlist in the
        // livekit-token edge function. Changing one without the other does not fail
        // loudly — it mints no token, or mints one for a room nobody else is in.
        assertEquals(listOf("comm-1", "medical", "emergency"), WalkieChannels.ALL.map { it.id })
        assertEquals("comm-1", WalkieChannels.DEFAULT.id)
        assertEquals("Comm 1", WalkieChannels.DEFAULT.name)
        assertTrue(WalkieChannels.ALL.first { it.id == "emergency" }.isEmergency)
    }

    @Test
    fun `unknown channel ids resolve to nothing rather than to a default`() {
        // join() must refuse an id it does not know instead of quietly putting the
        // volunteer on Comm 1: two devices that disagree about which room they are in
        // hear silence and have no way to tell that is what happened.
        assertEquals(null, WalkieChannels.byId("dispatch"))
        // The Communication screen's chat roster, which is a different list entirely.
        // It must not resolve here — it used to be passed straight to join().
        assertEquals(null, WalkieChannels.byId("all_hands"))
        assertEquals(null, WalkieChannels.byId("route-main"))
        assertEquals(WalkieChannels.DEFAULT, WalkieChannels.byId("comm-1"))
    }
}
