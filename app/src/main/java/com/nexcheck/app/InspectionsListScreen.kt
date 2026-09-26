package com.nexcheck.app

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nexcheck.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionsListScreen(
    onBack: () -> Unit,
    onViewInspection: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { InspectionRepository() }
    var inspections by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var companyLogo by remember { mutableStateOf<String?>(null) }

    var selectedGroup by remember { mutableStateOf<InspectionGroup?>(null) }
    var selectedInspection by remember { mutableStateOf<Map<String, Any>?>(null) }

    LaunchedEffect(Unit) {
        repository.getAllInspections { results ->
            inspections = results
            isLoading = false
        }
        repository.getCompanyLogo { logo -> companyLogo = logo }
    }

    val grouped = remember(inspections, searchText) {
        groupInspections(inspections.filter {
            val info = it["vehicleInfo"] as? Map<*, *>
            val placa = info?.get("placa")?.toString() ?: info?.get("composicao1")?.toString() ?: ""
            val comp = it["company"]?.toString() ?: ""
            placa.contains(searchText, ignoreCase = true) || comp.contains(searchText, ignoreCase = true)
        })
    }

    NexScaffold(
        title = "Vistorias realizadas",
        subtitle = if (isLoading) null else "${grouped.size} conjuntos nas últimas 50 vistorias",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SearchField(searchText, { searchText = it }, "Buscar placa ou transportadora") }
            when {
                isLoading -> item { LoadingState() }
                grouped.isEmpty() -> item { EmptyState(Icons.Default.SearchOff, "Nenhuma vistoria encontrada", "Tente buscar por outra placa ou transportadora.") }
                else -> items(grouped, key = { it.allIds.first() }) { group ->
                    GroupCard(group, context) { selectedGroup = group }
                }
            }
        }
    }

    // --- CONJUNTO: cavalo + composições ---
    selectedGroup?.let { group ->
        if (selectedInspection == null) {
            ModalBottomSheet(
                onDismissRequest = { selectedGroup = null },
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                    Text("Conjunto", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Toque em um veículo para ver o relatório",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    val allItems = listOfNotNull(group.cavalo) + group.composicoes
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(allItems) { item -> InspectionCard(item, context) { selectedInspection = item } }
                    }
                }
            }
        }
    }

    // --- RELATÓRIO ---
    selectedInspection?.let { data ->
        val numero = data["inspectionNumber"]?.toString() ?: "—"
        FullScreenDetail(
            title = "Relatório Nº $numero",
            subtitle = data["vehicleType"]?.toString()?.replace("Inspeção Eletromecânica - ", ""),
            onClose = { selectedInspection = null },
            actions = {
                IconButton(onClick = {
                    val id = data["id"]?.toString() ?: ""
                    if (id.isNotEmpty()) onViewInspection(id)
                }) { Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = "Ver checklist completo") }
                IconButton(onClick = { printInspectionReport(context, data, companyLogo) }) {
                    Icon(Icons.Default.Print, contentDescription = "Imprimir")
                }
            }
        ) {
            item { ReportHeaderBlock(data) }

            val itemsMap = data["items"] as? Map<*, *>
            if (!itemsMap.isNullOrEmpty()) item { SectionTitle("Checklist", Modifier.padding(top = 8.dp)) }
            itemsMap?.forEach { (_, sectionObj) ->
                val section = sectionObj as? Map<*, *>
                val title = section?.get("title")?.toString() ?: "Seção"
                val innerItems = section?.get("items") as? Map<*, *>
                if (!innerItems.isNullOrEmpty()) {
                    item {
                        NexCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                val hasRuim = innerItems.values.any { it.toString() == "ruim" }
                                StatusPill(if (hasRuim) "Reprovado" else "Aprovado")
                            }
                            Spacer(Modifier.height(8.dp))
                            innerItems.entries.forEachIndexed { index, (itemName, statusRaw) ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(itemName.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    val status = statusRaw.toString()
                                    StatusPill(if (status == "na") "N/A" else status)
                                }
                            }
                        }
                    }
                }
            }

            item { ReportConclusionBlock(data) }
            item { ReportSignaturesBlock(data) }
        }
    }
}

// =========================================================================
// AGRUPAMENTO (cavalo + composições da mesma viagem)
// =========================================================================

private data class InspectionGroup(
    val groupDate: Date,
    val clientName: String,
    val cavalo: Map<String, Any>?,
    val composicoes: MutableList<Map<String, Any>>,
    val allIds: MutableList<String>,
    var combinedStatus: String = "Liberado"
)

