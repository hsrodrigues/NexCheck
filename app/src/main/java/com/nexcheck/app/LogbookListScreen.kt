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
    // Busca a logo da empresa antes de gerar o relatório
    FirebaseFirestore.getInstance().collection("settings").document("branding").get()
        .addOnSuccessListener { doc ->
            val placa = data["plate"]?.toString() ?: "SP"
            printHtml(context, buildLogbookReportHtml(data, doc.getString("companyLogoBase64")), "Diario_Bordo_$placa")
        }
        .addOnFailureListener {
            Toast.makeText(context, "Erro ao preparar impressão. Verifique a conexão.", Toast.LENGTH_SHORT).show()
        }
}
