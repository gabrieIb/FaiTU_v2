package com.menumanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.menumanager.data.Loadable
import com.menumanager.data.model.ApiState
import com.menumanager.data.model.MealIngredient
import com.menumanager.data.model.MealProposal
import com.menumanager.data.model.MealStatus
import com.menumanager.data.model.ShoppingEntry
import com.menumanager.ui.MenuViewModel
import com.menumanager.ui.MenuViewModelFactory
import com.menumanager.ui.HouseholdViewModel
import com.menumanager.ui.HouseholdViewModelFactory
import com.menumanager.ui.HouseholdSetupScreen
import com.menumanager.ui.HouseholdState
import com.menumanager.ui.theme.MenuManagerTheme
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MenuViewModel by viewModels {
        val app = application as MenuManagerApp
        MenuViewModelFactory(app.container.menuRepository)
    }

    private val householdViewModel: HouseholdViewModel by viewModels {
        val app = application as MenuManagerApp
        HouseholdViewModelFactory(app.container.householdRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MenuManagerTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MenuAppRoot(viewModel, householdViewModel)
                }
            }
        }
    }
}

@Composable
fun MenuAppRoot(menuViewModel: MenuViewModel, householdViewModel: HouseholdViewModel) {
    val householdState by householdViewModel.state.collectAsStateWithLifecycle()

    when (householdState) {
        is HouseholdState.Loading,
        is HouseholdState.NeedsSetup,
        is HouseholdState.Creating,
        is HouseholdState.Joining,
        is HouseholdState.Error -> {
            HouseholdSetupScreen(
                state = householdState,
                onCreateHousehold = { householdViewModel.createHousehold() },
                onJoinHousehold = { code -> householdViewModel.joinHousehold(code) },
                onDismissError = { householdViewModel.dismissError() }
            )
        }
        is HouseholdState.Ready -> {
            val readyState = householdState as HouseholdState.Ready
            MenuApp(
                viewModel = menuViewModel,
                householdViewModel = householdViewModel,
                householdId = readyState.householdId,
                householdInviteCode = readyState.inviteCode
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MenuApp(
    viewModel: MenuViewModel,
    householdViewModel: HouseholdViewModel,
    householdId: String,
    householdInviteCode: String? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showProposalWizard by remember { mutableStateOf(false) }
    var showShoppingDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    fun emitStatus(message: String, isError: Boolean = false, triggerHaptic: Boolean = false) {
        statusMessage = message
        statusIsError = isError
        if (triggerHaptic) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun buildResultHandler(
        successMessage: String,
        onSuccess: (() -> Unit)? = null,
        triggerHaptic: Boolean = true
    ): (Throwable?) -> Unit = { error ->
        val message = error?.message?.let { "Errore: $it" } ?: successMessage
        val shouldHaptic = triggerHaptic || error != null
        emitStatus(message, isError = error != null, triggerHaptic = shouldHaptic)
        if (error == null) {
            onSuccess?.invoke()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(2500)
            statusMessage = null
            statusIsError = false
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(text = "Fai tu!") },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(imageVector = Icons.Filled.Settings, contentDescription = "Impostazioni")
                        }
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = "Cancella tutto")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    Icons.Outlined.Fastfood to "Menu",
                    Icons.AutoMirrored.Outlined.ListAlt to "Spesa"
                )
                tabs.forEachIndexed { index, (icon, label) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(label) },
                        icon = { Icon(imageVector = icon, contentDescription = label) }
                    )
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                statusMessage?.let { StatusIndicator(message = it, isError = statusIsError) }
                when (selectedTab) {
                    0 -> ExtendedFloatingActionButton(
                        onClick = { showProposalWizard = true },
                        icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Nuova proposta") },
                        text = { Text("Nuova proposta") }
                    )
                    else -> FloatingActionButton(onClick = { showShoppingDialog = true }) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "Aggiungi alla spesa")
                    }
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> MenuTab(
                padding = padding,
                state = state,
                onUpdateProposalStatus = { proposal, status ->
                    val successMessage = if (status == MealStatus.Cooked) "Segnato come cucinato" else "Segnato in attesa"
                    viewModel.updateProposalStatus(
                        proposal = proposal,
                        status = status,
                        onResult = buildResultHandler(
                            successMessage = successMessage,
                            triggerHaptic = false
                        )
                    )
                },
                onCreateProposal = { draft, onSuccess ->
                    viewModel.createProposal(
                        mealSlot = draft.mealSlot,
                        title = draft.title,
                        notes = draft.notes,
                        onResult = buildResultHandler("Proposta salvata", onSuccess)
                    )
                },
                onUpdateProposal = { proposal, draft, onSuccess ->
                    viewModel.updateProposal(
                        existing = proposal,
                        mealSlot = draft.mealSlot,
                        title = draft.title,
                        notes = draft.notes,
                        onResult = buildResultHandler("Proposta aggiornata", onSuccess)
                    )
                },
                onDeleteProposal = { proposal ->
                    viewModel.deleteProposal(
                        proposalId = proposal.proposalId,
                        onResult = buildResultHandler("Proposta rimossa")
                    )
                },
                onCreateIngredient = { proposalId, draft, onSuccess ->
                    viewModel.createIngredient(
                        proposalId = proposalId,
                        name = draft.name,
                        needToBuy = draft.needToBuy,
                        onResult = buildResultHandler("Ingrediente aggiunto", onSuccess)
                    )
                },
                onUpdateIngredient = { ingredient, draft, onSuccess ->
                    viewModel.updateIngredient(
                        ingredient = ingredient,
                        name = draft.name,
                        needToBuy = draft.needToBuy,
                        onResult = buildResultHandler("Ingrediente aggiornato", onSuccess)
                    )
                },
                onToggleIngredient = { ingredient, needToBuy ->
                    viewModel.toggleIngredientNeedToBuy(
                        ingredient = ingredient,
                        needToBuy = needToBuy,
                        onResult = buildResultHandler(if (needToBuy) "Segnato da comprare" else "Segnato come disponibile")
                    )
                },
                onDeleteIngredient = { ingredient ->
                    viewModel.deleteIngredient(
                        ingredientId = ingredient.ingredientId,
                        onResult = buildResultHandler("Ingrediente rimosso")
                    )
                }
            )
            else -> ShoppingTab(
                padding = padding,
                state = state,
                onCompleteGroup = { entries ->
                    val message = if (entries.all { it.isManual }) "Elemento rimosso" else "Segnato come acquistato"
                    viewModel.completeShoppingEntries(
                        entries = entries,
                        onResult = buildResultHandler(message)
                    )
                },
                onDeleteGroup = { entries ->
                    val message = if (entries.all { it.isManual }) "Voce rimossa" else "Ingrediente eliminato"
                    viewModel.deleteShoppingEntries(
                        entries = entries,
                        onResult = buildResultHandler(message)
                    )
                }
            )
        }
    }

    if (showSettingsDialog) {
        HouseholdSettingsDialog(
            currentHouseholdId = householdId,
            inviteCode = householdInviteCode,
            onDismiss = { showSettingsDialog = false },
            onSave = { newId, onComplete ->
                val handler = buildResultHandler("Famiglia aggiornata", onSuccess = {
                    showSettingsDialog = false
                })
                householdViewModel.overrideHouseholdId(newId) { error ->
                    handler(error)
                    onComplete(error)
                }
            }
        )
    }

    if (showProposalWizard) {
        ProposalWizardDialog(
            onDismiss = { showProposalWizard = false },
            onConfirm = { draft, ingredients ->
                val payload = ingredients.map { it.name to it.needToBuy }
                viewModel.createProposalWithIngredients(
                    mealSlot = draft.mealSlot,
                    title = draft.title,
                    notes = draft.notes,
                    ingredients = payload,
                    onResult = buildResultHandler("Proposta salvata", onSuccess = {
                        showProposalWizard = false
                    })
                )
            }
        )
    }

    if (showShoppingDialog) {
        val proposals = (state as? Loadable.Ready)?.data?.proposals.orEmpty()
        ShoppingItemDialog(
            proposals = proposals,
            onDismiss = { showShoppingDialog = false },
            onConfirm = { name, proposalId ->
                when {
                    name.isBlank() -> emitStatus("Scrivi un nome", isError = true)
                    proposalId == null -> {
                        viewModel.createManualShoppingItem(
                            name = name,
                            onResult = buildResultHandler("Aggiunto alla spesa", onSuccess = {
                                showShoppingDialog = false
                            })
                        )
                    }
                    else -> {
                        viewModel.createIngredient(
                            proposalId = proposalId,
                            name = name,
                            needToBuy = true,
                            onResult = buildResultHandler("Aggiunto alla spesa", onSuccess = {
                                showShoppingDialog = false
                            })
                        )
                    }
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            icon = { Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = null) },
            onDismissRequest = { showResetDialog = false },
            title = { Text("Cancella tutto") },
            text = { Text("Svuota menu, ingredienti e lista della spesa? L'operazione non si può annullare.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAll(
                        onResult = buildResultHandler(
                            successMessage = "Dati azzerati",
                            onSuccess = {
                                showResetDialog = false
                                viewModel.refresh()
                            }
                        )
                    )
                }) {
                    Text("Svuota")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

private data class ProposalWithIngredients(
    val proposal: MealProposal,
    val ingredients: List<MealIngredient>
)

data class ProposalDraft(
    val mealSlot: String = "",
    val title: String = "",
    val notes: String = ""
)

data class IngredientDraft(
    val name: String = "",
    val needToBuy: Boolean = true
)

@Composable
private fun MenuTab(
    padding: PaddingValues,
    state: Loadable<ApiState>,
    onUpdateProposalStatus: (MealProposal, MealStatus) -> Unit,
    onCreateProposal: (ProposalDraft, () -> Unit) -> Unit,
    onUpdateProposal: (MealProposal, ProposalDraft, () -> Unit) -> Unit,
    onDeleteProposal: (MealProposal) -> Unit,
    onCreateIngredient: (String, IngredientDraft, () -> Unit) -> Unit,
    onUpdateIngredient: (MealIngredient, IngredientDraft, () -> Unit) -> Unit,
    onToggleIngredient: (MealIngredient, Boolean) -> Unit,
    onDeleteIngredient: (MealIngredient) -> Unit
) {
    var proposalDraft by remember { mutableStateOf<ProposalDraft?>(null) }
    var editingProposal by remember { mutableStateOf<MealProposal?>(null) }
    var ingredientDraft by remember { mutableStateOf<IngredientDraft?>(null) }
    var editingIngredient by remember { mutableStateOf<MealIngredient?>(null) }
    var ingredientProposalId by remember { mutableStateOf<String?>(null) }
    // Sub-tab: Tutto / Pranzi / Cene
    var selectedFilter by rememberSaveable { mutableStateOf(MealFilter.All.name) }
    // Se utente è su tab "Pranzi" o "Cene" e apre un dettaglio
    var detailProposal by remember { mutableStateOf<MealProposal?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state) {
            is Loadable.Idle -> Text("Premi l'icona di aggiornamento per sincronizzare il menu", modifier = Modifier.fillMaxWidth())
            is Loadable.Loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            is Loadable.Error -> Text("Errore: ${state.throwable.message}", modifier = Modifier.fillMaxWidth())
            is Loadable.Ready -> {
                // Se siamo in dettaglio mostra schermo dettaglio singola proposta
                val filterEnum = MealFilter.valueOf(selectedFilter)
                if (detailProposal != null && filterEnum != MealFilter.All) {
                    ProposalDetailScreen(
                        proposal = detailProposal!!,
                        ingredients = state.data.ingredients.filter { it.proposalId == detailProposal!!.proposalId },
                        onBack = { detailProposal = null },
                        onEdit = { p ->
                            editingProposal = p
                            proposalDraft = ProposalDraft(mealSlot = p.mealSlot, title = p.title, notes = p.notes)
                        },
                            onUpdateStatus = onUpdateProposalStatus,
                        onDelete = onDeleteProposal,
                        onAddIngredient = { pid ->
                            ingredientProposalId = pid
                            editingIngredient = null
                            ingredientDraft = IngredientDraft()
                        },
                        onEditIngredient = { ingredient ->
                            editingIngredient = ingredient
                            ingredientProposalId = ingredient.proposalId
                            ingredientDraft = IngredientDraft(name = ingredient.name, needToBuy = ingredient.needToBuy)
                        },
                        onToggleIngredient = onToggleIngredient,
                        onDeleteIngredient = onDeleteIngredient
                    )
                } else {
                    FilterRow(
                        active = filterEnum,
                        onSelected = {
                            selectedFilter = it.name
                            // reset detail all cambio tab
                            detailProposal = null
                        }
                    )
                    val grouped = remember(state.data) {
                        state.data.proposals.map { proposal ->
                            ProposalWithIngredients(
                                proposal = proposal,
                                ingredients = state.data.ingredients.filter { it.proposalId == proposal.proposalId }
                            )
                        }
                    }
                    val filtered = remember(grouped, selectedFilter) {
                        when (filterEnum) {
                            MealFilter.All -> grouped
                            MealFilter.Pranzo -> grouped.filter { it.proposal.mealSlot.equals("Pranzo", ignoreCase = true) }
                            MealFilter.Cena -> grouped.filter { it.proposal.mealSlot.equals("Cena", ignoreCase = true) }
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text("Non c'è nulla in programma: premi “Nuova proposta” per iniziare.", fontWeight = FontWeight.SemiBold)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(filtered, key = { it.proposal.proposalId }) { item ->
                                if (filterEnum == MealFilter.All) {
                                    // Vista completa con ingredienti
                                    ProposalCard(
                                        item = item,
                                        onEdit = { proposal ->
                                            editingProposal = proposal
                                            proposalDraft = ProposalDraft(
                                                mealSlot = proposal.mealSlot,
                                                title = proposal.title,
                                                notes = proposal.notes
                                            )
                                        },
                                        onToggleStatus = onUpdateProposalStatus,
                                        onDelete = onDeleteProposal,
                                        onAddIngredient = { proposalId ->
                                            ingredientProposalId = proposalId
                                            editingIngredient = null
                                            ingredientDraft = IngredientDraft()
                                        },
                                        onEditIngredient = { ingredient ->
                                            editingIngredient = ingredient
                                            ingredientProposalId = ingredient.proposalId
                                            ingredientDraft = IngredientDraft(
                                                name = ingredient.name,
                                                needToBuy = ingredient.needToBuy
                                            )
                                        },
                                        onToggleIngredient = onToggleIngredient,
                                        onDeleteIngredient = onDeleteIngredient
                                    )
                                } else {
                                    // Vista lista semplice: solo card minimale cliccabile
                                    SimpleProposalRow(
                                        proposal = item.proposal,
                                        onToggleStatus = onUpdateProposalStatus,
                                        onOpen = { detailProposal = it },
                                        onDelete = onDeleteProposal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (proposalDraft != null) {
        ProposalDialog(
            initial = proposalDraft!!,
            onDismiss = {
                proposalDraft = null
                editingProposal = null
            },
            onConfirm = { draft ->
                val onSuccess = {
                    proposalDraft = null
                    editingProposal = null
                }
                if (editingProposal == null) {
                    onCreateProposal(draft, onSuccess)
                } else {
                    onUpdateProposal(editingProposal!!, draft, onSuccess)
                }
            }
        )
    }

    if (ingredientDraft != null && ingredientProposalId != null) {
        IngredientDialog(
            initial = ingredientDraft!!,
            onDismiss = {
                ingredientDraft = null
                editingIngredient = null
                ingredientProposalId = null
            },
            onConfirm = { draft ->
                val onSuccess = {
                    ingredientDraft = null
                    editingIngredient = null
                    ingredientProposalId = null
                }
                val proposalId = ingredientProposalId
                if (editingIngredient == null && proposalId != null) {
                    onCreateIngredient(proposalId, draft, onSuccess)
                } else if (editingIngredient != null) {
                    onUpdateIngredient(editingIngredient!!, draft, onSuccess)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalCard(
    item: ProposalWithIngredients,
    onToggleStatus: (MealProposal, MealStatus) -> Unit,
    onEdit: (MealProposal) -> Unit,
    onDelete: (MealProposal) -> Unit,
    onAddIngredient: (String) -> Unit,
    onEditIngredient: (MealIngredient) -> Unit,
    onToggleIngredient: (MealIngredient, Boolean) -> Unit,
    onDeleteIngredient: (MealIngredient) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.proposal.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.proposal.mealSlot.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                ProposalStatusButton(
                    proposal = item.proposal,
                    onStatusChange = { status -> onToggleStatus(item.proposal, status) }
                )
                Spacer(modifier = Modifier.size(8.dp))
                TextButton(onClick = { onAddIngredient(item.proposal.proposalId) }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Ingrediente")
                }
            }
            if (item.proposal.notes.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = item.proposal.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            if (item.ingredients.isEmpty()) {
                Text("Nessun ingrediente ancora", style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.ingredients.forEach { ingredient ->
                        IngredientPill(
                            ingredient = ingredient,
                            onToggle = onToggleIngredient,
                            onEdit = onEditIngredient,
                            onDelete = onDeleteIngredient
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { onEdit(item.proposal) }) { Text("Modifica") }
                TextButton(onClick = { onDelete(item.proposal) }) { Text("Elimina") }
            }
        }
    }
}

@Composable
private fun ProposalStatusButton(
    proposal: MealProposal,
    onStatusChange: (MealStatus) -> Unit
) {
    val isCooked = proposal.status == MealStatus.Cooked
    val nextStatus = if (isCooked) MealStatus.Pending else MealStatus.Cooked
    FilledTonalButton(
        onClick = { onStatusChange(nextStatus) },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isCooked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Icon(
            imageVector = if (isCooked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (isCooked) "Segna come in attesa" else "Segna come cucinato"
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(if (isCooked) "Cucinato" else "In attesa")
    }
}

@Composable
private fun SimpleProposalRow(
    proposal: MealProposal,
    onToggleStatus: (MealProposal, MealStatus) -> Unit,
    onOpen: (MealProposal) -> Unit,
    onDelete: (MealProposal) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = proposal.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = proposal.mealSlot,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            ProposalStatusIcon(
                proposal = proposal,
                onStatusChange = { status -> onToggleStatus(proposal, status) }
            )
            TextButton(onClick = { onOpen(proposal) }) { Text("Apri") }
            IconButton(onClick = { onDelete(proposal) }) { Icon(Icons.Filled.Delete, contentDescription = "Elimina") }
        }
    }
}

@Composable
private fun ProposalStatusIcon(
    proposal: MealProposal,
    onStatusChange: (MealStatus) -> Unit
) {
    val isCooked = proposal.status == MealStatus.Cooked
    val nextStatus = if (isCooked) MealStatus.Pending else MealStatus.Cooked
    IconButton(onClick = { onStatusChange(nextStatus) }) {
        Icon(
            imageVector = if (isCooked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (isCooked) "Segna come in attesa" else "Segna come cucinato",
            tint = if (isCooked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalDetailScreen(
    proposal: MealProposal,
    ingredients: List<MealIngredient>,
    onBack: () -> Unit,
    onEdit: (MealProposal) -> Unit,
    onUpdateStatus: (MealProposal, MealStatus) -> Unit,
    onDelete: (MealProposal) -> Unit,
    onAddIngredient: (String) -> Unit,
    onEditIngredient: (MealIngredient) -> Unit,
    onToggleIngredient: (MealIngredient, Boolean) -> Unit,
    onDeleteIngredient: (MealIngredient) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Indietro") }
            Text(proposal.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(proposal.mealSlot.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        ProposalStatusButton(
            proposal = proposal,
            onStatusChange = { status -> onUpdateStatus(proposal, status) }
        )
        if (proposal.notes.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(proposal.notes, modifier = Modifier.padding(12.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { onEdit(proposal) }) { Text("Modifica") }
            TextButton(onClick = { onDelete(proposal) }) { Text("Elimina") }
            TextButton(onClick = { onAddIngredient(proposal.proposalId) }) { Text("Ingrediente") }
        }
        Text("Ingredienti", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (ingredients.isEmpty()) {
            Text("Nessun ingrediente ancora")
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ingredients.forEach { ingredient ->
                    IngredientPill(
                        ingredient = ingredient,
                        onToggle = onToggleIngredient,
                        onEdit = onEditIngredient,
                        onDelete = onDeleteIngredient
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientPill(
    ingredient: MealIngredient,
    onToggle: (MealIngredient, Boolean) -> Unit,
    onEdit: (MealIngredient) -> Unit,
    onDelete: (MealIngredient) -> Unit
) {
    Surface(color = if (ingredient.needToBuy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false)
            )
            AssistChip(
                onClick = { onToggle(ingredient, !ingredient.needToBuy) },
                label = { Text(if (ingredient.needToBuy) "Da comprare" else "In dispensa") }
            )
            IconButton(onClick = { onEdit(ingredient) }) {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = "Modifica ingrediente")
            }
            IconButton(onClick = { onDelete(ingredient) }) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Cancella ingrediente")
            }
        }
    }
}

@Composable
private fun ProposalDialog(
    initial: ProposalDraft,
    onDismiss: () -> Unit,
    onConfirm: (ProposalDraft) -> Unit
) {
    var mealSlot by remember { mutableStateOf(normalizeMealSlot(initial.mealSlot)) }
    var title by remember { mutableStateOf(initial.title) }
    var notes by remember { mutableStateOf(initial.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica proposta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MealSlotDropdown(
                    value = mealSlot,
                    onValueChange = { mealSlot = it },
                    label = "Momento"
                )
                TextField(value = title, onValueChange = { title = it }, label = { Text("Titolo") })
                TextField(value = notes, onValueChange = { notes = it }, label = { Text("Note") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ProposalDraft(mealSlot = mealSlot, title = title, notes = notes))
            }) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
private fun HouseholdSettingsDialog(
    currentHouseholdId: String,
    inviteCode: String?,
    onDismiss: () -> Unit,
    onSave: (String, (Throwable?) -> Unit) -> Unit
) {
    var householdId by remember(currentHouseholdId) { mutableStateOf(currentHouseholdId) }
    var isSaving by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = { Text("Impostazioni") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ID", style = MaterialTheme.typography.labelMedium)
                TextField(
                    value = householdId,
                    onValueChange = { householdId = it },
                    label = { Text("ID") },
                    singleLine = true
                )
                inviteCode?.let { code ->
                    Text("Codice invito", style = MaterialTheme.typography.labelMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copia codice invito")
                        }
                    }
                    Text("Condividi questo codice con chi deve unirsi al gruppo.", style = MaterialTheme.typography.bodySmall)
                } ?: Text("Nessun codice invito disponibile per questo gruppo.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            val trimmedId = householdId.trim()
            Button(
                onClick = {
                    isSaving = true
                    onSave(trimmedId) { error ->
                        isSaving = false
                        if (error == null) {
                            householdId = trimmedId
                        }
                    }
                },
                enabled = trimmedId.isNotEmpty() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Salva")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isSaving) onDismiss() }, enabled = !isSaving) {
                Text("Chiudi")
            }
        }
    )
}

@Composable
private fun IngredientDialog(
    initial: IngredientDraft,
    onDismiss: () -> Unit,
    onConfirm: (IngredientDraft) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var needToBuy by remember { mutableStateOf(initial.needToBuy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ingrediente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Nome") })
                AssistChip(
                    onClick = { needToBuy = !needToBuy },
                    label = { Text(if (needToBuy) "Da comprare" else "In dispensa") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(IngredientDraft(name = name, needToBuy = needToBuy))
            }) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalWizardDialog(
    onDismiss: () -> Unit,
    onConfirm: (ProposalDraft, List<IngredientDraft>) -> Unit
) {
    var mealSlot by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var ingredientName by remember { mutableStateOf("") }
    var ingredientNeedToBuy by remember { mutableStateOf(true) }
    val ingredients = remember { mutableStateListOf<IngredientDraft>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova proposta") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MealSlotDropdown(
                    value = mealSlot,
                    onValueChange = { mealSlot = it },
                    label = "Momento"
                )
                TextField(value = title, onValueChange = { title = it }, label = { Text("Titolo") })
                TextField(value = notes, onValueChange = { notes = it }, label = { Text("Note") })
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Ingredienti", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { ingredientNeedToBuy = !ingredientNeedToBuy },
                                label = { Text(if (ingredientNeedToBuy) "Da comprare" else "In dispensa") }
                            )
                            TextField(
                                modifier = Modifier.weight(1f),
                                value = ingredientName,
                                onValueChange = { ingredientName = it },
                                placeholder = { Text("___") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                                ),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    if (ingredientName.isNotBlank()) {
                                        ingredients.add(IngredientDraft(name = ingredientName.trim(), needToBuy = ingredientNeedToBuy))
                                        ingredientName = ""
                                        ingredientNeedToBuy = true
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = "Aggiungi ingrediente")
                            }
                        }
                        if (ingredients.isEmpty()) {
                            Text("Nessun ingrediente ancora")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ingredients.forEachIndexed { index, ingredient ->
                                    Surface(color = if (ingredient.needToBuy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(ingredient.name, modifier = Modifier.weight(1f, fill = false))
                                            AssistChip(
                                                onClick = {
                                                    ingredients[index] = ingredient.copy(needToBuy = !ingredient.needToBuy)
                                                },
                                                label = { Text(if (ingredient.needToBuy) "Da comprare" else "In dispensa") }
                                            )
                                            IconButton(onClick = { ingredients.removeAt(index) }) {
                                                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Rimuovi")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    onConfirm(ProposalDraft(mealSlot = mealSlot, title = title.trim(), notes = notes.trim()), ingredients.toList())
                }
            }) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

private enum class MealFilter { All, Pranzo, Cena }

@Composable
private fun FilterRow(active: MealFilter, onSelected: (MealFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MealFilter.values().forEach { filter ->
            val label = when (filter) {
                MealFilter.All -> "Tutto"
                MealFilter.Pranzo -> "Pranzi"
                MealFilter.Cena -> "Cene"
            }
            FilterChip(
                selected = filter == active,
                onClick = { onSelected(filter) },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealSlotDropdown(value: String, onValueChange: (String) -> Unit, label: String) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Pranzo", "Cena")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = !expanded }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = value.takeIf { it.isNotBlank() } ?: "Seleziona")
                    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun normalizeMealSlot(raw: String): String = when {
    raw.equals("Pranzo", ignoreCase = true) -> "Pranzo"
    raw.equals("Cena", ignoreCase = true) -> "Cena"
    else -> raw
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingItemDialog(
    proposals: List<MealProposal>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedProposalId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo elemento della spesa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Nome") })
                val selectedTitle = proposals.firstOrNull { it.proposalId == selectedProposalId }?.title ?: "Nessun collegamento"
                Text(text = "Collega a proposta (opzionale)", style = MaterialTheme.typography.bodySmall)
                Box {
                    FilledTonalButton(
                        onClick = { if (proposals.isNotEmpty()) expanded = true },
                        enabled = proposals.isNotEmpty()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(selectedTitle)
                            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Nessun collegamento") },
                            onClick = {
                                selectedProposalId = null
                                expanded = false
                            }
                        )
                        proposals.forEach { proposal ->
                            DropdownMenuItem(
                                text = { Text(proposal.title) },
                                onClick = {
                                    selectedProposalId = proposal.proposalId
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name.trim(), selectedProposalId)
            }) {
                Text("Aggiungi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

private data class ShoppingUiGroup(
    val label: String,
    val entries: List<ShoppingEntry>
) {
    val key: String = entries.joinToString(separator = "|") { it.shoppingId }
    val count: Int = entries.size
}

@Composable
private fun StatusIndicator(message: String, isError: Boolean) {
    val bubbleColor = if (isError) Color(0xFF4B5563) else Color(0xFF9CA3AF)
    Surface(
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = message },
        shape = CircleShape,
        color = bubbleColor,
        contentColor = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "!", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShoppingTab(
    padding: PaddingValues,
    state: Loadable<ApiState>,
    onCompleteGroup: (List<ShoppingEntry>) -> Unit,
    onDeleteGroup: (List<ShoppingEntry>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (state) {
            is Loadable.Idle -> Text("Premi l'icona di aggiornamento per vedere la lista della spesa", modifier = Modifier.fillMaxWidth())
            is Loadable.Loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            is Loadable.Error -> Text("Errore: ${state.throwable.message}", modifier = Modifier.fillMaxWidth())
            is Loadable.Ready -> {
                val groups = remember(state.data.shopping) {
                    state.data.shopping
                        .groupBy { it.name.trim().lowercase(Locale.getDefault()) }
                        .map { (_, items) ->
                            val display = items.firstOrNull()?.name?.trim()?.takeIf { it.isNotBlank() } ?: "___"
                            ShoppingUiGroup(label = display, entries = items)
                        }
                        .sortedBy { it.label.lowercase(Locale.getDefault()) }
                }
                if (groups.isEmpty()) {
                    Text("Nulla da comprare", fontWeight = FontWeight.SemiBold)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(groups, key = { it.key }) { group ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = if (group.count > 1) "${group.label} ×${group.count}" else group.label,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = { onCompleteGroup(group.entries) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF15803D),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(imageVector = Icons.Filled.Check, contentDescription = "Segna come acquistato")
                                            Spacer(modifier = Modifier.size(6.dp))
                                            Text("Fatto")
                                        }
                                        Button(
                                            onClick = { onDeleteGroup(group.entries) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFB91C1C),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Elimina")
                                            Spacer(modifier = Modifier.size(6.dp))
                                            Text("Rimuovi")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
