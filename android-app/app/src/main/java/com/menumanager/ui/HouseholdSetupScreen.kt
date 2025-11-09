package com.menumanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdSetupScreen(
    state: HouseholdState,
    onCreateHousehold: () -> Unit,
    onJoinHousehold: (String) -> Unit,
    onDismissError: () -> Unit
) {
    var inviteCodeInput by remember { mutableStateOf("") }
    var showJoinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configura la famiglia") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is HouseholdState.Loading -> CircularProgressIndicator()
                is HouseholdState.NeedsSetup -> {
                    Text(
                        text = "Benvenuto!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Questo è il primo avvio. Scegli come procedere:",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onCreateHousehold,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crea una nuova famiglia")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unisciti con codice invito")
                    }
                }
                is HouseholdState.Creating -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Creazione famiglia in corso...")
                }
                is HouseholdState.Joining -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Mi sto unendo...")
                }
                is HouseholdState.Ready -> {
                    // Questo stato sarà gestito dal parent (MainActivity mostrerà l'app principale)
                    Text("Famiglia configurata!")
                }
                is HouseholdState.Error -> {
                    Text(
                        text = "Errore: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDismissError) {
                        Text("Riprova")
                    }
                }
            }
        }
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Inserisci codice invito") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Chiedi all'altra persona il codice a 6 caratteri.")
                    TextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it.uppercase().take(6) },
                        label = { Text("Codice (6 caratteri)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onJoinHousehold(inviteCodeInput)
                        showJoinDialog = false
                        inviteCodeInput = ""
                    },
                    enabled = inviteCodeInput.length == 6
                ) {
                    Text("Unisciti")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
fun HouseholdInfoCard(inviteCode: String?) {
    if (inviteCode != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Codice invito per l'altra persona:",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = inviteCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Condividi questo codice per far unire l'altro dispositivo.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
