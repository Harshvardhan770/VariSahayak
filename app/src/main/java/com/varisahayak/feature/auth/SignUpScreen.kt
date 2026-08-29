package com.varisahayak.feature.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.common.AppError
import com.varisahayak.core.designsystem.component.LoadingDots
import com.varisahayak.core.designsystem.component.LogoLoading
import com.varisahayak.domain.model.UserRole
import kotlinx.coroutines.delay

/** Matches the field name AuthRepositoryImpl tags its organisation validation error with. */
private const val FIELD_ORGANISATION = "organisationName"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel,
    onNavigateBack: () -> Unit,
    onSignUpSuccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Initial logo buffering state
    var isBuffering by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(800) // Short buffering time as requested
        isBuffering = false
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onSignUpSuccess()
        }
    }

    AnimatedContent(
        targetState = isBuffering,
        transitionSpec = {
            fadeIn(tween(400)) togetherWith fadeOut(tween(400))
        },
        label = "signup_buffering"
    ) { buffering ->
        if (buffering) {
            LogoLoading()
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.auth_sign_up),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OutlinedTextField(
                                    value = uiState.displayName,
                                    onValueChange = viewModel::onDisplayNameChanged,
                                    label = { Text(stringResource(R.string.auth_display_name)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = !uiState.isLoading,
                                    leadingIcon = {
                                        Icon(Icons.Default.Badge, contentDescription = null)
                                    },
                                    shape = MaterialTheme.shapes.medium
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                var expanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = !expanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = when (uiState.selectedRole) {
                                            UserRole.VOLUNTEER -> stringResource(R.string.role_volunteer)
                                            UserRole.MEDICAL_RESPONDER -> stringResource(R.string.role_medical)
                                            UserRole.POLICE_RESPONDER -> stringResource(R.string.role_police)
                                            UserRole.NGO_RESPONDER -> stringResource(R.string.role_ngo)
                                            UserRole.ORGANISER -> stringResource(R.string.role_organiser)
                                            UserRole.ADMINISTRATOR -> stringResource(R.string.role_admin)
                                        },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(R.string.profile_title)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Work, contentDescription = null)
                                        },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                                            .fillMaxWidth(),
                                        enabled = !uiState.isLoading,
                                        shape = MaterialTheme.shapes.medium
                                    )

                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        UserRole.entries.forEach { role ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        when (role) {
                                                            UserRole.VOLUNTEER -> stringResource(R.string.role_volunteer)
                                                            UserRole.MEDICAL_RESPONDER -> stringResource(R.string.role_medical)
                                                            UserRole.POLICE_RESPONDER -> stringResource(R.string.role_police)
                                                            UserRole.NGO_RESPONDER -> stringResource(R.string.role_ngo)
                                                            UserRole.ORGANISER -> stringResource(R.string.role_organiser)
                                                            UserRole.ADMINISTRATOR -> stringResource(R.string.role_admin)
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.onRoleChanged(role)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Responders only. A responder with no organisation cannot be
                                // routed to, so the field appears — and is required — the moment
                                // a responder role is picked, and disappears again if it is not.
                                if (uiState.requiresOrganisation) {
                                    Spacer(modifier = Modifier.height(16.dp))

                                    OutlinedTextField(
                                        value = uiState.organisationName,
                                        onValueChange = viewModel::onOrganisationNameChanged,
                                        label = { Text(stringResource(R.string.auth_organisation_name)) },
                                        supportingText = {
                                            Text(stringResource(R.string.auth_organisation_helper))
                                        },
                                        isError = (uiState.error as? AppError.Validation)
                                            ?.field == FIELD_ORGANISATION,
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !uiState.isLoading,
                                        leadingIcon = {
                                            Icon(Icons.Default.Business, contentDescription = null)
                                        },
                                        shape = MaterialTheme.shapes.medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = uiState.email,
                                    onValueChange = viewModel::onEmailChanged,
                                    label = { Text(stringResource(R.string.auth_email)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = !uiState.isLoading,
                                    leadingIcon = {
                                        Icon(Icons.Default.Email, contentDescription = null)
                                    },
                                    shape = MaterialTheme.shapes.medium
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = uiState.password,
                                    onValueChange = viewModel::onPasswordChanged,
                                    label = { Text(stringResource(R.string.auth_password)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    enabled = !uiState.isLoading,
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null)
                                    },
                                    shape = MaterialTheme.shapes.medium
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                Button(
                                    onClick = viewModel::signUp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    enabled = !uiState.isLoading,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    AnimatedContent(
                                        targetState = uiState.isLoading,
                                        transitionSpec = {
                                            (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 })
                                                .togetherWith(fadeOut(tween(300)))
                                        },
                                        label = "signup_button_content"
                                    ) { isLoading ->
                                        if (isLoading) {
                                            LoadingDots(
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Text(
                                                stringResource(R.string.auth_sign_up),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        uiState.error?.let { error ->
                            Spacer(modifier = Modifier.height(16.dp))
                            val errorMessage = when (error) {
                                is AppError.Validation -> error.message
                                is AppError.Unauthorised -> stringResource(R.string.auth_error_invalid_credentials)
                                is AppError.Offline -> stringResource(R.string.auth_error_offline)
                                else -> error.cause?.message ?: stringResource(R.string.state_error)
                            }
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