private fun groupInspections(inspections: List<Map<String, Any>>): List<InspectionGroup> {
    val sortedInspections = inspections.sortedByDescending { (it["inspectionDate"] as? Timestamp)?.seconds ?: 0L }
    val groups = mutableListOf<InspectionGroup>()

    for (insp in sortedInspections) {
        val type = insp["vehicleType"]?.toString() ?: ""
        if (type.contains("Cavalo")) {
            val d = (insp["inspectionDate"] as? Timestamp)?.toDate() ?: Date()
            val clientName = insp["clientName"]?.toString() ?: insp["company"]?.toString() ?: ""
            groups.add(InspectionGroup(groupDate = d, clientName = clientName, cavalo = insp, composicoes = mutableListOf(), allIds = mutableListOf(insp["id"].toString())))
        }
    }

    for (insp in sortedInspections) {
        val type = insp["vehicleType"]?.toString() ?: ""
        if (!type.contains("Cavalo")) {
            val info = insp["vehicleInfo"] as? Map<*, *>
            val linkedPlate = info?.get("linkedCavaloPlate")?.toString()?.uppercase() ?: ""
            val inspDate = (insp["inspectionDate"] as? Timestamp)?.toDate() ?: Date()

            var bestGroup: InspectionGroup? = null
            var minTimeDiff = 12L * 60 * 60 * 1000

            if (linkedPlate.isNotBlank()) {
                for (group in groups) {
                    val cInfo = group.cavalo?.get("vehicleInfo") as? Map<*, *>
                    val cavaloPlate = cInfo?.get("placa")?.toString()?.uppercase() ?: ""
                    if (cavaloPlate == linkedPlate) {
                        val timeDiff = Math.abs(group.groupDate.time - inspDate.time)
                        if (timeDiff < minTimeDiff) {
                            minTimeDiff = timeDiff
                            bestGroup = group
                        }
                    }
                }
            }

            if (bestGroup != null) {
                bestGroup.composicoes.add(insp)
                bestGroup.allIds.add(insp["id"].toString())
            } else {
                val clientName = insp["clientName"]?.toString() ?: insp["company"]?.toString() ?: ""
                groups.add(InspectionGroup(groupDate = inspDate, clientName = clientName, cavalo = null, composicoes = mutableListOf(insp), allIds = mutableListOf(insp["id"].toString())))
            }
        }
    }

    groups.forEach { group ->
        val allItems = mutableListOf<Map<String, Any>>()
        group.cavalo?.let { allItems.add(it) }
        allItems.addAll(group.composicoes)

        val hasRejection = allItems.any { it["status"] == "Não Liberado" || it["status"] == "Reprovado" }
        group.combinedStatus = if (hasRejection) "Não Liberado" else "Liberado"
        group.composicoes.sortBy { (it["vehicleInfo"] as? Map<*, *>)?.get("composicao1")?.toString() ?: "" }
    }

    return groups.sortedByDescending { it.groupDate.time }
}

@Composable
private fun GroupCard(group: InspectionGroup, context: Context, onClick: () -> Unit) {
    val isLiberado = group.combinedStatus == "Liberado"
    val statusColor = toneColors(toneFor(group.combinedStatus)).first
    val main = group.cavalo ?: group.composicoes.firstOrNull()
    val cInfo = group.cavalo?.get("vehicleInfo") as? Map<*, *>
    val compInfo = group.composicoes.firstOrNull()?.get("vehicleInfo") as? Map<*, *>

    val placaPrincipal = cInfo?.get("placa")?.toString() ?: compInfo?.get("composicao1")?.toString() ?: "S/ CAVALO"
    val transportadora = main?.get("company")?.toString()?.ifBlank { null } ?: group.clientName
    val cliente = main?.get("clientName")?.toString().orEmpty()
    val dateStr = SimpleDateFormat("dd/MM/yyyy · HH:mm", BrLocale).format(group.groupDate)
    val compCount = group.composicoes.size

    NexCard(onClick = onClick, accent = statusColor, contentPadding = PaddingValues(start = 14.dp, end = 8.dp, top = 12.dp, bottom = if (isLiberado) 12.dp else 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateTag(placaPrincipal)
            if (compCount > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                    Text(
                        "+$compCount comp.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            StatusPill(group.combinedStatus, Modifier.padding(end = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(transportadora.ifBlank { "Sem transportadora" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(cliente, dateStr).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!isLiberado) {
                WhatsAppButton {
                    enviarWhatsApp(context, "reprovada", placaPrincipal, transportadora, group.cavalo?.get("servicesNeeded")?.toString() ?: "Falha técnica")
                }
            }
        }
    }
}

@Composable
private fun WhatsAppButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = com.nexcheck.app.ui.theme.NexTheme.status.danger)
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Avisar", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ReportHeaderBlock(data: Map<String, Any>) {
    val info = data["vehicleInfo"] as? Map<*, *>
    val vehicleType = data["vehicleType"]?.toString() ?: ""
    val isCavalo = vehicleType.contains("Cavalo")

    val placa = if (isCavalo) info?.get("placa")?.toString() ?: "-" else info?.get("composicao1")?.toString() ?: "-"
    val status = data["status"]?.toString() ?: "Pendente"
    val dateString = data["inspectionDate"].asDate()?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale).format(it) } ?: "—"

    NexCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateTag(placa, large = true)
            Spacer(Modifier.weight(1f))
            StatusPill(status)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            if (isCavalo) {
                InfoItem("Motorista", data["driverName"]?.toString() ?: "", Modifier.weight(1f))
            } else {
                InfoItem("Cavalo vinculado", info?.get("linkedCavaloPlate")?.toString() ?: "Não informado", Modifier.weight(1f))
            }
            InfoItem("Transportadora", data["company"]?.toString() ?: "", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoItem("Data da vistoria", dateString, Modifier.weight(1f))
            InfoItem("Vistoriador", data["inspectorName"]?.toString() ?: "", Modifier.weight(1f))
        }
        // Próxima inspeção só aparece para cavalo
        val next = data["nextInspectionDate"].asDate()
        if (isCavalo && next != null) {
            Spacer(Modifier.height(12.dp))
            InfoItem(
                "Próxima inspeção",
                SimpleDateFormat("dd/MM/yyyy", BrLocale).format(next),
                valueColor = MaterialTheme.colorScheme.primary,
                emphasize = true
            )
        }
    }
}

