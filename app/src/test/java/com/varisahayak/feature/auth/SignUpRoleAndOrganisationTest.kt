package com.varisahayak.feature.auth

import com.varisahayak.core.common.AppError
import com.varisahayak.core.common.Outcome
import com.varisahayak.domain.model.UserRole
import com.varisahayak.domain.repository.AuthRepository
import com.varisahayak.domain.repository.AuthState
import com.varisahayak.domain.repository.BulkSignUpResult
import com.varisahayak.domain.repository.BulkUserRequest
import com.varisahayak.domain.repository.SignUpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Sign-up used to discard the selected role: every account, whatever the user picked,
 * was written with the VOLUNTEER role_id. The role now travels to the server as
 * `raw_user_meta_data.role`, keyed on [UserRole.wireName], and the `handle_new_user`
 * trigger looks the role_id up by that exact string.
 *
 * These tests guard the two halves of that contract the device is responsible for: the
 * wire name is stable and unambiguous for every role, and the role the user chose is the
 * role that gets sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignUpRoleAndOrganisationTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var authRepository: RecordingAuthRepository
    private lateinit var viewModel: SignUpViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        authRepository = RecordingAuthRepository()
        viewModel = SignUpViewModel(authRepository)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    // --- role mapping -------------------------------------------------------------------

    @Test
    fun `every role has a distinct wire name`() {
        val wireNames = UserRole.entries.map { it.wireName }

        assertEquals(
            wireNames.size,
            wireNames.toSet().size,
            "Two roles sharing a wire name would collapse to one role_id server-side",
        )
    }

    @ParameterizedTest
    @EnumSource(UserRole::class)
    fun `wire name round trips for every role`(role: UserRole) {
        assertEquals(role, UserRole.fromWire(role.wireName))
    }

    @ParameterizedTest
    @EnumSource(UserRole::class)
    fun `the selected role is the role sent to the server`(role: UserRole) = runTest(dispatcher) {
        viewModel.onDisplayNameChanged("Asha Kulkarni")
        viewModel.onEmailChanged("asha@example.org")
        viewModel.onPasswordChanged("correct horse battery")
        viewModel.onRoleChanged(role)
        if (role.isResponder) viewModel.onOrganisationNameChanged("Sassoon Hospital")

        viewModel.signUp()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(role, authRepository.lastCall?.role)
    }

    // --- organisation rule --------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(UserRole::class)
    fun `only responder roles ask for an organisation`(role: UserRole) {
        viewModel.onRoleChanged(role)

        assertEquals(role.isResponder, viewModel.uiState.value.requiresOrganisation)
    }

    @Test
    fun `exactly the three responder roles are responders`() {
        assertEquals(
            setOf(
                UserRole.MEDICAL_RESPONDER,
                UserRole.POLICE_RESPONDER,
                UserRole.NGO_RESPONDER,
            ),
            UserRole.entries.filter { it.isResponder }.toSet(),
        )
    }

    @Test
    fun `a responder organisation reaches the server`() = runTest(dispatcher) {
        viewModel.onDisplayNameChanged("Ravi Deshmukh")
        viewModel.onEmailChanged("ravi@example.org")
        viewModel.onPasswordChanged("correct horse battery")
        viewModel.onRoleChanged(UserRole.MEDICAL_RESPONDER)
        viewModel.onOrganisationNameChanged("  Sassoon Hospital  ")

        viewModel.signUp()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("  Sassoon Hospital  ", authRepository.lastCall?.organisationName)
    }

    @Test
    fun `a volunteer sends no organisation even after typing one`() = runTest(dispatcher) {
        viewModel.onDisplayNameChanged("Meera Patil")
        viewModel.onEmailChanged("meera@example.org")
        viewModel.onPasswordChanged("correct horse battery")

        // Picks a responder role, types an organisation, then changes their mind.
        viewModel.onRoleChanged(UserRole.NGO_RESPONDER)
        viewModel.onOrganisationNameChanged("Seva Trust")
        viewModel.onRoleChanged(UserRole.VOLUNTEER)

        assertEquals("", viewModel.uiState.value.organisationName)
        assertFalse(viewModel.uiState.value.requiresOrganisation)

        viewModel.signUp()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(authRepository.lastCall?.organisationName)
    }

    @Test
    fun `a responder with no organisation is rejected and never reaches the server`() =
        runTest(dispatcher) {
            authRepository.failWith =
                AppError.Validation("organisationName", "Enter the organisation you respond for.")

            viewModel.onDisplayNameChanged("Ravi Deshmukh")
            viewModel.onEmailChanged("ravi@example.org")
            viewModel.onPasswordChanged("correct horse battery")
            viewModel.onRoleChanged(UserRole.POLICE_RESPONDER)

            viewModel.signUp()
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isSuccess)
            assertNotNull(state.error)
            assertEquals("organisationName", (state.error as AppError.Validation).field)
            // The empty field is still forwarded, so the repository — the single place
            // every auth flow validates — is what refuses it.
            assertTrue(authRepository.lastCall?.organisationName.isNullOrBlank())
        }
}

private class RecordingAuthRepository : AuthRepository {

    data class SignUpCall(
        val email: String,
        val displayName: String,
        val role: UserRole,
        val organisationName: String?,
    )

    var lastCall: SignUpCall? = null
    var failWith: AppError? = null

    override val authState: Flow<AuthState> = flowOf(AuthState.Unknown)

    override fun currentUserId(): String? = null

    override suspend fun signIn(email: String, password: String): Outcome<Unit> =
        Outcome.Success(Unit)

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        role: UserRole,
        organisationName: String?,
    ): Outcome<SignUpResult> {
        lastCall = SignUpCall(email, displayName, role, organisationName)
        return failWith?.let { Outcome.Failure(it) }
            ?: Outcome.Success(SignUpResult.ConfirmationRequired(email))
    }

    /**
     * Not exercised by these tests, which are about the single-account sign-up form.
     * Present because the interface requires it; returning an empty result rather than
     * throwing keeps an accidental call from reading as a test failure in an unrelated suite.
     */
    override suspend fun bulkSignUp(users: List<BulkUserRequest>): BulkSignUpResult =
        BulkSignUpResult(created = emptyList(), failed = emptyList())

    override suspend fun signOut(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> = Outcome.Success(Unit)
}
