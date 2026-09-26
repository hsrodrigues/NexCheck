package com.nexcheck.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
fun LogbookListScreen(onBack: () -> Unit, onNewLogbook: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sdfFull = remember { SimpleDateFormat("dd/MM/yyyy · HH:mm", BrLocale) }

    var logbooks by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var selectedLogbook by remember { mutableStateOf<Map<String, Any>?>(null) }

    // Escuta em tempo real e remove o listener ao sair da tela
    DisposableEffect(Unit) {
        val registration = db.collection("daily_logbooks").orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) logbooks = snapshot.documents.map { it.data.orEmpty() + ("id" to it.id) }
                isLoading = false
            }
        onDispose { registration.remove() }
    }

    val filtered = remember(logbooks, searchText) {
        logbooks.filter {
            val placa = it["plate"]?.toString() ?: ""
            val mot = it["driverName"]?.toString() ?: ""
            placa.contains(searchText, true) || mot.contains(searchText, true)
        }
    }

    NexScaffold(
        title = "Diários de bordo",
        subtitle = "Últimos 50 registros",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewLogbook,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo diário") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SearchField(searchText, { searchText = it }, "Buscar placa ou motorista") }
            when {
                isLoading -> item { LoadingState() }
                filtered.isEmpty() -> item { EmptyState(Icons.AutoMirrored.Filled.FactCheck, "Nenhum diário encontrado", "Registre um novo diário pelo botão abaixo.") }
                else -> items(filtered, key = { it["id"].toString() }) { log ->
                    LogbookCard(log, sdfFull) { selectedLogbook = log }
                }
            }
        }
    }

    // --- DETALHES ---
    selectedLogbook?.let { data ->
        val status = data["status"]?.toString() ?: "Pendente"
        val dateFull = (data["createdAt"] as? Timestamp)?.toDate()?.let { sdfFull.format(it) } ?: "—"

        FullScreenDetail(
            title = "Diário Nº ${data["logNumber"] ?: "—"}",
            subtitle = dateFull,
            onClose = { selectedLogbook = null },
            actions = {
                IconButton(onClick = { printLogbookReport(context, data) }) { Icon(Icons.Default.Print, contentDescription = "Imprimir") }
            }
        ) {
            item {
                NexCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlateTag(data["plate"]?.toString() ?: "", large = true)
                        Spacer(Modifier.weight(1f))
                        StatusPill(status)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        InfoItem("Motorista", data["driverName"]?.toString() ?: "", Modifier.weight(1f))
                        InfoItem("Transportadora", data["carrier"]?.toString() ?: "", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        InfoItem("Composição", data["compositionType"]?.toString() ?: "PADRÃO", Modifier.weight(1f))
                        InfoItem("Carroceria", data["bodyType"]?.toString() ?: "SIDER", Modifier.weight(1f))
                    }
                }
            }

            val failed = data["failedItems"].asStringList()
            if (failed.isNotEmpty()) {
                item {
                    Surface(color = NexTheme.status.dangerContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Reprovações", style = MaterialTheme.typography.titleSmall, color = NexTheme.status.danger)
                            failed.forEach { f ->
                                Text("• $f", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }

            val struct = data["checklistStruct"].asMapList()
            if (struct.isNotEmpty()) item { SectionTitle("Itens verificados", Modifier.padding(top = 8.dp)) }
            struct.forEach { section ->
                item {
                    NexCard {
                        Text(section["sectionTitle"]?.toString() ?: "Geral", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        section["items"].asMapList().forEachIndexed { index, itm ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(itm["name"]?.toString() ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                val st = itm["status"]?.toString() ?: "NA"
                                StatusPill(if (st == "NA") "N/A" else st)
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Assinatura do motorista", Modifier.padding(top = 8.dp)) }
            item {
                val sig = data["signature"]?.toString() ?: ""
                val bmp = remember(sig) { decodeBase64ToBitmap(sig) }
                NexCard {
                    // Fundo branco fixo: a assinatura é traço preto em PNG transparente
                    Box(
                        Modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.shapes.medium).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) Image(bitmap = bmp, contentDescription = "Assinatura", modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                        else Text("Sem assinatura", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogbookCard(log: Map<String, Any>, sdf: SimpleDateFormat, onClick: () -> Unit) {
    val status = log["status"]?.toString() ?: "Pendente"
    val date = (log["createdAt"] as? Timestamp)?.toDate()
    NexCard(onClick = onClick, accent = toneColors(toneFor(status)).first) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlateTag(log["plate"]?.toString() ?: "")
            Spacer(Modifier.width(8.dp))
            Text(
                "Nº ${log["logNumber"] ?: "—"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            StatusPill(status)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            InfoItem("Motorista", log["driverName"]?.toString() ?: "", Modifier.weight(1f))
            InfoItem("Transportadora", log["carrier"]?.toString() ?: "", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(date?.let { sdf.format(it) } ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// =========================================================================
// IMPRESSÃO
// =========================================================================

private fun printLogbookReport(context: Context, data: Map<String, Any>) {
    val db = FirebaseFirestore.getInstance()

    // 1. Busca a Logo no Firebase antes de gerar o HTML
    db.collection("settings").document("branding").get().addOnSuccessListener { doc ->
        val logoBase64 = doc.getString("companyLogoBase64") ?: ""

        // Extração de dados (seguindo suas variáveis do JS)
        val num = data["logNumber"]?.toString() ?: "SN"
        val placa = data["plate"]?.toString() ?: "S/P"
        val status = data["status"]?.toString() ?: "Pendente"
        val driverName = data["driverName"]?.toString() ?: "N/A"
        val carrier = data["carrier"]?.toString() ?: "N/A"
        val compositionType = data["compositionType"]?.toString() ?: "PADRÃO"

        val dateObj = (data["createdAt"] as? Timestamp)?.toDate()
        val dateStr = dateObj?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale).format(it) } ?: "Data N/A"

        // Cores baseadas no seu CSS
        val isApproved = status == "Aprovado"
        val statusColor = if (isApproved) "#16a34a" else "#dc2626"
        val statusBg = if (isApproved) "#dcfce7" else "#fee2e2"

        // Logo HTML (idêntico ao JS)
        val logoHTML = if (logoBase64.isNotEmpty()) {
            "<img src='$logoBase64' style='height: 50px; max-width: 250px; object-fit: contain;'>"
        } else {
            "<div style='line-height: 1;'><span style='font-size: 24px; font-weight: 900; color: #111; letter-spacing: -1px;'>NEXCHECK</span><br><span style='font-size: 8px; font-weight: 700; color: #2563eb; letter-spacing: 2px; text-transform: uppercase;'>Fleet Management</span></div>"
        }

        // 2. Montagem do Checklist (GRID LIMPO igual ao seu JS)
        var checklistHTML = ""
        data["checklistStruct"].asMapList().forEach { section ->
            checklistHTML += """
                <div class="section-container">
                    <div class="section-title">${section["sectionTitle"] ?: "Verificação"}</div>
                    <div class="items-grid">
            """
            section["items"].asMapList().forEach { itm ->
                val name = itm["name"]?.toString() ?: ""
                val st = itm["status"]?.toString() ?: "NA"
                val badgeClass = when(st) {
                    "OK" -> "badge-ok"
                    "NOK" -> "badge-nok"
                    else -> "badge-na"
                }
                checklistHTML += """
                    <div class="item-row">
                        <span class="item-name">$name</span>
                        <span class="badge $badgeClass">$st</span>
                    </div>
                """
            }
            checklistHTML += "</div></div>"
        }

        // 3. INJEÇÃO DO SEU HTML E CSS ORIGINAL
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    @page { size: A4; margin: 10mm; }
                    body { font-family: 'Segoe UI', Arial, sans-serif; margin: 0; padding: 0; color: #333; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
                    
                    /* Layout Geral */
                    .header { display: flex; justify-content: space-between; align-items: flex-end; border-bottom: 3px solid #1f2937; padding-bottom: 10px; margin-bottom: 20px; }
                    .title-block { text-align: right; }
                    .main-title { font-size: 20px; font-weight: 900; margin: 0; text-transform: uppercase; color: #1f2937; }
                    .doc-number { display: inline-block; background: #f3f4f6; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; border: 1px solid #e5e7eb; margin-top: 4px; }
                    
                    /* Grids de Dados */
                    .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 20px; }
                    .info-box { border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden; }
                    .box-header { background: #f3f4f6; padding: 6px 10px; font-size: 10px; font-weight: bold; text-transform: uppercase; border-bottom: 1px solid #d1d5db; color: #4b5563; }
                    .box-content { padding: 10px; font-size: 11px; display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
                    
                    .field-label { display: block; font-size: 9px; color: #9ca3af; font-weight: bold; text-transform: uppercase; margin-bottom: 1px; }
                    .field-value { display: block; font-weight: bold; color: #111; font-size: 12px; }

                    /* Status Global */
                    .status-banner { text-align: center; padding: 10px; border-radius: 6px; margin-bottom: 25px; border: 2px solid $statusColor; background-color: $statusBg; color: $statusColor; }
                    .status-title { font-size: 10px; font-weight: bold; text-transform: uppercase; letter-spacing: 1px; opacity: 0.8; }
                    .status-val { font-size: 24px; font-weight: 900; text-transform: uppercase; line-height: 1; }

                    /* Checklist */
                    .section-container { margin-bottom: 15px; break-inside: avoid; page-break-inside: avoid; border: 1px solid #e5e7eb; border-radius: 6px; overflow: hidden; }
                    .section-title { background: #1f2937; color: white; font-size: 10px; font-weight: bold; text-transform: uppercase; padding: 6px 10px; }
                    
                    .items-grid { display: grid; grid-template-columns: 1fr 1fr; }
                    .item-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 10px; border-bottom: 1px solid #f3f4f6; border-right: 1px solid #f3f4f6; font-size: 10px; }
                    .item-row:nth-child(even) { background-color: #fafafa; }
                    
                    .item-name { font-weight: 500; color: #374151; text-transform: uppercase; }
                    
                    /* Badges */
                    .badge { padding: 2px 6px; border-radius: 3px; font-weight: bold; font-size: 9px; min-width: 25px; text-align: center; }
                    .badge-ok { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }
                    .badge-nok { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
                    .badge-na { background: #f3f4f6; color: #6b7280; border: 1px solid #e5e7eb; }

                    /* Assinatura */
                    .signature-block { margin-top: 30px; text-align: center; width: 40%; margin-left: auto; margin-right: auto; break-inside: avoid; }
                    .sig-img { height: 50px; display: block; margin: 0 auto 5px auto; }
                    .sig-line { border-top: 1px solid #9ca3af; padding-top: 5px; }
                    .sig-text { font-size: 10px; font-weight: bold; color: #6b7280; text-transform: uppercase; }
                    
                    .footer { margin-top: 30px; text-align: center; font-size: 8px; color: #9ca3af; border-top: 1px solid #f3f4f6; padding-top: 5px; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div>$logoHTML</div>
                    <div class="title-block">
                        <h2 class="main-title">CheckList Pré-Carregamento</h2>
                        <div class="doc-number">#$num</div>
                    </div>
                </div>

                <div class="info-grid">
                    <div class="info-box">
                        <div class="box-header">Dados do Veículo</div>
                        <div class="box-content">
                            <div><span class="field-label">Placa</span><span class="field-value">$placa</span></div>
                            <div><span class="field-label">Tipo</span><span class="field-value" style="color:#2563eb">$compositionType</span></div>
                            <div style="grid-column: span 2;"><span class="field-label">Transportadora</span><span class="field-value">$carrier</span></div>
                        </div>
                    </div>
                    <div class="info-box">
                        <div class="box-header">Detalhes da Operação</div>
                        <div class="box-content">
                            <div><span class="field-label">Motorista</span><span class="field-value">$driverName</span></div>
                            <div><span class="field-label">Data</span><span class="field-value">$dateStr</span></div>
                            <div style="grid-column: span 2;"><span class="field-label">Assinado Por</span><span class="field-value text-gray-500">Motorista Responsável</span></div>
                        </div>
                    </div>
                </div>

                <div class="status-banner">
                    <div class="status-title">Resultado da Avaliação</div>
                    <div class="status-val">$status</div>
                </div>

                $checklistHTML

                <div class="signature-block">
                    ${if (data["signature"]?.toString()?.isNotEmpty() == true) "<img src='${data["signature"]}' class='sig-img'>" else "<div style='height:30px'></div>"}
                    <div class="sig-line">
                        <p class="sig-text">Assinatura do Motorista</p>
                    </div>
                </div>
                <p class="sig-text" style="font-size: 10px; color: #adb1ba; text-align: center; margin-top: 3px;">
                    Declaro, para os devidos fins, que realizei pessoalmente a inspeção dos itens constantes neste checklist, atestando a integridade e veracidade das informações aqui prestadas.
                </p>

                <div class="footer">
                    Documento gerado eletronicamente pelo sistema NexCheck • ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", BrLocale).format(Date())}
                </div>
            </body>
            </html>
        """.trimIndent()

        // 4. Executa a impressão usando WebView nativo
        printHtml(context, htmlContent, "Diario_Bordo_$placa")
    }.addOnFailureListener {
        Toast.makeText(context, "Erro ao preparar impressão. Verifique a conexão.", Toast.LENGTH_SHORT).show()
    }
}