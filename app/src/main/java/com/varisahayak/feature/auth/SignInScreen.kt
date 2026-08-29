package com.varisahayak.feature.auth

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.varisahayak.R
import com.varisahayak.core.common.AppError

@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    onNavigateToSignUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

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
            // Header / Logo Section
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary
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

            Spacer(modifier = Modifier.height(40.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
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
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
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
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

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
