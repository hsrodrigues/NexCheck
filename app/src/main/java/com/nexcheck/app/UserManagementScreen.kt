package com.nexcheck.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.nexcheck.app.ui.components.*

private val RoleLabels = mapOf(
    "admin" to "Administrador",
    "escritorio" to "Escritório",
    "torre_de_controle" to "Torre de Controle",
    "inspector" to "Inspetor"
)

private fun roleLabel(raw: String) = RoleLabels[raw] ?: "Inspetor"

// Permissões agrupadas por área (chave no banco -> rótulo)
private val PermissionGroups = listOf(
    "Vistorias" to listOf(
        "nova_vistoria_cavalo" to "Nova vistoria cavalo",
        "nova_vistoria_carreta" to "Nova vistoria carreta",
        "historico" to "Histórico de vistorias",
        "pendencias" to "Pendências de frota"
    ),
    "Operacional" to listOf(
        "fumaca_nova" to "Nova aferição de fumaça",
        "fumaca_pendencias" to "Pendências de fumaça",
        "diario_bordo" to "Diário de bordo"
    ),
    "Gestão" to listOf(
        "agenda" to "Agenda de vistorias",
        "ocorrencias" to "Ocorrências e acidentes"
    )
)

@Composable
fun UserManagementScreen(onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var users by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf("ALL") }

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

    fun roleOf(user: Map<String, Any>) = (user["role"] ?: "inspector").toString().lowercase().trim().let { if (it in RoleLabels) it else "inspector" }

    val filteredUsers = remember(users, searchText, roleFilter) {
        users.filter {
            val name = (it["name"] ?: it["nome"] ?: "").toString()
            val email = (it["email"] ?: "").toString()
            val role = roleOf(it)
            (name.contains(searchText, true) || email.contains(searchText, true) || roleLabel(role).contains(searchText, true)) &&
                (roleFilter == "ALL" || role == roleFilter)
        }.sortedBy { (it["name"] ?: it["nome"] ?: "").toString().lowercase() }
    }

    NexScaffold(title = "Usuários e permissões", subtitle = "${users.size} usuários cadastrados", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SearchField(searchText, { searchText = it }, "Buscar por nome, e-mail ou cargo") }
            item {
                FilterChipsRow(
                    options = listOf(FilterOption("ALL", "Todos")) + RoleLabels.map { (key, label) ->
                        FilterOption(key, label, users.count { roleOf(it) == key })
                    },
                    selected = roleFilter,
                    onSelect = { roleFilter = it }
                )
            }
            when {
                isLoading -> item { LoadingState() }
                filteredUsers.isEmpty() -> item { EmptyState(Icons.Default.PersonSearch, "Nenhum usuário encontrado") }
                else -> items(filteredUsers, key = { it["id"].toString() }) { user ->
                    UserPermissionCard(user, roleOf(user))
                }
            }
        }
    }
}

@Composable
private fun UserPermissionCard(user: Map<String, Any>, roleRaw: String) {
    val db = FirebaseFirestore.getInstance()
    val userId = user["id"].toString()
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "seta")

    val name = (user["name"] ?: user["nome"] ?: "Usuário sem nome").toString()
    val email = (user["email"] ?: "Sem e-mail cadastrado").toString()
    val currentPermissions = NexCheckLogic.effectivePermissions(roleRaw, user["permissions"])
    val enabledCount = currentPermissions.count { it.value }

    NexCard(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val initials = if (parts.size > 1) "${parts.first().first()}${parts.last().first()}" else name.take(2)
                Text(initials.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(roleLabel(roleRaw), if (roleRaw == "admin") StatusTone.Info else StatusTone.Neutral)
                    Spacer(Modifier.width(8.dp))
                    Text("$enabledCount de ${currentPermissions.size} acessos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Recolher" else "Expandir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(arrowRotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 12.dp)) {
                PermissionGroups.forEach { (groupTitle, perms) ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                    Text(groupTitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    perms.forEach { (key, label) ->
                        PermissionToggle(label, currentPermissions[key] == true) { newValue ->
                            updatePermission(db, userId, key, newValue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionToggle(label: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
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
