package com.nexcheck.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
    var userName by remember { mutableStateOf("Carregando...") }
    var userJobRaw by remember { mutableStateOf("inspector") }
    var userJobDisplay by remember { mutableStateOf("Inspetor") }
    val userPermissions = remember { mutableStateMapOf<String, Boolean>() }

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

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    var isOnline by remember { mutableStateOf(true) }
    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FintechBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Olá,", color = TextMuted, fontSize = 14.sp)
                Text(userName, color = TextDark, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            IconButton(
                onClick = onLogout,
                modifier = Modifier.clip(CircleShape).background(Color.White).border(1.dp, CardBorder, CircleShape)
            ) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.Red) }
        }

        // --- CARD DE STATUS ---
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isOnline) PrimaryBlue else Color(0xFFEA580C)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Status do Sistema", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text(userJobDisplay.uppercase(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(text = if (isOnline) "Operação Online" else "Modo Offline", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    SummaryItem(label = if (isOnline) "Sincronizado" else "Aguardando", icon = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff)
                    SummaryItem(label = "Versão $appVersion", icon = Icons.Default.Verified)
                }
            }
        }

        // ==========================================================
        // VISTORIA ELETROMECÂNICA
        // ==========================================================
        SectionTitle("Vistoria Eletromecânica")

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
            if (userPermissions["nova_vistoria_cavalo"] != false) {
                MenuButton(Modifier.weight(1f), "Nova Vistoria Cavalo", Icons.Default.LocalShipping, PrimaryBlue) { onNavigateToInspection("cavalo") }
            } else { Spacer(Modifier.weight(1f)) }

            if (userPermissions["nova_vistoria_carreta"] != false) {
                MenuButton(Modifier.weight(1f), "Nova Vistoria Carreta", Icons.Default.RvHookup, Color(0xFF10B981)) { onNavigateToInspection("implemento") }
            } else { Spacer(Modifier.weight(1f)) }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
            if (userPermissions["historico"] != false) {
                MenuButton(Modifier.weight(1f), "Vistorias Realizadas", Icons.Default.History, Color(0xFF6366F1)) { onNavigateToHistory() }
            } else { Spacer(Modifier.weight(1f)) }

            if (userPermissions["pendencias"] == true) {
                MenuButton(Modifier.weight(1f), "Pendências de Vistorias", Icons.Default.Warning, Color(0xFFF59E0B)) { onNavigateToPendencies() }
            } else { Spacer(Modifier.weight(1f)) }
        }

        // ==========================================================
        // OPERACIONAL AVANÇADO (FUMAÇA / DIÁRIO)
        // ==========================================================
        if (userPermissions["fumaca_nova"] != false || userPermissions["fumaca_pendencias"] == true || userPermissions["diario_bordo"] != false) {
            Spacer(Modifier.height(32.dp))
            SectionTitle("Operacional")

            // Fumaça
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                if (userPermissions["fumaca_nova"] != false) {
                    MenuButton(Modifier.weight(1f), "Aferição de Fumaça", Icons.Default.CloudQueue, Color(0xFF8B5CF6)) { onNavigateToSmokeForm() }
                } else { Spacer(Modifier.weight(1f)) }

                if (userPermissions["fumaca_pendencias"] == true) {
                    MenuButton(Modifier.weight(1f), "Pendências (Fumaça)", Icons.Default.ReportProblem, Color(0xFFEAB308)) { onNavigateToSmokePendencies() }
                } else { Spacer(Modifier.weight(1f)) }
            }

            // Diário de Bordo
            if (userPermissions["diario_bordo"] != false) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                    MenuButton(Modifier.weight(1f), "Diário de Bordo", Icons.AutoMirrored.Filled.FactCheck, Color(0xFF14B8A6)) { onNavigateToLogbooks() }
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // ==========================================================
        // GESTÃO E OCORRÊNCIAS
        // ==========================================================
        if (userPermissions["agenda"] == true || userPermissions["ocorrencias"] == true) {
            Spacer(Modifier.height(32.dp))
            SectionTitle("Gestão de Frota")

            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                if (userPermissions["agenda"] == true) {
                    MenuButton(Modifier.weight(1f), "Agenda Mensal", Icons.Default.DateRange, Color(0xFF10B981)) { onNavigateToSchedules() }
                } else { Spacer(Modifier.weight(1f)) }

                if (userPermissions["ocorrencias"] == true) {
                    MenuButton(Modifier.weight(1f), "Registro de Ocorrências", CarCrash, Color(0xFFDC2626)) { onNavigateToIncidents() }
                } else { Spacer(Modifier.weight(1f)) }
            }
        }

        // ==========================================================
        // SESSÃO ADMIN
        // ==========================================================
        if (userJobRaw == "admin") {
            Spacer(Modifier.height(32.dp))
            SectionTitle("Administração")
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                MenuButton(
                    Modifier.weight(1f).height(120.dp),
                    "Gestão de Usuários",
                    Icons.Default.ManageAccounts,
                    Color(0xFF334155)
                ) { onNavigateToUserManagement() }
                Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(40.dp))
        Text(
            "Powered by NexCheck • Inspeções Eletromecânicas e Gestão de Frota",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SummaryItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MenuButton(modifier: Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier.height(120.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), Arrangement.Center, Alignment.Start) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Black, lineHeight = 16.sp)
        }
    }
}

// 🔥 UTILITÁRIO: Um ícone de batida de carro para ocorrências
val CarCrash: androidx.compose.ui.graphics.vector.ImageVector
    get() = Icons.Default.Warning // Como o compose nativo não tem CarCrash por padrão, usamos o Warning como Fallback. Se quiser o de fato do Material 3 estendido, podemos instalar a lib.