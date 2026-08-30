package com.varisahayak.feature.auth

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.core.designsystem.component.VariSecondaryButton
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkRegistrationScreen(
    viewModel: BulkRegistrationViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val name = cursor?.use { c ->
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                c.moveToFirst()
                c.getString(nameIndex)
            } ?: "unknown_file.xlsx"
            viewModel.onFileSelected(it, name)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bulk Registration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            if (uiState.result != null) {
                ImportResultView(uiState.result!!, onDone = viewModel::clear)
            } else if (uiState.parsedUsers.isNotEmpty()) {
                ImportPreviewView(
                    users = uiState.parsedUsers,
                    isRegistering = uiState.isRegistering,
                    onConfirm = viewModel::startImport,
                    onCancel = viewModel::clear
                )
            } else {
                UploadPlaceholder(
                    isParsing = uiState.isParsing,
                    onPickFile = { launcher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }
                )
            }

            uiState.error?.let {
                Text(
                    text = it.toString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun UploadPlaceholder(
    isParsing: Boolean,
    onPickFile: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
    ) {
        Icon(
            imageVector = Icons.Default.FileUpload,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Upload Responder Sheet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Supports .xlsx files with email, full_name, role, organisation, and phone columns.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(Modifier.height(16.dp))
        
        if (isParsing) {
            CircularProgressIndicator()
        } else {
            VariPrimaryButton(
                text = "Select Excel File",
                onClick = onPickFile,
                icon = Icons.Default.FileOpen
            )
            
            TextButton(
                onClick = { downloadSample(context) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download Sample CSV")
            }
        }
    }
}

private fun downloadSample(context: android.content.Context) {
    val sampleContent = "email,full_name,role,organisation,phone\n" +
            "rajesh.pawar@police.gov.in,Rajesh Pawar,POLICE_RESPONDER,Pune City Police,+919876543210\n" +
            "amit.kulkarni@police.gov.in,Amit Kulkarni,POLICE_RESPONDER,Pune City Police,+919876543211"
    
    val file = File(context.cacheDir, "responder_sample.csv")
    FileOutputStream(file).use { it.write(sampleContent.toByteArray()) }
    
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Save sample file"))
}

@Composable
private fun ImportPreviewView(
    users: List<com.varisahayak.domain.repository.BulkUserRequest>,
    isRegistering: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Dimens.SpaceMd)) {
            Text(
                text = "Ready to Import",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Found ${users.size} users in the file.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(Modifier.height(Dimens.SpaceMd))
            
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)
            ) {
                items(users) { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(user.role.name, style = MaterialTheme.typography.labelSmall)
                    }
                    HorizontalDivider(alpha = 0.1f)
                }
            }
            
            Spacer(Modifier.height(Dimens.SpaceLg))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)
            ) {
                VariSecondaryButton(
                    text = "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                )
                VariPrimaryButton(
                    text = "Start Import",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = !isRegistering
                )
            }
        }
    }
}

@Composable
private fun ImportResultView(
    result: com.varisahayak.domain.repository.BulkSignUpResult,
    onDone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.failed.isEmpty()) VariTheme.colors.success.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceMd)) {
            Text(
                text = "Import Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(Dimens.SpaceSm))
            
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceLg)) {
                ResultStat(label = "Created", value = result.created.size.toString(), color = VariTheme.colors.success)
                ResultStat(label = "Failed", value = result.failed.size.toString(), color = MaterialTheme.colorScheme.error)
            }

            if (result.failed.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.SpaceMd))
                Text("Errors:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(result.failed) { failure ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Row ${failure.request.rowNumber}: ${failure.request.displayName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(failure.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(Dimens.SpaceLg))
            
            VariPrimaryButton(
                text = "Finish",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, alpha: Float = 0.2f) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    )
}
