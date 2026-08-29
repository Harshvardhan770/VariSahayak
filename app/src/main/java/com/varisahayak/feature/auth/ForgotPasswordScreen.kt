package com.varisahayak.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import com.varisahayak.core.common.AppError
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.core.designsystem.component.VariSecondaryButton

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        Text(
            text = stringResource(R.string.auth_reset_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.auth_reset_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            enabled = !uiState.isSent,
            isError = uiState.error is AppError.Validation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.error?.let { error ->
            Text(
                text = when (error) {
                    is AppError.Validation -> error.message
                    is AppError.Offline -> stringResource(R.string.auth_error_offline)
                    else -> stringResource(R.string.state_error)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (uiState.isSent) {
            Text(
                text = stringResource(R.string.auth_reset_sent),
                style = MaterialTheme.typography.bodyMedium,
                color = VariTheme.colors.success,
            )
        } else {
            VariPrimaryButton(
                text = stringResource(R.string.auth_reset_send),
                onClick = viewModel::send,
                enabled = !uiState.isSending,
            )
        }

        VariSecondaryButton(
            text = stringResource(R.string.action_back),
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
