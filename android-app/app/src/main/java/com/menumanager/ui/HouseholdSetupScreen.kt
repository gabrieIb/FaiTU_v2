package com.menumanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun HouseholdSetupScreen(
	state: HouseholdState,
	onCreateHousehold: () -> Unit,
	onJoinHousehold: (String) -> Unit,
	onDismissError: () -> Unit
) {
	var inviteCode by rememberSaveable { mutableStateOf("") }
	val isProcessing = state is HouseholdState.Creating || state is HouseholdState.Joining
	val isLoading = state is HouseholdState.Loading
	val errorMessage = (state as? HouseholdState.Error)?.message

	Surface(modifier = Modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 24.dp, vertical = 32.dp),
			verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				text = "Benvenuto",
				style = MaterialTheme.typography.headlineMedium,
				fontWeight = FontWeight.SemiBold
			)
			Text(
				text = "Crea una nuova famiglia oppure inserisci un codice invito per unirti a quella esistente.",
				style = MaterialTheme.typography.bodyMedium
			)

			if (isLoading) {
				CircularProgressIndicator()
			} else {
				Button(
					onClick = onCreateHousehold,
					enabled = !isProcessing,
					modifier = Modifier.fillMaxWidth()
				) {
					Text("Crea nuova famiglia")
				}

				Column(
					modifier = Modifier.fillMaxWidth(),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					TextField(
						value = inviteCode,
						onValueChange = { inviteCode = it.uppercase(Locale.ROOT).take(8) },
						label = { Text("Codice invito") },
						modifier = Modifier.fillMaxWidth(),
						enabled = !isProcessing
					)
					Button(
						onClick = { onJoinHousehold(inviteCode) },
						enabled = inviteCode.length >= 4 && !isProcessing,
						modifier = Modifier.fillMaxWidth()
					) {
						Text("Unisciti")
					}
				}
			}

			if (errorMessage != null) {
				Column(
					modifier = Modifier.fillMaxWidth(),
					verticalArrangement = Arrangement.spacedBy(8.dp),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
					TextButton(onClick = onDismissError) {
						Text("OK")
					}
				}
			}

			Spacer(modifier = Modifier.height(12.dp))

			if (isProcessing) {
				CircularProgressIndicator()
			}
		}
	}
}
