package com.nexcheck.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nexcheck.app.ui.components.*
import com.nexcheck.app.ui.theme.NexTheme
import java.text.SimpleDateFormat
import java.util.*

// Status calculado de cada placa (a última vistoria define a situação)
private val PendencyOrder = listOf("revistoria", "vencida", "avencer", "emdia")

private fun pendencyTone(status: String) = when (status) {
    "revistoria" -> StatusTone.Revisit
    "vencida" -> StatusTone.Danger
    "avencer" -> StatusTone.Warning
    else -> StatusTone.Success
}

private fun pendencyGroupTitle(status: String) = when (status) {
    "revistoria" -> "Revistorias"
    "vencida" -> "Vencidas"
    "avencer" -> "A vencer (15 dias)"
    else -> "Em dia"
}

@Composable
fun PendenciesScreen(
    onBack: () -> Unit,
    // Tipo, Placa, Marca, Modelo, Empresa, Motorista, Cliente, Ação
    onStartInspection: (String, String, String, String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { InspectionRepository() }
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", BrLocale) }

    var pendencies by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var searchText by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        repository.getAllInspections(limit = null) { results ->
            val relevantInspections = results.filter {
                val type = it["vehicleType"]?.toString() ?: ""
                val info = it["vehicleInfo"] as? Map<*, *>
                type.contains("Cavalo") && info?.get("placa") != null
            }

            val historyByPlate = mutableMapOf<String, MutableList<Map<String, Any>>>()
            relevantInspections.forEach { insp ->
                val plate = (insp["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() ?: return@forEach
                historyByPlate.getOrPut(plate) { mutableListOf() }.add(insp)
            }

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.time
            val alertDaysFromNow = Calendar.getInstance().apply { time = today; add(Calendar.DAY_OF_YEAR, 15) }.time

            val processedPendencies = historyByPlate.values.map { inspections ->
                inspections.sortByDescending { (it["inspectionDate"] as? Timestamp)?.seconds ?: 0L }
                val lastInsp = inspections.first()
                val isReprovado = lastInsp["status"] == "Não Liberado" || lastInsp["status"] == "Reprovado"

                val validade: Date = lastInsp["nextInspectionDate"].asDate()
                    ?: lastInsp["inspectionDate"].asDate()?.let { d -> Calendar.getInstance().apply { time = d; add(Calendar.MONTH, 6) }.time }
                    ?: Date(0)

                val (status, label) = when {
                    isReprovado -> "revistoria" to "Reprovado"
                    validade < today -> "vencida" to "Vencida"
                    validade <= alertDaysFromNow -> "avencer" to "A vencer"
                    else -> "emdia" to "Em dia"
                }
                lastInsp + mapOf("_statusNormalizado" to status, "_labelStatus" to label, "_validadeJS" to validade)
            }.sortedBy { PendencyOrder.indexOf(it["_statusNormalizado"]) }

            pendencies = processedPendencies
            isLoading = false
        }
    }

    val counts = remember(pendencies) { pendencies.groupingBy { it["_statusNormalizado"].toString() }.eachCount() }
    val filtered = remember(pendencies, searchText, activeFilter) {
        pendencies.filter { p ->
            val placa = (p["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() ?: ""
            val company = p["company"]?.toString() ?: p["clientName"]?.toString() ?: ""
            val matchSearch = searchText.isBlank() || placa.contains(searchText, true) || company.contains(searchText, true)
            val matchStatus = activeFilter == "ALL" || p["_statusNormalizado"] == activeFilter
            matchSearch && matchStatus
        }
    }

    NexScaffold(title = "Pendências de frota", subtitle = "Situação da última vistoria de cada cavalo", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- CONTADORES (também funcionam como filtro) ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PendencyOrder.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { status ->
                                CounterTile(
                                    label = pendencyGroupTitle(status),
                                    count = counts[status] ?: 0,
                                    tone = pendencyTone(status),
                                    selected = activeFilter == status,
                                    modifier = Modifier.weight(1f)
                                ) { activeFilter = if (activeFilter == status) "ALL" else status }
                            }
                        }
                    }
                }
            }
            item { SearchField(searchText, { searchText = it }, "Buscar placa ou transportadora") }

            when {
                isLoading -> item { LoadingState() }
                filtered.isEmpty() -> item { EmptyState(Icons.Default.CheckCircle, "Nenhuma pendência encontrada", "Ajuste a busca ou o filtro selecionado.") }
                else -> PendencyOrder.forEach { status ->
                    val group = filtered.filter { it["_statusNormalizado"] == status }
                    if (group.isNotEmpty()) {
                        item(key = "h-$status") { SectionTitle("${pendencyGroupTitle(status)} · ${group.size}", Modifier.padding(top = 4.dp)) }
                        items(group, key = { "$status-${(it["vehicleInfo"] as? Map<*, *>)?.get("placa")}" }) { item ->
                            PendencyCard(item, context, onStartInspection, sdf)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterTile(label: String, count: Int, tone: StatusTone, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (fg, bg) = toneColors(tone)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (selected) bg else MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) fg else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = fg)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PendencyCard(
    item: Map<String, Any>,
    context: Context,
    onStartInspection: (String, String, String, String, String, String, String, String) -> Unit,
    sdf: SimpleDateFormat
) {
    val info = item["vehicleInfo"] as? Map<*, *>
    val placa = info?.get("placa")?.toString() ?: "S/P"
    val marca = info?.get("marca")?.toString() ?: ""
    val modelo = info?.get("modelo")?.toString() ?: ""

    val company = item["company"]?.toString() ?: item["clientName"]?.toString() ?: ""
    val motorista = item["driverName"]?.toString() ?: ""
    val cliente = item["clientName"]?.toString() ?: ""
    val servicos = item["servicesNeeded"]?.toString() ?: "Vencimento de prazo"

    val ultimaData = (item["inspectionDate"] as? Timestamp)?.toDate()?.let { sdf.format(it) } ?: "—"
    val vencimentoStr = (item["_validadeJS"] as? Date)?.let { sdf.format(it) } ?: "—"

    val status = item["_statusNormalizado"]?.toString() ?: "emdia"
    val tone = pendencyTone(status)
    val (toneFg, _) = toneColors(tone)
    val isReprovado = status == "revistoria"
    val acaoFinal = if (isReprovado) "Revistoria" else "Vistoria"

    NexCard(accent = toneFg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateTag(placa)
            Spacer(Modifier.weight(1f))
            StatusPill(item["_labelStatus"]?.toString() ?: "", tone)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoItem("Transportadora", company.ifBlank { "N/A" }, Modifier.weight(1f))
            InfoItem("Última inspeção", ultimaData, Modifier.weight(0.7f))
        }
        Spacer(Modifier.height(10.dp))
        if (isReprovado) {
            InfoItem("Motivo", servicos, valueColor = NexTheme.status.danger)
        } else {
            InfoItem("Vencimento", vencimentoStr, valueColor = toneFg, emphasize = true)
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (status != "emdia") {
                FilledTonalIconButton(
                    onClick = {
                        val msg = if (isReprovado) "🚨 *NEXCHECK - ALERTA DE REPROVAÇÃO* 🚨\n\nOlá, prezados *$company*,\nO conjunto/veículo placa *$placa* foi *REPROVADO*."
                        else "⚠️ *NEXCHECK - ALERTA DE VENCIMENTO* ⚠️\n\nA vistoria de *$placa* vence em breve."
                        openWhatsApp(context, msg)
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = NexTheme.status.dangerContainer,
                        contentColor = NexTheme.status.danger
                    ),
                    modifier = Modifier.size(48.dp)
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Avisar pelo WhatsApp") }
            }
            Button(
                onClick = { onStartInspection("cavalo", placa, marca, modelo, company, motorista, cliente, acaoFinal) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = if (isReprovado) ButtonDefaults.buttonColors(containerColor = NexTheme.status.revisit, contentColor = MaterialTheme.colorScheme.surface)
                else ButtonDefaults.buttonColors()
            ) {
                Icon(if (isReprovado) Icons.Default.Refresh else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isReprovado) "Abrir revistoria" else "Iniciar vistoria", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
