package com.nexcheck.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.nexcheck.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun MainMenuScreen(
    profile: UserProfile,
    appVersion: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { InspectionRepository() }
    var isOnline by remember { mutableStateOf(true) }

    var recentInspections by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var todaySchedules by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loadingRecent by remember { mutableStateOf(true) }
    var loadingSchedules by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) { isOnline = false }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        onDispose { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    // Dados do painel: consultas leves (últimas 20 vistorias + agenda de hoje)
    LaunchedEffect(Unit) {
        repository.getAllInspections(limit = 20) { list ->
            recentInspections = list
            loadingRecent = false
        }
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        FirebaseFirestore.getInstance().collection("schedules")
            .whereGreaterThanOrEqualTo("scheduledDate", Timestamp(start.time))
            .whereLessThan("scheduledDate", Timestamp(end.time))
            .get()
            .addOnSuccessListener { snap ->
                todaySchedules = snap.documents
                    .map { it.data.orEmpty() + ("id" to it.id) }
                    .filter { val st = it["status"]?.toString(); st == "Agendado" || st == "Não Realizado" }
                    .sortedBy { (it["scheduledDate"] as? Timestamp)?.seconds }
                loadingSchedules = false
            }
            .addOnFailureListener { loadingSchedules = false }
    }

    val todayInspections = remember(recentInspections) {
        val today = Date()
        recentInspections.filter { it["inspectionDate"].asDate()?.let { d -> sameDay(d, today) } == true }
    }
    val rejectedToday = todayInspections.count { it["status"] == "Não Liberado" || it["status"] == "Reprovado" }

    val visible = profile.visibleModules()
    val quickActions = visible.filter { it.section != ModuleSection.GESTAO && it.route.let { r -> r.startsWith("inspection/") || r.startsWith("smoke_form") || r == "logbooks" } }
    val canSeeHistory = profile.permissions["historico"] == true
    val canSeeAgenda = profile.permissions["agenda"] == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // --- CABEÇALHO ---
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderIconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = "Abrir menu") }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    profile.name.ifBlank { " " },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HeaderIconButton(onClick = onToggleTheme) {
                Icon(
                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkTheme) "Usar tema claro" else "Usar tema escuro"
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onOpenDrawer),
                contentAlignment = Alignment.Center
            ) {
                Text(profile.initials, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- RESUMO DO DIA ---
        StatusHeroCard(
            isOnline = isOnline,
            role = profile.roleDisplay,
            inspectionsToday = if (loadingRecent) null else todayInspections.size,
            rejectedToday = if (loadingRecent) null else rejectedToday,
            schedulesToday = if (loadingSchedules) null else todaySchedules.size,
            onInspectionsClick = if (canSeeHistory) ({ onNavigate("history") }) else null,
            onSchedulesClick = if (canSeeAgenda) ({ onNavigate("schedules") }) else null
        )

        // --- AÇÕES RÁPIDAS (compactas) ---
        if (quickActions.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionTitle("Ações rápidas")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickActions.forEach { module ->
                    AssistChip(
                        onClick = { onNavigate(module.route) },
                        label = { Text(module.title, style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Icon(module.icon, contentDescription = null, tint = module.color, modifier = Modifier.size(18.dp)) },
                        shape = CircleShape,
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        }

        // --- AGENDA DE HOJE ---
        if (canSeeAgenda) {
            Spacer(Modifier.height(20.dp))
            SectionTitle("Agenda de hoje", trailing = { SeeAllButton { onNavigate("schedules") } })
            when {
                loadingSchedules -> CompactLoading()
                todaySchedules.isEmpty() -> EmptyLine(Icons.Default.EventAvailable, "Nenhuma vistoria agendada para hoje")
                else -> NexCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    todaySchedules.take(5).forEachIndexed { index, s ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        val time = (s["scheduledDate"] as? Timestamp)?.toDate()?.let { SimpleDateFormat("HH:mm", BrLocale).format(it) } ?: "--:--"
                        ListRow(
                            leading = { Text(time, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) },
                            plate = s["vehiclePlate"]?.toString() ?: "",
                            subtitle = s["clientName"]?.toString() ?: "",
                            trailing = { StatusPill(s["originStatus"]?.toString() ?: "Em Dia") },
                            onClick = { onNavigate("schedules") }
                        )
                    }
                }
            }
        }

        // --- ÚLTIMAS VISTORIAS ---
        if (canSeeHistory) {
            Spacer(Modifier.height(20.dp))
            SectionTitle("Últimas vistorias", trailing = { SeeAllButton { onNavigate("history") } })
            when {
                loadingRecent -> CompactLoading()
                recentInspections.isEmpty() -> EmptyLine(Icons.Default.History, "Nenhuma vistoria registrada ainda")
                else -> NexCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    recentInspections.take(6).forEachIndexed { index, insp ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        val info = insp["vehicleInfo"] as? Map<*, *>
                        val plate = info?.get("placa")?.toString() ?: info?.get("composicao1")?.toString() ?: ""
                        val isCavalo = insp["vehicleType"]?.toString()?.contains("Cavalo") == true
                        val date = insp["inspectionDate"].asDate()?.let { SimpleDateFormat("dd/MM · HH:mm", BrLocale).format(it) } ?: ""
                        ListRow(
                            leading = {
                                Icon(
                                    if (isCavalo) Icons.Default.LocalShipping else Icons.Default.RvHookup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            plate = plate,
                            subtitle = listOf(insp["company"]?.toString().orEmpty(), date).filter { it.isNotBlank() }.joinToString(" · "),
                            trailing = { StatusPill(insp["status"]?.toString() ?: "") },
                            onClick = { onNavigate("inspection/view?docId=${insp["id"]}") }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "NexCheck · v$appVersion",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HeaderIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) { content() }
}

@Composable
private fun StatusHeroCard(
    isOnline: Boolean,
    role: String,
    inspectionsToday: Int?,
    rejectedToday: Int?,
    schedulesToday: Int?,
    onInspectionsClick: (() -> Unit)?,
    onSchedulesClick: (() -> Unit)?
) {
    val gradient = if (isOnline) {
        Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFF3B82F6)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFB45309), Color(0xFFD97706), Color(0xFFF59E0B)))
    }
    val today = remember { SimpleDateFormat("EEEE, d 'de' MMMM", BrLocale).format(Date()).replaceFirstChar { it.uppercase() } }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.08f), radius = 80.dp.toPx(), center = Offset(size.width - 30.dp.toPx(), 30.dp.toPx()))
            }
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(today, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape) {
                    Text(role.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isOnline) "Online · dados sincronizados" else "Offline · registros serão enviados depois",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroMetric("Vistorias hoje", inspectionsToday, Modifier.weight(1f), onInspectionsClick)
                HeroMetric("Reprovadas", rejectedToday, Modifier.weight(1f), onInspectionsClick)
                HeroMetric("Agendadas hoje", schedulesToday, Modifier.weight(1f), onSchedulesClick)
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: Int?, modifier: Modifier, onClick: (() -> Unit)?) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp)
    ) {
        Text(value?.toString() ?: "–", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
    }
}

@Composable
private fun SeeAllButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text("Ver tudo", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ListRow(
    leading: @Composable () -> Unit,
    plate: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterStart) { leading() }
        Column(Modifier.weight(1f)) {
            PlateTag(plate)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun EmptyLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    NexCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactLoading() {
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
    }
}

private fun sameDay(a: Date, b: Date): Boolean {
    val ca = Calendar.getInstance().apply { time = a }
    val cb = Calendar.getInstance().apply { time = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia,"
    in 12..17 -> "Boa tarde,"
    else -> "Boa noite,"
}
