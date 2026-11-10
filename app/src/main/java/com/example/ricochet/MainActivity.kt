package com.example.ricochet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ForwarderRepository.getInstance(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = dynamicLightColorSchemeIfAvailable()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RicochetApp(repository = repository)
                }
            }
        }
    }
}

@Composable
private fun dynamicLightColorSchemeIfAvailable(): ColorScheme {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        lightColorScheme()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RicochetApp(repository: ForwarderRepository) {
    val context = LocalContext.current
    val masterEnabled by repository.masterEnabled.collectAsState()
    val rules by repository.rules.collectAsState()
    val history by repository.history.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingRule by remember { mutableStateOf<ForwardingRule?>(null) }
    var showRuleDialog by remember { mutableStateOf(false) }

    var hasPermissions by remember { mutableStateOf(checkRequiredPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermissions = checkRequiredPermissions(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ricochet") },
                actions = {
                    if (selectedTab == 1 && history.isNotEmpty()) {
                        IconButton(onClick = { repository.clearHistory() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear History"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Rule, contentDescription = null) },
                    label = { Text("Rules (${rules.size})") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("History (${history.size})") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingRule = null
                        showRuleDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Rule") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // 1. Master Active / Pause Toggle Card
            MasterControlCard(
                masterEnabled = masterEnabled,
                onToggle = { repository.setMasterEnabled(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Permission Banner (shown until RECEIVE_SMS & SEND_SMS are granted)
            if (!hasPermissions) {
                PermissionWarningCard(
                    onRequestPermissions = {
                        permissionLauncher.launch(requiredPermissionsArray())
                    },
                    onOpenAppSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 3. Main Tab Content
            when (selectedTab) {
                0 -> RulesTabContent(
                    rules = rules,
                    onToggleRule = { ruleId, enabled -> repository.toggleRule(ruleId, enabled) },
                    onEditRule = { rule ->
                        editingRule = rule
                        showRuleDialog = true
                    },
                    onDeleteRule = { ruleId -> repository.deleteRule(ruleId) }
                )
                1 -> HistoryTabContent(history = history)
            }
        }
    }

    if (showRuleDialog) {
        RuleEditorDialog(
            initialRule = editingRule,
            onDismiss = { showRuleDialog = false },
            onSave = { savedRule ->
                repository.saveRule(savedRule)
                showRuleDialog = false
            }
        )
    }
}

@Composable
private fun MasterControlCard(
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (masterEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (masterEnabled) "Ricochet Active" else "Ricochet Paused",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (masterEnabled) {
                        "Incoming SMS messages matching active rules will be forwarded."
                    } else {
                        "Master switch is paused. No SMS messages will be forwarded."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = masterEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun PermissionWarningCard(
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SMS Permissions Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Grant Receive & Send SMS permissions so Ricochet can read incoming login alerts and forward matching texts. On Android 13+, if sideloaded, tap App Settings -> 3-dots menu -> Allow restricted settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestPermissions) {
                    Text("Grant Permissions")
                }
                OutlinedButton(onClick = onOpenAppSettings) {
                    Text("App Settings")
                }
            }
        }
    }
}

@Composable
private fun RulesTabContent(
    rules: List<ForwardingRule>,
    onToggleRule: (String, Boolean) -> Unit,
    onEditRule: (ForwardingRule) -> Unit,
    onDeleteRule: (String) -> Unit
) {
    if (rules.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No Ricochet rules yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Tap 'Add Rule' to forward SMS alerts by sender or keyword regex.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            items(rules, key = { it.id }) { rule ->
                RuleItemCard(
                    rule = rule,
                    onToggle = { onToggleRule(rule.id, it) },
                    onEdit = { onEditRule(rule) },
                    onDelete = { onDeleteRule(rule.id) }
                )
            }
        }
    }
}

@Composable
private fun RuleItemCard(
    rule: ForwardingRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Forward to: ${rule.recipientNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (rule.senderRegex.isNotBlank()) {
                PatternBadge(label = "Sender Regex", pattern = rule.senderRegex)
            }
            if (rule.senderRegex.isNotBlank() && rule.messageRegex.isNotBlank()) {
                Text(
                    text = "AND",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (rule.messageRegex.isNotBlank()) {
                PatternBadge(label = "Message Regex", pattern = rule.messageRegex)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun PatternBadge(label: String, pattern: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = pattern,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HistoryTabContent(history: List<ForwardingLogEntry>) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()) }

    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No forwarded messages yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(history, key = { it.id }) { item ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.ruleName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.status.startsWith("Sent")) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        Text(
                            text = "${dateFormat.format(Date(item.timestamp))} • From ${item.originalSender} -> ${item.recipientNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.forwardedBody,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    initialRule: ForwardingRule?,
    onDismiss: () -> Unit,
    onSave: (ForwardingRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var recipient by remember { mutableStateOf(initialRule?.recipientNumber ?: "") }
    var senderRegex by remember { mutableStateOf(initialRule?.senderRegex ?: "") }
    var messageRegex by remember { mutableStateOf(initialRule?.messageRegex ?: "") }

    // Optional live test fields inside the dialog
    var testSender by remember { mutableStateOf("") }
    var testMessage by remember { mutableStateOf("") }

    val senderRegexError = SmsForwarderEngine.validateRegex(senderRegex)
    val messageRegexError = SmsForwarderEngine.validateRegex(messageRegex)
    val hasAtLeastOneCondition = senderRegex.isNotBlank() || messageRegex.isNotBlank()
    val canSave = name.isNotBlank() &&
        recipient.isNotBlank() &&
        hasAtLeastOneCondition &&
        senderRegexError == null &&
        messageRegexError == null

    val previewRule = ForwardingRule(
        id = initialRule?.id ?: "preview",
        name = name.ifBlank { "Preview" },
        recipientNumber = recipient,
        senderRegex = senderRegex,
        messageRegex = messageRegex
    )
    val testMatched = if (testSender.isNotBlank() || testMessage.isNotBlank()) {
        SmsForwarderEngine.matchesRule(previewRule, testSender, testMessage)
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule == null) "New Ricochet Rule" else "Edit Ricochet Rule") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name (e.g. Facebook Login)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient Phone Number") },
                    placeholder = { Text("+919876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = senderRegex,
                    onValueChange = { senderRegex = it },
                    label = { Text("Sender Regex (optional)") },
                    placeholder = { Text(".*(Facebook|FB).*") },
                    isError = senderRegexError != null,
                    supportingText = {
                        Text(senderRegexError ?: "Case-insensitive regex matched against sender ID/number")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = messageRegex,
                    onValueChange = { messageRegex = it },
                    label = { Text("Keyword / Message Regex (optional)") },
                    placeholder = { Text(".*(login alert|new sign-in).*") },
                    isError = messageRegexError != null,
                    supportingText = {
                        Text(
                            messageRegexError
                                ?: "If both Sender & Message regex are filled, BOTH must match (AND)"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Quick Regex Tester (Optional)",
                    style = MaterialTheme.typography.labelLarge
                )
                OutlinedTextField(
                    value = testSender,
                    onValueChange = { testSender = it },
                    label = { Text("Sample Sender (e.g. VM-FACEBK)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = testMessage,
                    onValueChange = { testMessage = it },
                    label = { Text("Sample SMS Body") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (testMatched != null) {
                    Text(
                        text = if (testMatched) "✓ Sample matches this rule!" else "✗ Sample does not match",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (testMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ForwardingRule(
                            id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            recipientNumber = recipient.trim(),
                            senderRegex = senderRegex.trim(),
                            messageRegex = messageRegex.trim(),
                            isEnabled = initialRule?.isEnabled ?: true
                        )
                    )
                },
                enabled = canSave
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun requiredPermissionsArray(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS
        )
    }
}

private fun checkRequiredPermissions(context: Context): Boolean {
    val smsPermissions = listOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS
    )
    return smsPermissions.all { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}
