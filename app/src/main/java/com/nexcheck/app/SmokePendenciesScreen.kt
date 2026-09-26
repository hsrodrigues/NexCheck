package com.nexcheck.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.nexcheck.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

private val SmokeOrder = listOf("vencida", "avencer", "emdia")

private fun smokeTone(status: String) = when (status) {
    "vencida" -> StatusTone.Danger
    "avencer" -> StatusTone.Warning
    else -> StatusTone.Success
}

private fun smokeTitle(status: String) = when (status) {
    "vencida" -> "Vencidas / reprovadas"
    "avencer" -> "A vencer"
    else -> "Em dia"
}

@Composable
fun SmokePendenciesScreen(
    onBack: () -> Unit,
    onNewSmokeInspection: (String, String) -> Unit // Placa e Empresa
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sdf = remember { SimpleDateFormat("dd/MM/yyyy", BrLocale) }

    var pendencies by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        db.collection("smokeInspections").get().addOnSuccessListener { snapshot ->
            val allSmokeInspections = snapshot.documents.map { it.data ?: emptyMap<String, Any>() }

            // 1. Última aferição de cada placa
            val latestByPlate = mutableMapOf<String, Map<String, Any>>()
            for (insp in allSmokeInspections) {
                val plate = insp["vehiclePlate"]?.toString() ?: continue
                val currentDate = insp["inspectionDate"].asDate()?.time ?: 0L
                val savedDate = latestByPlate[plate]?.get("inspectionDate").asDate()?.time ?: 0L
                if (currentDate >= savedDate) latestByPlate[plate] = insp
            }

            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
            val alertDaysFromNow = Calendar.getInstance().apply { time = today; add(Calendar.DAY_OF_YEAR, 15) }.time

            // 2. Status: reprovado entra direto como crítico
            pendencies = latestByPlate.values.map { insp ->
                val nextDate = insp["nextInspectionDate"].asDate() ?: Date(0)
                val (status, label) = when {
                    insp["result"]?.toString() == "Reprovado" -> "vencida" to "Reprovado"
                    nextDate < today -> "vencida" to "Vencida"
                    nextDate <= alertDaysFromNow -> "avencer" to "A vencer"
                    else -> "emdia" to "Em dia"
                }
                insp + mapOf("_statusNormalizado" to status, "_labelStatus" to label, "_validadeJS" to nextDate)
            }.sortedBy { SmokeOrder.indexOf(it["_statusNormalizado"]) }
            isLoading = false
        }.addOnFailureListener {
            isLoading = false
            Toast.makeText(context, "Erro ao carregar dados", Toast.LENGTH_SHORT).show()
        }
    }

    val counts = remember(pendencies) { pendencies.groupingBy { it["_statusNormalizado"].toString() }.eachCount() }
    val filtered = remember(pendencies, searchText, activeFilter) {
        pendencies.filter { p ->
            val placa = p["vehiclePlate"]?.toString() ?: ""
            val company = p["companyName"]?.toString() ?: ""
            (searchText.isBlank() || placa.contains(searchText, true) || company.contains(searchText, true)) &&
                (activeFilter == "ALL" || p["_statusNormalizado"] == activeFilter)
        }
    }

    NexScaffold(
        title = "Pendências de fumaça",
        subtitle = "Última aferição de cada placa",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNewSmokeInspection("", "") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nova aferição") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmokeOrder.forEach { status ->
                        val (fg, bg) = toneColors(smokeTone(status))
                        val selected = activeFilter == status
                        Card(
                            onClick = { activeFilter = if (selected) "ALL" else status },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = if (selected) bg else MaterialTheme.colorScheme.surface),
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) fg else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                                Text("${counts[status] ?: 0}", style = MaterialTheme.typography.headlineMedium, color = fg)
                                Text(smokeTitle(status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item { SearchField(searchText, { searchText = it }, "Buscar placa ou transportadora") }

            when {
                isLoading -> item { LoadingState() }
                filtered.isEmpty() -> item { EmptyState(Icons.Default.CheckCircle, "Nenhuma pendência encontrada") }
                else -> items(filtered, key = { it["vehiclePlate"].toString() }) { item ->
                    SmokeCard(item, sdf, onNewSmokeInspection)
                }
            }
        }
    }
}

@Composable
private fun SmokeCard(item: Map<String, Any>, sdf: SimpleDateFormat, onNewSmokeInspection: (String, String) -> Unit) {
    val placa = item["vehiclePlate"]?.toString() ?: "S/P"
    val company = item["companyName"]?.toString() ?: "N/A"
    val nivel = item["ringelmannValue"]?.toString()?.toIntOrNull() ?: 0
    val resultado = item["result"]?.toString() ?: ""
    val vencimentoStr = (item["_validadeJS"] as? Date)?.let { sdf.format(it) } ?: "—"
    val tone = smokeTone(item["_statusNormalizado"]?.toString() ?: "emdia")
    val toneFg = toneColors(tone).first

    NexCard(accent = toneFg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateTag(placa)
            Spacer(Modifier.weight(1f))
            StatusPill(item["_labelStatus"]?.toString() ?: "", tone)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoItem("Transportadora", company, Modifier.weight(1f))
            InfoItem("Nível", "$nivel (${nivel * 20}%)", Modifier.weight(0.6f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoItem("Vencimento", vencimentoStr, Modifier.weight(1f), valueColor = toneFg, emphasize = true)
            InfoItem("Último resultado", resultado, Modifier.weight(0.6f), valueColor = toneColors(toneFor(resultado)).first)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { onNewSmokeInspection(placa, company) },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Air, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nova aferição", style = MaterialTheme.typography.labelLarge)
        }
    }
}
