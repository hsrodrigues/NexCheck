package com.nexcheck.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendenciesScreen(
    onBack: () -> Unit,
    // 🔥 AJUSTE: Agora recebe 8 parâmetros (Tipo, Placa, Marca, Modelo, Empresa, Motorista, Cliente, Ação)
    onStartInspection: (String, String, String, String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { InspectionRepository() }
    val sdf = SimpleDateFormat("dd/MM/yyyy", BrLocale)

    var pendencies by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var searchText by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("ALL") }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

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

            val alertDaysFromNow = Calendar.getInstance().apply {
                time = today
                add(Calendar.DAY_OF_YEAR, 15)
            }.time

            val processedPendencies = mutableListOf<Map<String, Any>>()

            historyByPlate.forEach { (_, inspections) ->
                inspections.sortByDescending { (it["inspectionDate"] as? Timestamp)?.seconds ?: 0L }

                val lastInsp = inspections.first()
                val isReprovado = lastInsp["status"] == "Não Liberado" || lastInsp["status"] == "Reprovado"

                val validadeJS: Date = lastInsp["nextInspectionDate"].asDate()
                    ?: lastInsp["inspectionDate"].asDate()?.let { d -> Calendar.getInstance().apply { time = d; add(Calendar.MONTH, 6) }.time }
                    ?: Date(0)

                val statusNormalizado: String
                val colorClass: Color
                val labelStatus: String
                val bgClass: Color

                if (isReprovado) {
                    statusNormalizado = "revistoria"
                    colorClass = Color(0xFFC2410C)
                    bgClass = Color(0xFFFFEDD5)
                    labelStatus = "REPROVADO"
                } else {
                    if (validadeJS < today) {
                        statusNormalizado = "vencida"
                        colorClass = Color(0xFFB91C1C)
                        bgClass = Color(0xFFFEE2E2)
                        labelStatus = "VENCIDA"
                    } else if (validadeJS <= alertDaysFromNow) {
                        statusNormalizado = "avencer"
                        colorClass = Color(0xFFB45309)
                        bgClass = Color(0xFFFEF3C7)
                        labelStatus = "A VENCER"
                    } else {
                        statusNormalizado = "emdia"
                        colorClass = Color(0xFF15803D)
                        bgClass = Color(0xFFDCFCE7)
                        labelStatus = "EM DIA"
                    }
                }

                val mutableInsp = lastInsp.toMutableMap()
                mutableInsp["_statusNormalizado"] = statusNormalizado
                mutableInsp["_colorClass"] = colorClass
                mutableInsp["_bgClass"] = bgClass
                mutableInsp["_labelStatus"] = labelStatus
                mutableInsp["_validadeJS"] = validadeJS
                processedPendencies.add(mutableInsp)
            }

            processedPendencies.sortBy { p ->
                when (p["_statusNormalizado"]) {
                    "revistoria" -> 1
                    "vencida" -> 2
                    "avencer" -> 3
                    else -> 4
                }
            }

            pendencies = processedPendencies
            isLoading = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text("Pendências de Frotas", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = TextDark) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {

            OutlinedTextField(
                value = searchText, onValueChange = { searchText = it.uppercase() },
                placeholder = { Text("Buscar Placa ou Transportadora...") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterTab("TODOS", "ALL", activeFilter) { activeFilter = it }
                FilterTab("REVISTORIAS", "revistoria", activeFilter) { activeFilter = it }
                FilterTab("VENCIDOS", "vencida", activeFilter) { activeFilter = it }
                FilterTab("A VENCER", "avencer", activeFilter) { activeFilter = it }
                FilterTab("EM DIA", "emdia", activeFilter) { activeFilter = it }
            }

            if (isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryBlue)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    val filtered = pendencies.filter { p ->
                        val info = p["vehicleInfo"] as? Map<*, *>
                        val placa = info?.get("placa")?.toString() ?: ""
                        val company = p["company"]?.toString() ?: p["clientName"]?.toString() ?: ""
                        val matchSearch = searchText.isEmpty() || placa.contains(searchText) || company.contains(searchText)
                        val matchStatus = activeFilter == "ALL" || p["_statusNormalizado"] == activeFilter
                        matchSearch && matchStatus
                    }

                    if (filtered.isEmpty()) {
                        item { Text("Nenhuma pendência encontrada.", color = TextMuted, modifier = Modifier.padding(20.dp)) }
                    } else {
                        val revistorias = filtered.filter { it["_statusNormalizado"] == "revistoria" }
                        val vencidas = filtered.filter { it["_statusNormalizado"] == "vencida" }
                        val aVencer = filtered.filter { it["_statusNormalizado"] == "avencer" }
                        val emDia = filtered.filter { it["_statusNormalizado"] == "emdia" }

                        if (activeFilter == "ALL" || activeFilter == "revistoria") {
                            if (revistorias.isNotEmpty()) {
                                item { GroupHeader("Revistorias", revistorias.size, Color(0xFFEA580C), Color(0xFFFFEDD5)) }
                                items(revistorias) { item -> PendencyCard(item, context, onStartInspection, sdf) }
                            }
                        }

                        if (activeFilter == "ALL" || activeFilter == "vencida") {
                            if (vencidas.isNotEmpty()) {
                                item { GroupHeader("Vencidos (Prazo)", vencidas.size, Color(0xFFDC2626), Color(0xFFFEE2E2)) }
                                items(vencidas) { item -> PendencyCard(item, context, onStartInspection, sdf) }
                            }
                        }

                        if (activeFilter == "ALL" || activeFilter == "avencer") {
                            if (aVencer.isNotEmpty()) {
                                item { GroupHeader("Próximos a Vencer", aVencer.size, Color(0xFFD97706), Color(0xFFFEF3C7)) }
                                items(aVencer) { item -> PendencyCard(item, context, onStartInspection, sdf) }
                            }
                        }

                        if (activeFilter == "ALL" || activeFilter == "emdia") {
                            if (emDia.isNotEmpty()) {
                                item { GroupHeader("Em Dia", emDia.size, Color(0xFF16A34A), Color(0xFFDCFCE7)) }
                                items(emDia) { item -> PendencyCard(item, context, onStartInspection, sdf) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupHeader(title: String, count: Int, textColor: Color, bgColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${title.uppercase()} ($count)",
            fontWeight = FontWeight.Black,
            color = textColor,
            fontSize = 14.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun PendencyCard(
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
    val cliente = item["clientName"]?.toString() ?: "" // 🔥 Pegando o Cliente
    val servicos = item["servicesNeeded"]?.toString() ?: "Vencimento de Prazo"

    val ultimaData = (item["inspectionDate"] as? Timestamp)?.toDate()?.let { sdf.format(it) } ?: "-"
    val validadeObj = item["_validadeJS"] as? Date
    val vencimentoStr = validadeObj?.let { sdf.format(it) } ?: "-"

    val statusNormalizado = item["_statusNormalizado"]?.toString() ?: "emdia"
    val isReprovado = statusNormalizado == "revistoria"

    val colorClass = item["_colorClass"] as? Color ?: TextMuted
    val bgClass = item["_bgClass"] as? Color ?: InputBg
    val labelStatus = item["_labelStatus"]?.toString() ?: ""

    val btnText = if (isReprovado) "ABRIR REVISTORIA" else "INICIAR VISTORIA"
    val btnColor = if (isReprovado) Color(0xFFF97316) else PrimaryBlue

    // 🔥 A LÓGICA EXATA DO JS
    val acaoFinal = if (isReprovado) "Revistoria" else "Vistoria"

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(colorClass))

            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(placa, fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextDark)
                    Surface(color = bgClass, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colorClass.copy(alpha = 0.5f))) {
                        Text(labelStatus, color = colorClass, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Transportadora", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                        Text(if(company.isEmpty()) "N/A" else company, fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Última Insp.", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                        Text(ultimaData, fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (isReprovado) {
                            Text("Motivo", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                            Text(servicos, fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Bold, maxLines = 1)
                        } else {
                            Text("Vencimento", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                            Text(vencimentoStr, fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Black)
                        }
                    }

                    if (statusNormalizado != "emdia") {
                        IconButton(
                            onClick = {
                                val msg = if (isReprovado) "🚨 *NEXCHECK - ALERTA DE REPROVAÇÃO* 🚨\n\nOlá, prezados *$company*,\nO conjunto/veículo placa *$placa* foi *REPROVADO*." else "⚠️ *NEXCHECK - ALERTA DE VENCIMENTO* ⚠️\n\nA vistoria de *$placa* vence em breve."
                                openWhatsApp(context, msg)
                            },
                            modifier = Modifier.size(36.dp).background(Color(0xFFFEE2E2), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ZAP", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 12.dp))

                Button(
                    onClick = {
                        // 🔥 PASSANDO TUDO PARA A FRENTE
                        onStartInspection("cavalo", placa, marca, modelo, company, motorista, cliente, acaoFinal)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor)
                ) {
                    Text(btnText, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun FilterTab(label: String, value: String, activeValue: String, onClick: (String) -> Unit) {
    val isActive = value == activeValue
    Button(
        onClick = { onClick(value) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) PrimaryBlue else Color.White,
            contentColor = if (isActive) Color.White else TextMuted
        ),
        border = if (!isActive) BorderStroke(1.dp, CardBorder) else null,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}