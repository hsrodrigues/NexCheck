package com.nexcheck.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { InspectionRepository() }

    var schedules by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var allInspections by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var pendencies by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var carriersList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var currentMonth by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }

    // Controle do Modal
    var showModal by remember { mutableStateOf(false) }
    var selectedSchedule by remember { mutableStateOf<Map<String, Any>?>(null) }
    var preSelectedDate by remember { mutableStateOf<Date?>(null) }

    var autoFillPlate by remember { mutableStateOf("") }
    var autoFillCompany by remember { mutableStateOf("") }
    var autoFillNotes by remember { mutableStateOf("") }
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    // ==============================================================
    // LÓGICA DE PENDÊNCIAS (CÓPIA FIEL DO JS)
    // ==============================================================
    fun calculatePendencies(inspections: List<Map<String, Any>>) {
        val historyByPlate = mutableMapOf<String, MutableList<Map<String, Any>>>()
        inspections.filter { it["vehicleType"]?.toString()?.contains("Cavalo") == true && (it["vehicleInfo"] as? Map<*, *>)?.get("placa") != null }
            .forEach { insp ->
                val plate = (insp["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() ?: return@forEach
                historyByPlate.getOrPut(plate) { mutableListOf() }.add(insp)
            }

        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
        val alertDaysFromNow = Calendar.getInstance().apply { time = today; add(Calendar.DAY_OF_YEAR, 15) }.time
        val calculatedPendencies = mutableListOf<Map<String, Any>>()

        historyByPlate.forEach { (_, history) ->
            history.sortByDescending { (it["inspectionDate"] as? Timestamp)?.seconds ?: 0L }
            val lastInsp = history.first()
            val isReprovado = lastInsp["status"] == "Não Liberado" || lastInsp["status"] == "Reprovado"

            val validadeJS: Date = lastInsp["nextInspectionDate"].asDate()
                ?: lastInsp["inspectionDate"].asDate()?.let { d -> Calendar.getInstance().apply { time = d; add(Calendar.MONTH, 6) }.time }
                ?: Date(0)

            val statusNormalizado = if (isReprovado) "Vencida" else if (validadeJS < today) "Vencida" else if (validadeJS <= alertDaysFromNow) "A Vencer" else "Em Dia"

            val mutableInsp = lastInsp.toMutableMap()
            mutableInsp["pendencyStatus"] = statusNormalizado
            mutableInsp["calculatedNextDate"] = validadeJS
            calculatedPendencies.add(mutableInsp)
        }
        pendencies = calculatedPendencies
    }

    // ==============================================================
    // ROBÔ: AUTO-EXPIRAR AGENDAMENTOS
    // ==============================================================
    fun runAutoExpire(loadedSchedules: List<Map<String, Any>>) {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
        loadedSchedules.forEach { s ->
            if (s["status"] == "Agendado" && s["originStatus"] != "Vencida") {
                val sDate = (s["scheduledDate"] as? Timestamp)?.toDate()
                if (sDate != null) {
                    val sDateMidnight = Calendar.getInstance().apply { time = sDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
                    val diffDays = (today.time - sDateMidnight.time) / (1000 * 60 * 60 * 24)
                    if (diffDays >= 1) {
                        // Só marca como vencida; não reescreve placa, notas e inspetor do agendamento
                        repository.updateScheduleOriginStatus(s["id"].toString(), "Vencida")
                    }
                }
            }
        }
    }

    fun loadData() {
        isLoading = true
        repository.getCarriers { carriersList = it }
        repository.getAllInspections(limit = null) { insps ->
            allInspections = insps
            calculatePendencies(insps)
            repository.getSchedules { scheds ->
                // FILTRO: Ignora o que já foi Realizado. Só mostra Agendado ou Não Realizado (Vencido)
                val activeScheds = scheds.filter {
                    val status = it["status"]?.toString() ?: ""
                    status == "Agendado" || status == "Não Realizado"
                }
                schedules = activeScheds
                runAutoExpire(activeScheds)
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text("Agenda de Vistorias", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { selectedSchedule = null; preSelectedDate = Date(); autoFillPlate = ""; autoFillCompany = ""; autoFillNotes = ""; showModal = true },
                containerColor = PrimaryBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Novo")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // CABEÇALHO DO MÊS
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val prev = currentMonth.clone() as Calendar
                    prev.add(Calendar.MONTH, -1)
                    currentMonth = prev
                }) { Icon(Icons.Default.ChevronLeft, "Anterior") }

                val monthName = SimpleDateFormat("MMMM yyyy", BrLocale).format(currentMonth.time).uppercase()
                Text(monthName, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextDark)

                IconButton(onClick = {
                    val next = currentMonth.clone() as Calendar
                    next.add(Calendar.MONTH, 1)
                    currentMonth = next
                }) { Icon(Icons.Default.ChevronRight, "Próximo") }
            }

            // ==============================================================
            // CALENDÁRIO GRID
            // ==============================================================
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEach { day ->
                            Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = CardBorder)

                    val dayKeyFormat = SimpleDateFormat("ddMMyyyy", BrLocale)
                    val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val firstDayOfWeek = currentMonth.get(Calendar.DAY_OF_WEEK) - 1
                    var dayCounter = 1

                    for (row in 0..5) {
                        if (dayCounter > daysInMonth) break
                        Row(Modifier.fillMaxWidth().height(65.dp)) {
                            for (col in 0..6) {
                                if (row == 0 && col < firstDayOfWeek || dayCounter > daysInMonth) {
                                    Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF9FAFB)))
                                } else {
                                    val currentDay = dayCounter
                                    val cellDate = currentMonth.clone() as Calendar
                                    cellDate.set(Calendar.DAY_OF_MONTH, currentDay)
                                    val fmtDate = dayKeyFormat.format(cellDate.time)

                                    val daySchedules = schedules.filter { s ->
                                        val d = (s["scheduledDate"] as? Timestamp)?.toDate()
                                        d != null && dayKeyFormat.format(d) == fmtDate
                                    }

                                    val dayPendencies = pendencies.filter { p ->
                                        val d = p["calculatedNextDate"] as? Date
                                        val pPlate = (p["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() ?: ""
                                        val hasSchedule = schedules.any { it["vehiclePlate"]?.toString() == pPlate }
                                        d != null && dayKeyFormat.format(d) == fmtDate && !hasSchedule
                                    }

                                    Box(
                                        Modifier.weight(1f).fillMaxHeight()
                                            .border(0.5.dp, Color(0xFFF3F4F6))
                                            .clickable { preSelectedDate = cellDate.time; selectedSchedule = null; autoFillPlate = ""; autoFillCompany = ""; autoFillNotes = ""; showModal = true }
                                            .padding(2.dp)
                                    ) {
                                        Text(currentDay.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd))

                                        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                                            var itemShowCount = 0

                                            daySchedules.take(2).forEach { ev ->
                                                val status = ev["originStatus"]?.toString() ?: "Em Dia"
                                                val color = when(status) {
                                                    "Vencida" -> Color(0xFFDC2626)
                                                    "A Vencer" -> Color(0xFFF59E0B)
                                                    else -> Color(0xFF10B981)
                                                }
                                                Box(Modifier.fillMaxWidth().padding(top = 2.dp).background(color, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp)) {
                                                    Text(ev["vehiclePlate"]?.toString() ?: "", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                }
                                                itemShowCount++
                                            }

                                            if (itemShowCount < 2) {
                                                dayPendencies.take(2 - itemShowCount).forEach { p ->
                                                    val status = p["pendencyStatus"]?.toString() ?: "Em Dia"
                                                    val color = when(status) {
                                                        "Vencida" -> Color(0xFFDC2626)
                                                        "A Vencer" -> Color(0xFFF59E0B)
                                                        else -> Color(0xFF10B981)
                                                    }
                                                    val pPlate = (p["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() ?: ""
                                                    val pCompany = p["company"]?.toString() ?: p["clientName"]?.toString() ?: ""
                                                    val sourceNum = p["inspectionNumber"]?.toString() ?: ""

                                                    Box(Modifier.fillMaxWidth().padding(top = 2.dp).background(color.copy(alpha=0.2f), RoundedCornerShape(2.dp))
                                                        .border(1.dp, color, RoundedCornerShape(2.dp)).padding(horizontal = 2.dp)
                                                        .clickable {
                                                            preSelectedDate = cellDate.time
                                                            selectedSchedule = null
                                                            autoFillPlate = pPlate
                                                            autoFillCompany = pCompany
                                                            autoFillNotes = "Agendamento criado a partir da pendência da vistoria Nº$sourceNum"
                                                            showModal = true
                                                        }
                                                    ) {
                                                        Text(pPlate, color = color, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                                    }
                                                }
                                            }

                                            val extras = (daySchedules.size + dayPendencies.size) - 2
                                            if (extras > 0) {
                                                Text("+$extras", fontSize = 7.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                            }
                                        }
                                    }
                                    dayCounter++
                                }
                            }
                        }
                    }
                }
            }

            // LISTA DE PRÓXIMOS
            Text("Próximos Agendamentos", fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(16.dp))

            if (isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp), color = PrimaryBlue)
            } else {
                val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.time
                val upcoming = schedules.filter { ((it["scheduledDate"] as? Timestamp)?.toDate() ?: Date(0)) >= today }
                    .sortedBy { (it["scheduledDate"] as? Timestamp)?.seconds }

                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(upcoming.take(15)) { schedule ->
                        ScheduleListItem(schedule) {
                            selectedSchedule = schedule
                            autoFillPlate = ""; autoFillCompany = ""; autoFillNotes = ""
                            showModal = true
                        }
                    }
                }
            }
        }
    }

    // =======================================================
    // MODAL DE AGENDAMENTO
    // =======================================================
    if (showModal) {
        var formPlate by remember { mutableStateOf(if(autoFillPlate.isNotEmpty()) autoFillPlate else selectedSchedule?.get("vehiclePlate")?.toString() ?: "") }
        var formCarrier by remember { mutableStateOf(if(autoFillCompany.isNotEmpty()) autoFillCompany else selectedSchedule?.get("clientName")?.toString() ?: "") }
        var formNotes by remember { mutableStateOf(if(autoFillNotes.isNotEmpty()) autoFillNotes else selectedSchedule?.get("notes")?.toString() ?: "") }
        var expandedCarrier by remember { mutableStateOf(false) } // Controle do Dropdown

        val initialDate = (selectedSchedule?.get("scheduledDate") as? Timestamp)?.toDate() ?: preSelectedDate ?: Date()
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", BrLocale)
        val sdfTime = SimpleDateFormat("HH:mm", BrLocale)
        var formDateStr by remember { mutableStateOf(sdfDate.format(initialDate)) }
        var formTimeStr by remember { mutableStateOf(sdfTime.format(if(selectedSchedule != null) initialDate else Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,8);set(Calendar.MINUTE,0)}.time)) }

        var isSaving by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showModal = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (selectedSchedule != null) "Editar Agendamento" else "Novo Agendamento", fontWeight = FontWeight.Black, fontSize = 18.sp, color = PrimaryBlue, modifier = Modifier.padding(bottom = 16.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = formPlate,
                            onValueChange = { formPlate = it.uppercase() },
                            label = { Text("Placa") },
                            modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                val match = allInspections.firstOrNull {
                                    (it["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() == formPlate ||
                                            (it["vehicleInfo"] as? Map<*, *>)?.get("composicao1")?.toString() == formPlate
                                }
                                if (match != null) {
                                    val lastDate = (match["inspectionDate"] as? Timestamp)?.toDate() ?: Date()
                                    val suggest = Calendar.getInstance().apply { time = lastDate; add(Calendar.MONTH, 6); set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0) }.time
                                    formDateStr = sdfDate.format(suggest)
                                    formTimeStr = "08:00"
                                    formCarrier = match["company"]?.toString() ?: match["clientName"]?.toString() ?: formCarrier
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).background(Color(0xFFEFF6FF), CircleShape)
                        ) {
                            Icon(Icons.Default.AutoAwesome, "Auto Sugerir", tint = PrimaryBlue)
                        }
                    }

                    // ==============================================================
                    // AUTOCOMPLETE TRANSPORTADORA (CORRIGIDO PARA O PADRÃO DA VISTORIA)
                    // ==============================================================
                    ExposedDropdownMenuBox(
                        expanded = expandedCarrier,
                        onExpandedChange = { expandedCarrier = !expandedCarrier }
                    ) {
                        OutlinedTextField(
                            value = formCarrier,
                            onValueChange = {
                                formCarrier = it.uppercase()
                                expandedCarrier = true
                            },
                            label = { Text("Transportadora") },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth().padding(bottom = 8.dp),
                            singleLine = true
                        )

                        val suggestions = carriersList.filter { it.contains(formCarrier, ignoreCase = true) }
                        if (suggestions.isNotEmpty() && formCarrier.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = expandedCarrier,
                                onDismissRequest = { expandedCarrier = false }
                            ) {
                                suggestions.take(5).forEach { selection ->
                                    DropdownMenuItem(
                                        text = { Text(selection, fontWeight = FontWeight.Bold, color = TextDark) },
                                        onClick = {
                                            formCarrier = selection
                                            expandedCarrier = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = formDateStr, onValueChange = { formDateStr = it }, label = { Text("Data (DD/MM)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = formTimeStr, onValueChange = { formTimeStr = it }, label = { Text("Hora (HH:MM)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }

                    OutlinedTextField(value = formNotes, onValueChange = { formNotes = it }, label = { Text("Observações / Motivo") }, modifier = Modifier.fillMaxWidth().height(100.dp).padding(bottom = 16.dp))

                    if (isSaving) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryBlue) }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (selectedSchedule != null) {
                            TextButton(onClick = {
                                isSaving = true
                                repository.deleteSchedule(
                                    selectedSchedule!!["id"].toString(),
                                    onSuccess = { isSaving = false; showModal = false; loadData() },
                                    onError = { erro -> isSaving = false; Toast.makeText(context, "Erro ao excluir: $erro", Toast.LENGTH_LONG).show() }
                                )
                            }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("EXCLUIR") }
                        }

                        TextButton(onClick = { showModal = false }) { Text("CANCELAR", color = TextMuted) }

                        Button(enabled = !isSaving, onClick = {
                            if (formPlate.isBlank()) {
                                Toast.makeText(context, "Informe a placa.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSaving = true
                            try {
                                // Não-leniente: "32/13/2026" é erro, e não "01/02/2027"
                                val parser = SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale).apply { isLenient = false }
                                val finalDate = parser.parse("${formDateStr.trim()} ${formTimeStr.trim()}") ?: throw IllegalArgumentException()
                                var finalCarrier = formCarrier
                                val match = allInspections.firstOrNull {
                                    (it["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() == formPlate ||
                                            (it["vehicleInfo"] as? Map<*, *>)?.get("composicao1")?.toString() == formPlate
                                }
                                if (match != null && match["company"] != null) {
                                    finalCarrier = match["company"].toString()
                                }

                                val diffDays = (finalDate.time - Date().time) / (1000 * 60 * 60 * 24)
                                val originStatus = if (diffDays < 0) "Vencida" else if (diffDays <= 15) "A Vencer" else "Em Dia"

                                repository.saveSchedule(selectedSchedule?.get("id")?.toString(), formPlate, finalCarrier, finalDate, formNotes, originStatus, onSuccess = {
                                    isSaving = false; showModal = false; loadData()
                                }, onError = { erro ->
                                    isSaving = false
                                    Toast.makeText(context, "Erro ao salvar: $erro", Toast.LENGTH_LONG).show()
                                })
                            } catch (e: Exception) {
                                isSaving = false
                                Toast.makeText(context, "Data ou hora inválida. Use DD/MM/AAAA e HH:MM.", Toast.LENGTH_LONG).show()
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                            Text("SALVAR", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleListItem(schedule: Map<String, Any>, onClick: () -> Unit) {
    val plate = schedule["vehiclePlate"]?.toString() ?: "S/P"
    val carrier = schedule["clientName"]?.toString() ?: "S/ Empresa"
    val date = (schedule["scheduledDate"] as? Timestamp)?.toDate() ?: Date()
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale).format(date)
    val status = schedule["originStatus"]?.toString() ?: "Em Dia"

    val color = when(status) {
        "Vencida" -> Color(0xFFDC2626)
        "A Vencer" -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(12.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(plate, fontWeight = FontWeight.Black, fontSize = 14.sp, color = TextDark)
                    Text(dateStr, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalShipping, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(carrier, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}