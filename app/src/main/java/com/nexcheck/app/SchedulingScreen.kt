package com.nexcheck.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import com.nexcheck.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

// "Vencida" / "A Vencer" / "Em Dia" -> tom visual
private fun scheduleTone(status: String) = when (status) {
    "Vencida" -> StatusTone.Danger
    "A Vencer" -> StatusTone.Warning
    else -> StatusTone.Success
}

private fun sameDay(a: Date, b: Date): Boolean {
    val ca = Calendar.getInstance().apply { time = a }
    val cb = Calendar.getInstance().apply { time = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

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
    var selectedDay by remember { mutableStateOf(Date()) }

    // Controle do Modal
    var showModal by remember { mutableStateOf(false) }
    var selectedSchedule by remember { mutableStateOf<Map<String, Any>?>(null) }
    var preSelectedDate by remember { mutableStateOf<Date?>(null) }

    var autoFillPlate by remember { mutableStateOf("") }
    var autoFillCompany by remember { mutableStateOf("") }
    var autoFillNotes by remember { mutableStateOf("") }

    // ==============================================================
    // LÓGICA DE PENDÊNCIAS (mesma regra do sistema web)
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

        pendencies = historyByPlate.values.map { history ->
            history.sortByDescending { (it["inspectionDate"] as? Timestamp)?.seconds ?: 0L }
            val lastInsp = history.first()
            val isReprovado = lastInsp["status"] == "Não Liberado" || lastInsp["status"] == "Reprovado"

            val validade: Date = lastInsp["nextInspectionDate"].asDate()
                ?: lastInsp["inspectionDate"].asDate()?.let { d -> Calendar.getInstance().apply { time = d; add(Calendar.MONTH, 6) }.time }
                ?: Date(0)

            val statusNormalizado = if (isReprovado) "Vencida" else if (validade < today) "Vencida" else if (validade <= alertDaysFromNow) "A Vencer" else "Em Dia"
            lastInsp + mapOf("pendencyStatus" to statusNormalizado, "calculatedNextDate" to validade)
        }
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
                // Ignora o que já foi Realizado. Só mostra Agendado ou Não Realizado (Vencido)
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

    fun openNew(date: Date, plate: String = "", company: String = "", notes: String = "") {
        selectedSchedule = null
        preSelectedDate = date
        autoFillPlate = plate; autoFillCompany = company; autoFillNotes = notes
        showModal = true
    }

    fun openEdit(schedule: Map<String, Any>) {
        selectedSchedule = schedule
        autoFillPlate = ""; autoFillCompany = ""; autoFillNotes = ""
        showModal = true
    }

    // Pendências sem agendamento para a placa aparecem como "sugestões" no calendário
    val scheduledPlates = remember(schedules) { schedules.mapNotNull { it["vehiclePlate"]?.toString() }.toSet() }
    val openPendencies = remember(pendencies, scheduledPlates) {
        pendencies.filter { p -> (p["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() !in scheduledPlates }
    }

    val daySchedules = schedules.filter { s -> (s["scheduledDate"] as? Timestamp)?.toDate()?.let { sameDay(it, selectedDay) } == true }
        .sortedBy { (it["scheduledDate"] as? Timestamp)?.seconds }
    val dayPendencies = openPendencies.filter { p -> (p["calculatedNextDate"] as? Date)?.let { sameDay(it, selectedDay) } == true }

    val todayStart = remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time }
    val upcoming = schedules.filter { ((it["scheduledDate"] as? Timestamp)?.toDate() ?: Date(0)) >= todayStart }
        .sortedBy { (it["scheduledDate"] as? Timestamp)?.seconds }
        .take(15)

    NexScaffold(
        title = "Agenda de vistorias",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openNew(selectedDay) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Agendar") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                MonthCalendar(
                    month = currentMonth,
                    selectedDay = selectedDay,
                    schedules = schedules,
                    pendencies = openPendencies,
                    onMonthChange = { delta ->
                        currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, delta) }
                    },
                    onToday = {
                        currentMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
                        selectedDay = Date()
                    },
                    onSelectDay = { selectedDay = it }
                )
            }

            // --- DIA SELECIONADO ---
            item {
                SectionTitle(
                    SimpleDateFormat("EEEE, d 'de' MMMM", BrLocale).format(selectedDay),
                    Modifier.padding(top = 8.dp),
                    trailing = {
                        TextButton(onClick = { openNew(selectedDay) }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Agendar neste dia", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
            }
            if (isLoading) {
                item { LoadingState() }
            } else if (daySchedules.isEmpty() && dayPendencies.isEmpty()) {
                item {
                    NexCard {
                        Text("Nenhum agendamento neste dia.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(daySchedules, key = { "d-" + it["id"].toString() }) { schedule ->
                    ScheduleListItem(schedule) { openEdit(schedule) }
                }
                items(dayPendencies) { p ->
                    val pPlate = (p["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() ?: ""
                    val pCompany = p["company"]?.toString() ?: p["clientName"]?.toString() ?: ""
                    val status = p["pendencyStatus"]?.toString() ?: "Em Dia"
                    NexCard(accent = toneColors(scheduleTone(status)).first) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlateTag(pPlate)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Vencimento sem agendamento", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(pCompany, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledTonalButton(onClick = {
                                openNew(selectedDay, pPlate, pCompany, "Agendamento criado a partir da pendência da vistoria Nº${p["inspectionNumber"] ?: ""}")
                            }) { Text("Agendar") }
                        }
                    }
                }
            }

            // --- PRÓXIMOS ---
            item { SectionTitle("Próximos agendamentos", Modifier.padding(top = 12.dp)) }
            if (!isLoading && upcoming.isEmpty()) {
                item { EmptyState(Icons.Default.EventAvailable, "Nenhum agendamento futuro") }
            }
            items(upcoming, key = { "u-" + it["id"].toString() }) { schedule ->
                ScheduleListItem(schedule, showDate = true) { openEdit(schedule) }
            }
        }
    }

    // =======================================================
    // MODAL DE AGENDAMENTO
    // =======================================================
    if (showModal) {
        var formPlate by remember { mutableStateOf(autoFillPlate.ifEmpty { selectedSchedule?.get("vehiclePlate")?.toString() ?: "" }) }
        var formCarrier by remember { mutableStateOf(autoFillCompany.ifEmpty { selectedSchedule?.get("clientName")?.toString() ?: "" }) }
        var formNotes by remember { mutableStateOf(autoFillNotes.ifEmpty { selectedSchedule?.get("notes")?.toString() ?: "" }) }

        val initialDate = (selectedSchedule?.get("scheduledDate") as? Timestamp)?.toDate() ?: preSelectedDate ?: Date()
        val sdfDate = remember { SimpleDateFormat("dd/MM/yyyy", BrLocale) }
        val sdfTime = remember { SimpleDateFormat("HH:mm", BrLocale) }
        var formDateStr by remember { mutableStateOf(sdfDate.format(initialDate)) }
        var formTimeStr by remember {
            mutableStateOf(sdfTime.format(if (selectedSchedule != null) initialDate else Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0) }.time))
        }
        var isSaving by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }

        fun findInspection() = allInspections.firstOrNull {
            (it["vehicleInfo"] as? Map<*, *>)?.get("placa")?.toString() == formPlate ||
                (it["vehicleInfo"] as? Map<*, *>)?.get("composicao1")?.toString() == formPlate
        }

        Dialog(onDismissRequest = { if (!isSaving) showModal = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Text(if (selectedSchedule != null) "Editar agendamento" else "Novo agendamento", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(20.dp))

                    NexTextField(
                        value = formPlate,
                        onValueChange = { formPlate = it.uppercase() },
                        label = "Placa",
                        trailingIcon = {
                            // Sugere data (+6 meses da última vistoria) e transportadora
                            IconButton(onClick = {
                                val match = findInspection()
                                if (match != null) {
                                    val lastDate = (match["inspectionDate"] as? Timestamp)?.toDate() ?: Date()
                                    val suggest = Calendar.getInstance().apply { time = lastDate; add(Calendar.MONTH, 6); set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0) }.time
                                    formDateStr = sdfDate.format(suggest)
                                    formTimeStr = "08:00"
                                    formCarrier = match["company"]?.toString() ?: match["clientName"]?.toString() ?: formCarrier
                                } else {
                                    Toast.makeText(context, "Nenhuma vistoria encontrada para essa placa.", Toast.LENGTH_SHORT).show()
                                }
                            }) { Icon(Icons.Default.AutoAwesome, contentDescription = "Sugerir data", tint = MaterialTheme.colorScheme.primary) }
                        },
                        supportingText = "Toque na estrela para sugerir a data"
                    )
                    Spacer(Modifier.height(8.dp))
                    CarrierField(value = formCarrier, onValueChange = { formCarrier = it }, carriers = carriersList, label = "Transportadora")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NexTextField(value = formDateStr, onValueChange = { formDateStr = it }, label = "Data", modifier = Modifier.weight(1.3f))
                        NexTextField(value = formTimeStr, onValueChange = { formTimeStr = it }, label = "Hora", modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    NexTextField(value = formNotes, onValueChange = { formNotes = it }, label = "Observações / motivo", singleLine = false, minLines = 3, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences)

                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(
                        text = "Salvar",
                        loading = isSaving,
                        onClick = {
                            if (formPlate.isBlank()) {
                                Toast.makeText(context, "Informe a placa.", Toast.LENGTH_SHORT).show()
                                return@PrimaryButton
                            }
                            isSaving = true
                            try {
                                // Não-leniente: "32/13/2026" é erro, e não "01/02/2027"
                                val parser = SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale).apply { isLenient = false }
                                val finalDate = parser.parse("${formDateStr.trim()} ${formTimeStr.trim()}") ?: throw IllegalArgumentException()
                                val match = findInspection()
                                val finalCarrier = match?.get("company")?.toString() ?: formCarrier

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
                        }
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        if (selectedSchedule != null) {
                            TextButton(
                                onClick = { confirmDelete = true },
                                enabled = !isSaving,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Excluir")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showModal = false }, enabled = !isSaving) { Text("Cancelar") }
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Excluir agendamento?") },
                text = { Text("O agendamento de ${formPlate.ifBlank { "este veículo" }} será removido.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        isSaving = true
                        repository.deleteSchedule(
                            selectedSchedule!!["id"].toString(),
                            onSuccess = { isSaving = false; showModal = false; loadData() },
                            onError = { erro -> isSaving = false; Toast.makeText(context, "Erro ao excluir: $erro", Toast.LENGTH_LONG).show() }
                        )
                    }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
private fun MonthCalendar(
    month: Calendar,
    selectedDay: Date,
    schedules: List<Map<String, Any>>,
    pendencies: List<Map<String, Any>>,
    onMonthChange: (Int) -> Unit,
    onToday: () -> Unit,
    onSelectDay: (Date) -> Unit
) {
    val dayKey = remember { SimpleDateFormat("yyyyMMdd", BrLocale) }
    // Status por dia: agendamentos (cor do originStatus) + pendências sem agenda (cor do status)
    val eventsByDay = remember(schedules, pendencies) {
        val map = mutableMapOf<String, MutableList<String>>()
        schedules.forEach { s ->
            (s["scheduledDate"] as? Timestamp)?.toDate()?.let { map.getOrPut(dayKey.format(it)) { mutableListOf() }.add(s["originStatus"]?.toString() ?: "Em Dia") }
        }
        pendencies.forEach { p ->
            (p["calculatedNextDate"] as? Date)?.let { map.getOrPut(dayKey.format(it)) { mutableListOf() }.add(p["pendencyStatus"]?.toString() ?: "Em Dia") }
        }
        map
    }
    val todayKey = dayKey.format(Date())
    val selectedKey = dayKey.format(selectedDay)

    NexCard(contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(-1) }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Mês anterior") }
            Text(
                SimpleDateFormat("MMMM 'de' yyyy", BrLocale).format(month.time).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { onMonthChange(1) }) { Icon(Icons.Default.ChevronRight, contentDescription = "Próximo mês") }
        }
        TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Ir para hoje", style = MaterialTheme.typography.labelMedium) }

        Row(Modifier.fillMaxWidth()) {
            listOf("D", "S", "T", "Q", "Q", "S", "S").forEach { d ->
                Text(d, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))

        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = month.get(Calendar.DAY_OF_WEEK) - 1
        val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

        (0 until totalCells).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val day = cell - firstDayOfWeek + 1
                    if (day < 1 || day > daysInMonth) {
                        Spacer(Modifier.weight(1f).height(48.dp))
                    } else {
                        val date = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.time
                        val key = dayKey.format(date)
                        val isToday = key == todayKey
                        val isSelected = key == selectedKey
                        val events = eventsByDay[key].orEmpty()
                        Column(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onSelectDay(date) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                Modifier.size(26.dp).then(
                                    if (isToday && !isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                events.take(3).forEach { status ->
                                    val dot = if (isSelected) MaterialTheme.colorScheme.onPrimary else toneColors(scheduleTone(status)).first
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf("Vencida", "A Vencer", "Em Dia").forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(toneColors(scheduleTone(s)).first))
                    Spacer(Modifier.width(4.dp))
                    Text(s, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ScheduleListItem(schedule: Map<String, Any>, showDate: Boolean = false, onClick: () -> Unit) {
    val plate = schedule["vehiclePlate"]?.toString() ?: "S/P"
    val carrier = schedule["clientName"]?.toString() ?: "S/ Empresa"
    val date = (schedule["scheduledDate"] as? Timestamp)?.toDate() ?: Date()
    val status = schedule["originStatus"]?.toString() ?: "Em Dia"
    val (fg, bg) = toneColors(scheduleTone(status))

    NexCard(onClick = onClick, accent = fg, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.clip(MaterialTheme.shapes.small).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(SimpleDateFormat("HH:mm", BrLocale).format(date), style = MaterialTheme.typography.titleSmall, color = fg)
                if (showDate) Text(SimpleDateFormat("dd/MM", BrLocale).format(date), style = MaterialTheme.typography.labelSmall, color = fg)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PlateTag(plate)
                Spacer(Modifier.height(4.dp))
                Text(carrier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusPill(status, scheduleTone(status))
        }
    }
}
