package com.nexcheck.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nexcheck.app.ui.components.*
import com.nexcheck.app.ui.theme.NexTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun incidentColor(type: String): Color = when (type) {
    "ACIDENTE" -> NexTheme.status.danger
    "INCIDENTE" -> NexTheme.status.info
    "FALHA MECÂNICA" -> NexTheme.status.revisit
    "FALHA ELÉTRICA" -> NexTheme.status.warning
    else -> NexTheme.status.neutral
}

@Composable
private fun IncidentTypeTag(type: String) {
    val color = incidentColor(type)
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.extraSmall) {
        Text(type, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun IncidentsScreen(onBack: () -> Unit, onNewIncident: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val sdfDate = remember { SimpleDateFormat("dd/MM/yyyy · HH:mm", BrLocale) }

    var incidents by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("ALL") }
    var selectedIncident by remember { mutableStateOf<Map<String, Any>?>(null) }

    // Escuta em tempo real e remove o listener ao sair da tela
    DisposableEffect(Unit) {
        val registration = db.collection("incidents").orderBy("date", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) incidents = snapshot.documents.map { it.data.orEmpty() + ("id" to it.id) }
                isLoading = false
            }
        onDispose { registration.remove() }
    }

    val filtered = remember(incidents, searchText, typeFilter) {
        incidents.filter {
            val placa = it["plate"]?.toString() ?: ""
            val mot = it["driver"]?.toString() ?: ""
            val comp = it["company"]?.toString() ?: ""
            (placa.contains(searchText, true) || mot.contains(searchText, true) || comp.contains(searchText, true)) &&
                (typeFilter == "ALL" || it["type"] == typeFilter)
        }
    }

    NexScaffold(
        title = "Ocorrências",
        subtitle = "Acidentes, incidentes e falhas",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewIncident,
                containerColor = NexTheme.status.danger,
                contentColor = MaterialTheme.colorScheme.surface,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Registrar") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SearchField(searchText, { searchText = it }, "Buscar placa, motorista ou empresa") }
            item {
                FilterChipsRow(
                    options = listOf(FilterOption("ALL", "Todas")) + IncidentTypes.map { t ->
                        FilterOption(t, t.lowercase().replaceFirstChar { it.uppercase() }, incidents.count { it["type"] == t })
                    },
                    selected = typeFilter,
                    onSelect = { typeFilter = it }
                )
            }
            when {
                isLoading -> item { LoadingState() }
                filtered.isEmpty() -> item { EmptyState(Icons.Default.CarCrash, "Nenhuma ocorrência encontrada") }
                else -> items(filtered, key = { it["id"].toString() }) { inc ->
                    IncidentCard(inc, sdfDate) { selectedIncident = inc }
                }
            }
        }
    }

    // --- DETALHES ---
    selectedIncident?.let { data ->
        val type = data["type"]?.toString() ?: "OUTRO"
        val date = (data["date"] as? Timestamp)?.toDate()?.let { sdfDate.format(it) } ?: "—"
        val cause = data["cause"]?.toString() ?: "A ANALISAR"

        FullScreenDetail(title = "Detalhes da ocorrência", subtitle = date, onClose = { selectedIncident = null }) {
            item {
                NexCard(accent = incidentColor(type)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlateTag(data["plate"]?.toString() ?: "", large = true)
                        Spacer(Modifier.weight(1f))
                        IncidentTypeTag(type)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        InfoItem("Motorista", data["driver"]?.toString() ?: "", Modifier.weight(1f))
                        InfoItem("Transportadora", data["company"]?.toString() ?: "", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    InfoItem("Localização", data["location"]?.toString()?.ifBlank { "Não informado" } ?: "Não informado")
                    Spacer(Modifier.height(12.dp))
                    InfoItem("Registrado por", data["createdBy"]?.toString() ?: "")
                }
            }
            item { SectionTitle("Causa raiz", Modifier.padding(top = 8.dp)) }
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(cause, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item { SectionTitle("Relato detalhado", Modifier.padding(top = 8.dp)) }
            item {
                NexCard {
                    Text(
                        data["relato"]?.toString()?.ifBlank { null } ?: "Sem descrição preenchida.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun IncidentCard(inc: Map<String, Any>, sdf: SimpleDateFormat, onClick: () -> Unit) {
    val type = inc["type"]?.toString() ?: "OUTRO"
    val date = (inc["date"] as? Timestamp)?.toDate()?.let { sdf.format(it) } ?: "—"

    NexCard(onClick = onClick, accent = incidentColor(type)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateTag(inc["plate"]?.toString() ?: "")
            Spacer(Modifier.weight(1f))
            IncidentTypeTag(type)
        }
        Spacer(Modifier.height(12.dp))
        Text(inc["company"]?.toString() ?: "—", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Motorista: ${inc["driver"]?.toString() ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val relato = inc["relato"]?.toString().orEmpty()
        if (relato.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(relato, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val location = inc["location"]?.toString().orEmpty()
            if (location.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
