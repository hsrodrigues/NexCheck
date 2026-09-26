package com.nexcheck.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var users by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }

    // Ajuste de ícones do sistema para modo claro
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    // Escuta a coleção "users" em tempo real (e para de escutar ao sair da tela)
    DisposableEffect(Unit) {
        val registration = db.collection("users").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                users = snapshot.documents.map { it.data.orEmpty() + ("id" to it.id) }
            }
            isLoading = false
        }
        onDispose { registration.remove() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text("Gestão de Permissões", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            // Busca aprimorada: Nome, Email ou Cargo
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Buscar por nome, email ou cargo...") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            if (isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryBlue)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val filteredUsers = users.filter {
                        val name = (it["name"] ?: it["nome"] ?: "").toString()
                        val email = (it["email"] ?: "").toString()
                        val role = (it["role"] ?: "").toString()

                        name.contains(searchText, true) ||
                                email.contains(searchText, true) ||
                                role.contains(searchText, true)
                    }

                    items(filteredUsers, key = { it["id"].toString() }) { user ->
                        UserPermissionCard(user)
                    }
                }
            }
        }
    }
}

@Composable
fun UserPermissionCard(user: Map<String, Any>) {
    val db = FirebaseFirestore.getInstance()
    val userId = user["id"].toString()
    var expanded by remember { mutableStateOf(false) }

    // Mapeamento do Role técnico para o Nome de exibição
    val roleRaw = (user["role"] ?: "inspector").toString().lowercase()
    val roleDisplay = when (roleRaw) {
        "admin" -> "Administrador"
        "escritorio" -> "Escritório"
        "torre_de_controle" -> "Torre de Controle"
        else -> "Inspetor"
    }

    val userEmail = (user["email"] ?: "Sem email cadastrado").toString()
    val currentPermissions = NexCheckLogic.effectivePermissions(roleRaw, user["permissions"])

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = (user["name"] ?: user["nome"] ?: "Usuário sem Nome").toString(),
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = TextDark
                    )
                    Text(
                        text = userEmail,
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    // Tag do Cargo (Job)
                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = roleDisplay.uppercase(),
                            fontSize = 10.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = CardBorder.copy(alpha = 0.5f))

                Text(
                    "PERMISSÕES DE ACESSO:",
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(8.dp))

                // Lista de Permissões Básicas
                PermissionToggle("Nova Vistoria Cavalo", currentPermissions["nova_vistoria_cavalo"] == true) { newValue ->
                    updatePermission(db, userId, "nova_vistoria_cavalo", newValue)
                }
                PermissionToggle("Nova Vistoria Carreta", currentPermissions["nova_vistoria_carreta"] == true) { newValue ->
                    updatePermission(db, userId, "nova_vistoria_carreta", newValue)
                }
                PermissionToggle("Histórico de Vistorias", currentPermissions["historico"] == true) { newValue ->
                    updatePermission(db, userId, "historico", newValue)
                }
                PermissionToggle("Pendências de Frota", currentPermissions["pendencias"] == true) { newValue ->
                    updatePermission(db, userId, "pendencias", newValue)
                }
                PermissionToggle("Agenda de Vistorias", currentPermissions["agenda"] == true) { newValue ->
                    updatePermission(db, userId, "agenda", newValue)
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = CardBorder.copy(alpha = 0.2f))

                // 🔥 NOVAS PERMISSÕES (MÓDULOS NOVOS) 🔥
                PermissionToggle("Nova Aferição de Fumaça", currentPermissions["fumaca_nova"] == true) { newValue ->
                    updatePermission(db, userId, "fumaca_nova", newValue)
                }
                PermissionToggle("Pendências de Fumaça", currentPermissions["fumaca_pendencias"] == true) { newValue ->
                    updatePermission(db, userId, "fumaca_pendencias", newValue)
                }
                PermissionToggle("Diário de Bordo", currentPermissions["diario_bordo"] == true) { newValue ->
                    updatePermission(db, userId, "diario_bordo", newValue)
                }
                PermissionToggle("Ocorrências e Acidentes", currentPermissions["ocorrencias"] == true) { newValue ->
                    updatePermission(db, userId, "ocorrencias", newValue)
                }
            }
        }
    }
}

@Composable
fun PermissionToggle(label: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark)
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedTrackColor = Color.LightGray.copy(alpha = 0.4f)
            )
        )
    }
}

fun updatePermission(
    db: FirebaseFirestore,
    userId: String,
    key: String,
    value: Boolean
) {
    // Atualiza só a chave alterada ("permissions.<chave>"), sem apagar as demais
    db.collection("users").document(userId).update("permissions.$key", value)
}