@Composable
private fun ReportConclusionBlock(data: Map<String, Any>) {
    val servicos = data["servicesNeeded"]?.toString() ?: ""
    val observacoes = data["observations"]?.toString() ?: ""
    val danger = com.nexcheck.app.ui.theme.NexTheme.status.danger

    SectionTitle("Conclusão", Modifier.padding(top = 8.dp))
    NexCard {
        Text("Serviços necessários", style = MaterialTheme.typography.labelMedium, color = danger)
        Spacer(Modifier.height(4.dp))
        Text(servicos.ifBlank { "Nenhum apontamento." }, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text("Observações gerais", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(observacoes.ifBlank { "Nenhuma observação." }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReportSignaturesBlock(data: Map<String, Any>) {
    SectionTitle("Assinaturas", Modifier.padding(top = 8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SignatureImageCard("Motorista / responsável", data["driverSignature"]?.toString() ?: "", Modifier.weight(1f))
        SignatureImageCard("Vistoriador", data["inspectorSignature"]?.toString() ?: "", Modifier.weight(1f))
    }
}

@Composable
private fun SignatureImageCard(title: String, base64Str: String, modifier: Modifier) {
    val bitmap = remember(base64Str) { decodeBase64ToBitmap(base64Str) }
    NexCard(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        // Fundo branco fixo: a assinatura é traço preto em PNG transparente
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(MaterialTheme.shapes.small).background(androidx.compose.ui.graphics.Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = "Assinatura", modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            } else {
                Text("Sem assinatura", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.Gray)
            }
        }
    }
}

@Composable
private fun InspectionCard(data: Map<String, Any>, context: Context, onClick: () -> Unit) {
    val info = data["vehicleInfo"] as? Map<*, *>
    val type = data["vehicleType"]?.toString() ?: ""
    val placa = info?.get("placa")?.toString() ?: info?.get("composicao1")?.toString() ?: "S/P"
    val status = data["status"]?.toString() ?: "Pendente"
    val isLiberado = status == "Liberado" || status == "Aprovado"
    val company = data["company"]?.toString() ?: "S/ Empresa"
    val servicos = data["servicesNeeded"]?.toString() ?: "Vários"

    NexCard(onClick = onClick, accent = toneColors(toneFor(status)).first) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateTag(placa)
            Spacer(Modifier.width(12.dp))
            Text(
                type.replace("Inspeção Eletromecânica - ", ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StatusPill(status)
        }
        if (!isLiberado) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WhatsAppButton { enviarWhatsApp(context, "reprovada", placa, company, servicos) }
            }
        }
    }
}

// =========================================================================
// IMPRESSÃO E WHATSAPP
// =========================================================================

private fun printInspectionReport(context: Context, data: Map<String, Any>, companyLogoBase64: String?) {
    val info = data["vehicleInfo"] as? Map<*, *>
    val placa = info?.get("placa")?.toString() ?: info?.get("composicao1")?.toString() ?: "SP"
    printHtml(context, buildInspectionReportHtml(data, companyLogoBase64), "Laudo_Vistoria_$placa")
}

private fun enviarWhatsApp(context: Context, type: String, plate: String, company: String, info: String) {
    val msg = if (type == "reprovada") {
        "🚨 *NEXCHECK - ALERTA DE REPROVAÇÃO* 🚨\n\nOlá, prezados *$company*,\n\nO conjunto/veículo placa *$plate* foi *REPROVADO* na vistoria eletromecânica de hoje.\n\nE dessa forma, se encontra suspenso para carregamento até ser efetuado os ajustes necessários.\n\n📝 *Motivos Detalhados:*\n$info\n\n⚠️ Favor providenciar a correção para liberação.\n\nGestão de Transportes"
    } else {
        "⚠️ *NEXCHECK - ALERTA DE VENCIMENTO* ⚠️\n\nOlá *$company*,\n\nA vistoria do veículo *$plate* vencerá em breve ou está vencida.\n\nGestão de Transportes"
    }
    openWhatsApp(context, msg)
}
