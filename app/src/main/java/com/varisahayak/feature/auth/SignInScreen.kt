package com.varisahayak.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.common.AppError
import com.varisahayak.core.designsystem.component.LoadingDots

@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    onNavigateToSignUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    
    // Animation state for staggered entry
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Header / Logo Section
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.vari_logo),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.auth_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = stringResource(R.string.auth_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(800, 200)) + slideInVertically(tween(800, 200)) { 40 }
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
                            onClick = viewModel::signIn,
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
                                label = "signin_button_content"
                            ) { isLoading ->
                                if (isLoading) {
                                    LoadingDots(
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.auth_sign_in),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn() + slideInVertically { -20 },
                exit = fadeOut() + slideOutVertically { -20 }
            ) {
                uiState.error?.let { error ->
                    Column {
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
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(1000, 400))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have an account?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onNavigateToSignUp) {
                        Text(
                            text = "Sign Up",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
