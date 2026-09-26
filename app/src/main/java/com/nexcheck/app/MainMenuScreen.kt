package com.nexcheck.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nexcheck.app.ui.components.IconBadge
import com.nexcheck.app.ui.components.NexCard
import com.nexcheck.app.ui.components.SectionTitle
import com.nexcheck.app.ui.theme.ModuleColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

private data class ModuleItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun MainMenuScreen(
    onNavigateToInspection: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPendencies: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    onNavigateToUserManagement: () -> Unit,
    onNavigateToSmokePendencies: () -> Unit,
    onNavigateToSmokeForm: () -> Unit,
    onNavigateToLogbooks: () -> Unit,
    onNavigateToIncidents: () -> Unit,
    onLogout: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid
    val context = LocalContext.current

    // --- ESTADOS PARA NOME E CARGO ---
    var userName by remember { mutableStateOf("") }
    var userJobRaw by remember { mutableStateOf("inspector") }
    var userJobDisplay by remember { mutableStateOf("Inspetor") }
    val userPermissions = remember { mutableStateMapOf<String, Boolean>() }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // --- BUSCA NA COLEÇÃO USERS PELO CAMPO ROLE E PERMISSÕES ---
    LaunchedEffect(uid) {
        if (uid != null) {
            val fallbackName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Usuário"
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                userName = doc.getString("name") ?: doc.getString("nome") ?: fallbackName

                val role = doc.getString("role") ?: "inspector"
                userJobRaw = role.lowercase().trim()

                userJobDisplay = when (userJobRaw) {
                    "admin" -> "Administrador"
                    "escritorio" -> "Escritório"
                    "torre_de_controle" -> "Torre de Controle"
                    else -> "Inspetor"
                }

                userPermissions.clear()
                userPermissions.putAll(NexCheckLogic.effectivePermissions(userJobRaw, doc.get("permissions")))
            }.addOnFailureListener {
                userName = fallbackName
                userPermissions.putAll(NexCheckLogic.defaultPermissions(userJobRaw))
            }
        }
    }

    // Enquanto as permissões não chegam, mostra só o que todo inspetor pode ver
    // (evita "piscar" módulos de gestão para quem não tem acesso).
    val perms: Map<String, Boolean> = userPermissions.ifEmpty { NexCheckLogic.defaultPermissions("inspector") }
    fun can(key: String) = perms[key] == true

    var isOnline by remember { mutableStateOf(true) }
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
    }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) { isOnline = false }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        onDispose { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    val inspectionModules = buildList {
        if (can("nova_vistoria_cavalo")) add(ModuleItem("Vistoria Cavalo", "Nova inspeção do cavalo mecânico", Icons.Default.LocalShipping, ModuleColors.Cavalo) { onNavigateToInspection("cavalo") })
        if (can("nova_vistoria_carreta")) add(ModuleItem("Vistoria Carreta", "Nova inspeção de implemento", Icons.Default.RvHookup, ModuleColors.Carreta) { onNavigateToInspection("implemento") })
        if (can("historico")) add(ModuleItem("Realizadas", "Histórico e relatórios", Icons.Default.History, ModuleColors.Historico, onNavigateToHistory))
        if (can("pendencias")) add(ModuleItem("Pendências", "Vencidas e revistorias", Icons.Default.PendingActions, ModuleColors.Pendencias, onNavigateToPendencies))
    }
    val operationalModules = buildList {
        if (can("fumaca_nova")) add(ModuleItem("Aferição de Fumaça", "Escala Ringelmann", Icons.Default.Air, ModuleColors.Fumaca, onNavigateToSmokeForm))
        if (can("fumaca_pendencias")) add(ModuleItem("Pendências Fumaça", "Vencimentos de aferição", Icons.Default.ReportProblem, ModuleColors.FumacaPendencias, onNavigateToSmokePendencies))
        if (can("diario_bordo")) add(ModuleItem("Diário de Bordo", "Checklist pré-carregamento", Icons.AutoMirrored.Filled.FactCheck, ModuleColors.Diario, onNavigateToLogbooks))
    }
    val managementModules = buildList {
        if (can("agenda")) add(ModuleItem("Agenda", "Calendário de vistorias", Icons.Default.CalendarMonth, ModuleColors.Agenda, onNavigateToSchedules))
        if (can("ocorrencias")) add(ModuleItem("Ocorrências", "Acidentes e falhas", Icons.Default.CarCrash, ModuleColors.Ocorrencias, onNavigateToIncidents))
        if (userJobRaw == "admin") add(ModuleItem("Usuários", "Permissões de acesso", Icons.Default.ManageAccounts, ModuleColors.Admin, onNavigateToUserManagement))
    }

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
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initialsOf(userName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    userName.ifBlank { " " },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair", tint = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- CARTÃO DE STATUS ---
        StatusHeroCard(isOnline = isOnline, role = userJobDisplay, version = appVersion)

        // --- MÓDULOS ---
        if (inspectionModules.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionTitle("Vistoria eletromecânica")
            ModuleGrid(inspectionModules)
        }
        if (operationalModules.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionTitle("Operacional")
            ModuleGrid(operationalModules)
        }
        if (managementModules.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionTitle("Gestão")
            ModuleGrid(managementModules)
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "NexCheck · v$appVersion",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text("Sair da conta?") },
            text = { Text("Você precisará entrar novamente para usar o app.") },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("Sair", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StatusHeroCard(isOnline: Boolean, role: String, version: String) {
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
            // círculo decorativo desenhado no fundo (não influencia a altura do cartão)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.08f), radius = 80.dp.toPx(), center = Offset(size.width - 30.dp.toPx(), 30.dp.toPx()))
            }
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(today, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape) {
                    Text(
                        role.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isOnline) "Operação online" else "Modo offline",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (isOnline) "Dados sincronizados com o servidor · v$version"
                else "Os registros serão enviados quando a conexão voltar",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun ModuleGrid(items: List<ModuleItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item -> ModuleTile(item, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModuleTile(item: ModuleItem, modifier: Modifier) {
    NexCard(modifier = modifier, onClick = item.onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            IconBadge(item.icon, item.color)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            item.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia,"
    in 12..17 -> "Boa tarde,"
    else -> "Boa noite,"
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}
