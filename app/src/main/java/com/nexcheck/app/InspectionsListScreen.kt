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
import androidx.compose.material.icons.filled.CalendarToday
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
import java.net.URLEncoder
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
    val (statusColor, _) = toneColors(toneFor(group.combinedStatus))
    val cInfo = group.cavalo?.get("vehicleInfo") as? Map<*, *>
    val compInfo = group.composicoes.firstOrNull()?.get("vehicleInfo") as? Map<*, *>

    val placaPrincipal = cInfo?.get("placa")?.toString() ?: compInfo?.get("composicao1")?.toString() ?: "S/ CAVALO"
    val dateStr = SimpleDateFormat("dd/MM/yyyy · HH:mm", BrLocale).format(group.groupDate)
    val compCount = group.composicoes.size

    NexCard(onClick = onClick, accent = statusColor) {
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
            StatusPill(group.combinedStatus)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.clientName.ifBlank { "Sem transportadora" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!isLiberado) {
                WhatsAppButton {
                    enviarWhatsApp(context, "reprovada", placaPrincipal, group.clientName, group.cavalo?.get("servicesNeeded")?.toString() ?: "Falha técnica")
                }
            }
        }
    }
}

@Composable
private fun WhatsAppButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = com.nexcheck.app.ui.theme.NexTheme.status.dangerContainer,
            contentColor = com.nexcheck.app.ui.theme.NexTheme.status.danger
        )
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
    val vehicleType = data["vehicleType"]?.toString() ?: ""
    val isCavalo = vehicleType.contains("Cavalo")

    val placa = if (isCavalo) info?.get("placa")?.toString() ?: "-" else info?.get("composicao1")?.toString() ?: "-"
    val status = data["status"]?.toString() ?: "Pendente"
    val num = data["inspectionNumber"]?.toString() ?: "SN"
    val company = data["company"]?.toString() ?: "N/A"
    val inspectionId = data["id"]?.toString() ?: ""

    val dateObj = data["inspectionDate"]
    val dateString = if (dateObj is Timestamp) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale).format(dateObj.toDate())
    } else dateObj?.toString() ?: "Data N/A"

    val isLiberado = status == "Liberado" || status == "Aprovado"
    val borderColor = if (isLiberado) "#22c55e" else "#ef4444"
    val statusTextColor = if (isLiberado) "#16a34a" else "#dc2626"
    val signatureLabel = if (isCavalo) "Motorista" else "Responsável"

    val logoHTML = if (!companyLogoBase64.isNullOrBlank()) {
        "<img src='$companyLogoBase64' style='height: 45px; max-width: 200px; object-fit: contain;'>"
    } else {
        "<h1 style='margin:0; font-size: 24px; color: #1f2937;'>NEXCHECK</h1>"
    }

    val baseUrl = "https://app.nexcheck.com"
    val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=" + URLEncoder.encode("$baseUrl/?public_verify=$inspectionId", "UTF-8") + "&color=000000"

    // 🔥 REGRA: Próxima Inspeção só aparece se for Cavalo e se tiver data
    var nextDateHTML = ""
    if (isCavalo && data["nextInspectionDate"] != null) {
        val nd = data["nextInspectionDate"]
        val ndStr = if (nd is Timestamp) SimpleDateFormat("dd/MM/yyyy", BrLocale).format(nd.toDate()) else nd.toString()
        nextDateHTML = "<div><strong>Próxima Inspeção:</strong><br><span style='color: #2563eb; font-weight: bold;'>$ndStr</span></div>"
    }

    val vehicleInfoHTML = if (isCavalo) {
        "<div><strong>Placa:</strong><br>${info?.get("placa") ?: "-"}</div>" +
                "<div><strong>Marca/Modelo:</strong><br>${info?.get("marca") ?: "-"} / ${info?.get("modelo") ?: "-"}</div>" +
                "<div><strong>KM:</strong><br>${info?.get("km") ?: "-"}</div>" +
                "<div><strong>Motorista:</strong><br>${data["driverName"] ?: "-"}</div>" +
                "<div><strong>Transportadora:</strong><br>$company</div>" +
                nextDateHTML // INSERIDO AQUI
    } else {
        "<div><strong>Cavalo Vinculado:</strong><br><span style='display:inline-block; margin-top:4px; color:#1e40af; background:#dbeafe; padding:2px 6px; border-radius:4px; border:1px solid #bfdbfe; font-weight:bold;'>${info?.get("linkedCavaloPlate") ?: "NÃO INFORMADO"}</span></div>" +
                "<div><strong>1ª Composição:</strong><br>${info?.get("composicao1") ?: "-"}</div>" +
                "<div><strong>2ª Composição:</strong><br>${info?.get("composicao2") ?: "-"}</div>" +
                "<div><strong>3ª Composição:</strong><br>${info?.get("composicao3") ?: "-"}</div>" +
                "<div><strong>Transportadora:</strong><br>$company</div>"
    }

    var itemsHTML = "<div class='items-grid'>"
    val itemsMap = data["items"] as? Map<*, *>
    itemsMap?.forEach { (_, sectionObj) ->
        val section = sectionObj as? Map<*, *>
        val title = section?.get("title")?.toString() ?: "Seção"
        val innerItems = section?.get("items") as? Map<*, *>

        if (innerItems != null && innerItems.isNotEmpty()) {
            val hasRuim = innerItems.values.contains("ruim")
            val badge = if (hasRuim) "<span class='badge b-red'>REPROVADO</span>" else "<span class='badge b-green'>APROVADO</span>"

            itemsHTML += "<div class='section-box'><div class='section-header'><span>$title</span> $badge</div><ul class='item-list'>"
            innerItems.forEach { (itemName, statusRaw) ->
                val stHtml = when (statusRaw.toString().lowercase()) {
                    "bom" -> "<span class='badge b-green'>BOM</span>"
                    "ruim" -> "<span class='badge b-red'>RUIM</span>"
                    else -> "<span class='badge b-gray'>N/A</span>"
                }
                itemsHTML += "<li><span>$itemName</span> $stHtml</li>"
            }
            itemsHTML += "</ul></div>"
        }
    }
    itemsHTML += "</div>"

    val sigDriver = data["driverSignature"]?.toString() ?: ""
    val sigInsp = data["inspectorSignature"]?.toString() ?: ""

    val imgDriver = if (sigDriver.length > 10) "<img src='$sigDriver' class='sig-img'>" else "<p style='color:#9ca3af; font-size:10px; font-style:italic; padding:15px 0;'>Nenhuma assinatura.</p>"
    val imgInsp = if (sigInsp.length > 10) "<img src='$sigInsp' class='sig-img'>" else "<p style='color:#9ca3af; font-size:10px; font-style:italic; padding:15px 0;'>Nenhuma assinatura.</p>"

    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                @page { size: A4; margin: 10mm; }
                body { font-family: 'Segoe UI', Arial, sans-serif; color: #1f2937; margin: 0; padding: 0; font-size: 11px; }
                * { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; box-sizing: border-box; }

                /* Header */
                .header-container { display: flex; justify-content: space-between; align-items: flex-start; padding-bottom: 15px; margin-bottom: 20px; border-bottom: 4px solid $borderColor; }
                .header-left { width: 30%; display: flex; align-items: center; }
                .header-center { text-align: center; width: 40%; }
                .header-title { font-size: 18px; font-weight: 900; text-transform: uppercase; margin: 0; letter-spacing: 0.5px; }
                .header-badge { display: inline-block; background: #f3f4f6; padding: 4px 12px; border-radius: 4px; font-family: monospace; font-weight: bold; font-size: 12px; margin-top: 8px; border: 1px solid #e5e7eb; color: #4b5563; }
                .header-subtitle { font-size: 10px; color: #9ca3af; margin-top: 5px; text-transform: uppercase; font-weight: bold; }
                
                /* Lado Direito c/ QR Code */
                .header-right { text-align: right; width: 30%; display: flex; align-items: center; justify-content: flex-end; gap: 10px; }
                .status-text { font-size: 14px; font-weight: 900; color: $statusTextColor; margin:0; }
                .status-sub { font-size: 10px; color: #9ca3af; margin-top: 2px; }
                .qr-img { width: 55px; height: 55px; border: 1px solid #e5e7eb; padding: 2px; border-radius: 4px; background: white; }

                /* Veículo Info */
                .info-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 15px; background: #f9fafb; padding: 15px; border-radius: 6px; border: 1px solid #f3f4f6; margin-bottom: 20px; break-inside: avoid; }
                .info-grid div { font-size: 11px; color: #1f2937; }
                .info-grid strong { display: block; color: #4b5563; font-size: 10px; margin-bottom: 2px; text-transform: uppercase; }

                /* Checklist */
                .items-title { font-size: 14px; font-weight: bold; margin-bottom: 10px; border-top: 2px solid #e5e7eb; padding-top: 15px; }
                .items-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; margin-bottom: 20px; }
                .section-box { break-inside: avoid; margin-bottom: 10px; }
                .section-header { font-size: 11px; font-weight: bold; border-bottom: 1px solid #e5e7eb; padding-bottom: 6px; margin-bottom: 6px; display: flex; justify-content: space-between; align-items: center; background: #f9fafb; padding: 6px 8px; border-radius: 4px; color: #374151; }
                .item-list { list-style: none; padding: 0; margin: 0; }
                .item-list li { display: flex; justify-content: space-between; padding: 5px 2px; border-bottom: 1px solid #f3f4f6; font-size: 10px; color: #4b5563; }

                /* Badges */
                .badge { padding: 2px 6px; border-radius: 4px; font-size: 8px; font-weight: bold; text-transform: uppercase; }
                .b-green { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }
                .b-red { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
                .b-gray { background: #f3f4f6; color: #6b7280; border: 1px solid #e5e7eb; }

                /* Conclusão */
                .obs-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 15px; margin-top: 15px; border-top: 2px solid #e5e7eb; padding-top: 15px; break-inside: avoid; }
                .obs-box { background: #f9fafb; padding: 12px; border-radius: 6px; border: 1px solid #e5e7eb; }
                .serv-box { background: #fef2f2; padding: 12px; border-radius: 6px; border: 1px solid #fecaca; }
                .obs-box h4 { margin: 0 0 5px 0; color: #374151; font-size: 11px; font-weight: bold; }
                .serv-box h4 { margin: 0 0 5px 0; color: #991b1b; font-size: 11px; font-weight: bold; }
                .text-content { font-size: 10px; color: #4b5563; white-space: pre-wrap; word-wrap: break-word; }
                .text-danger { color: #b91c1c; font-weight: bold; }

                /* Assinaturas */
                .sig-container { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; margin-top: 30px; border-top: 2px solid #e5e7eb; padding-top: 20px; text-align: center; break-inside: avoid; }
                .sig-img { height: 40px; object-fit: contain; margin: 0 auto; display: block; }
                .sig-line { border-top: 1px solid #9ca3af; width: 60%; margin: 5px auto 0; padding-top: 5px; font-size: 9px; font-weight: bold; color: #6b7280; text-transform: uppercase; }
            </style>
        </head>
        <body>
            <div class="header-container">
                <div class="header-left">$logoHTML</div>
                
                <div class="header-center">
                    <h2 class="header-title">Relatório de Inspeção</h2>
                    <div class="header-badge">Nº $num</div>
                    <div class="header-subtitle">${data["inspectionType"] ?: "Vistoria"} • ${data["vehicleType"]}</div>
                </div>
                
                <div class="header-right">
                    <div>
                        <p class="status-text">${status.uppercase()}</p>
                        <p class="status-sub">Validação Digital</p>
                    </div>
                    <img src="$qrUrl" class="qr-img" alt="QR Code">
                </div>
            </div>
            
            <div class="info-grid">
                $vehicleInfoHTML
                <div><strong>Data da Vistoria:</strong><br>$dateString</div>
                <div><strong>Vistoriador:</strong><br>${data["inspectorName"] ?: "N/A"}</div>
            </div>

            <div class="items-title">ITENS VISTORIADOS</div>
            $itemsHTML

            <div class="obs-grid">
                <div class="obs-box">
                    <h4>Observações Gerais</h4>
                    <div class="text-content">${data["observations"]?.toString()?.ifBlank { "Nenhuma" } ?: "Nenhuma"}</div>
                </div>
                <div class="serv-box">
                    <h4>Serviços / Pendências</h4>
                    <div class="text-content text-danger">${data["servicesNeeded"]?.toString()?.ifBlank { "Nenhum apontamento" } ?: "Nenhum apontamento"}</div>
                </div>
            </div>

            <div class="sig-container">
                <div>
                    $imgDriver
                    <div class="sig-line">Assinatura do $signatureLabel</div>
                </div>
                <div>
                    $imgInsp
                    <div class="sig-line">Assinatura do Vistoriador</div>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()

    printHtml(context, htmlContent, "Laudo_Vistoria_$placa")
}

private fun enviarWhatsApp(context: Context, type: String, plate: String, company: String, info: String) {
    val msg = if (type == "reprovada") {
        "🚨 *NEXCHECK - ALERTA DE REPROVAÇÃO* 🚨\n\nOlá, prezados *$company*,\n\nO conjunto/veículo placa *$plate* foi *REPROVADO* na vistoria eletromecânica de hoje.\n\nE dessa forma, se encontra suspenso para carregamento até ser efetuado os ajustes necessários.\n\n📝 *Motivos Detalhados:*\n$info\n\n⚠️ Favor providenciar a correção para liberação.\n\nGestão de Transportes"
    } else {
        "⚠️ *NEXCHECK - ALERTA DE VENCIMENTO* ⚠️\n\nOlá *$company*,\n\nA vistoria do veículo *$plate* vencerá em breve ou está vencida.\n\nGestão de Transportes"
    }
    openWhatsApp(context, msg)
}
