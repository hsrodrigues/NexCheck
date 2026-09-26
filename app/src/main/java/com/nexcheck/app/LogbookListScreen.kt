package com.nexcheck.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookListScreen(onBack: () -> Unit, onNewLogbook: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sdfDate = SimpleDateFormat("dd/MM/yyyy", BrLocale)
    val sdfTime = SimpleDateFormat("HH:mm", BrLocale)

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

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text("Diários de Bordo", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewLogbook, containerColor = PrimaryBlue, contentColor = Color.White) {
                Icon(Icons.Default.Add, "Novo")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = searchText, onValueChange = { searchText = it.uppercase() },
                placeholder = { Text("Buscar Placa ou Motorista...") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp), leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryBlue)
            else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val filtered = logbooks.filter {
                        val placa = it["plate"]?.toString() ?: ""
                        val mot = it["driverName"]?.toString() ?: ""
                        placa.contains(searchText) || mot.contains(searchText)
                    }
                    items(filtered) { log -> LogbookCard(log, sdfDate, sdfTime) { selectedLogbook = log } }
                }
            }
        }
    }

    // MODAL DE DETALHES (IGUAL AO JS)
    selectedLogbook?.let { data ->
        val number = data["logNumber"]?.toString() ?: "SN"
        val status = data["status"]?.toString() ?: "Pendente"
        val isApproved = status == "Aprovado" || status == "Liberado"
        val badgeColor = if (isApproved) Color(0xFF16A34A) else Color(0xFFDC2626)
        val badgeBg = if (isApproved) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
        val dateFull = (data["createdAt"] as? Timestamp)?.toDate()?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale).format(it) } ?: "N/A"

        Dialog(onDismissRequest = { selectedLogbook = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = FintechBg) {
                Column {
                    TopAppBar(
                        title = { Text("Diário #$number", fontWeight = FontWeight.Black) },
                        navigationIcon = { IconButton(onClick = { selectedLogbook = null }) { Icon(Icons.Default.Close, null) } },
                        actions = { IconButton(onClick = { printLogbookReport(context, data) }) { Icon(Icons.Default.Print, null, tint = PrimaryBlue) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )

                    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Placa: ${data["plate"]}", fontWeight = FontWeight.Black, fontSize = 18.sp)
                                        Surface(color = badgeBg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))) {
                                            Text(status.uppercase(), color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text("Motorista: ${data["driverName"]}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Transportadora: ${data["carrier"]}", fontSize = 14.sp)
                                    Text("Carroceria: ${data["bodyType"] ?: "SIDER"}", fontSize = 14.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Registrado em: $dateFull", fontSize = 12.sp, color = TextMuted)
                                }
                            }
                        }

                        // LÊ A ESTRUTURA EXATA DO JS (checklistStruct)
                        item {
                            SectionTitle("ITENS VERIFICADOS")
                            val struct = data["checklistStruct"].asMapList()
                            if (struct.isNotEmpty()) {
                                struct.forEach { section ->
                                    val sTitle = section["sectionTitle"]?.toString() ?: "Geral"
                                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(sTitle.uppercase(), fontWeight = FontWeight.Black, fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                                            section["items"].asMapList().forEach { item ->
                                                val name = item["name"]?.toString() ?: ""
                                                val st = item["status"]?.toString() ?: "NA"
                                                val isOk = st == "OK"
                                                val isNa = st == "NA"
                                                val stColor = if (isOk) Color(0xFF16A34A) else if(isNa) Color.Gray else Color(0xFFDC2626)
                                                val stBg = if (isOk) Color(0xFFDCFCE7) else if(isNa) Color(0xFFF3F4F6) else Color(0xFFFEE2E2)

                                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                                    Text(name, fontSize = 13.sp, color = TextDark, modifier = Modifier.weight(1f))
                                                    Surface(color = stBg, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, stColor.copy(alpha = 0.3f))) {
                                                        Text(st, color = stColor, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                }
                                                HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                                            }
                                        }
                                    }
                                }
                            }

                            val failed = data["failedItems"].asStringList()
                            if (failed.isNotEmpty()) {
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), border = BorderStroke(1.dp, Color(0xFFFECACA)), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("REPROVAÇÕES", fontWeight = FontWeight.Black, color = Color(0xFF991B1B), fontSize = 12.sp)
                                        failed.forEach { f -> Text("• $f", fontSize = 12.sp, color = Color(0xFFB91C1C), modifier = Modifier.padding(top = 4.dp)) }
                                    }
                                }
                            }
                        }

                        item {
                            SectionTitle("ASSINATURA")
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().height(140.dp)) {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    val sig = data["signature"]?.toString() ?: ""
                                    val bmp = remember(sig) { decodeBase64ToBitmap(sig) }
                                    if (bmp != null) Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                                    else Text("Sem assinatura", color = Color.LightGray)
                                }
                            }
                            Spacer(Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogbookCard(log: Map<String, Any>, sdfD: SimpleDateFormat, sdfT: SimpleDateFormat, onClick: () -> Unit) {
    val status = log["status"]?.toString() ?: "Pendente"
    val color = if (status == "Aprovado") Color(0xFF10B981) else Color(0xFFEF4444)
    val date = (log["createdAt"] as? Timestamp)?.toDate()
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(log["plate"]?.toString() ?: "S/P", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(status.uppercase(), color = color, fontWeight = FontWeight.Black, fontSize = 10.sp)
                }
                Text(log["carrier"]?.toString() ?: "-", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceBetween) {
                    Text(log["driverName"]?.toString() ?: "N/A", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(date?.let { "${sdfD.format(it)} ${sdfT.format(it)}" } ?: "--/--", fontSize = 12.sp, color = TextMuted)
                }
            }
        }
    }
}

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