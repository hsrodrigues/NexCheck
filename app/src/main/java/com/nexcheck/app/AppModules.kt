package com.nexcheck.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nexcheck.app.ui.theme.ModuleColors

// =========================================================================
// MÓDULOS DO APP
// Fonte única para a grade da tela inicial e para o menu lateral.
// =========================================================================

enum class ModuleSection(val title: String) {
    INSPECAO("Vistoria eletromecânica"),
    OPERACIONAL("Operacional"),
    GESTAO("Gestão")
}

data class AppModule(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val section: ModuleSection,
    val route: String,
    val permission: String? // null = só administradores
)

val AllModules = listOf(
    AppModule("Vistoria Cavalo", "Nova inspeção do cavalo mecânico", Icons.Default.LocalShipping, ModuleColors.Cavalo, ModuleSection.INSPECAO, "inspection/cavalo", "nova_vistoria_cavalo"),
    AppModule("Vistoria Carreta", "Nova inspeção de implemento", Icons.Default.RvHookup, ModuleColors.Carreta, ModuleSection.INSPECAO, "inspection/implemento", "nova_vistoria_carreta"),
    AppModule("Realizadas", "Histórico e relatórios", Icons.Default.History, ModuleColors.Historico, ModuleSection.INSPECAO, "history", "historico"),
    AppModule("Pendências", "Vencidas e revistorias", Icons.Default.PendingActions, ModuleColors.Pendencias, ModuleSection.INSPECAO, "pendencies", "pendencias"),
    AppModule("Aferição de Fumaça", "Escala Ringelmann", Icons.Default.Air, ModuleColors.Fumaca, ModuleSection.OPERACIONAL, "smoke_form?placa=&empresa=", "fumaca_nova"),
    AppModule("Pendências Fumaça", "Vencimentos de aferição", Icons.Default.ReportProblem, ModuleColors.FumacaPendencias, ModuleSection.OPERACIONAL, "smoke_pendencies", "fumaca_pendencias"),
    AppModule("Diário de Bordo", "Checklist pré-carregamento", Icons.AutoMirrored.Filled.FactCheck, ModuleColors.Diario, ModuleSection.OPERACIONAL, "logbooks", "diario_bordo"),
    AppModule("Agenda", "Calendário de vistorias", Icons.Default.CalendarMonth, ModuleColors.Agenda, ModuleSection.GESTAO, "schedules", "agenda"),
    AppModule("Ocorrências", "Acidentes e falhas", Icons.Default.CarCrash, ModuleColors.Ocorrencias, ModuleSection.GESTAO, "incidents", "ocorrencias"),
    AppModule("Usuários", "Permissões de acesso", Icons.Default.ManageAccounts, ModuleColors.Admin, ModuleSection.GESTAO, "userManagement", null)
)

// =========================================================================
// SESSÃO DO USUÁRIO (nome, cargo e permissões)
// =========================================================================

data class UserProfile(
    val name: String = "",
    val role: String = "inspector",
    val loaded: Boolean = false,
    val photoUrl: String? = null,
    val permissions: Map<String, Boolean> = NexCheckLogic.defaultPermissions("inspector")
) {
    val roleDisplay: String
        get() = when (role) {
            "admin" -> "Administrador"
            "escritorio" -> "Escritório"
            "torre_de_controle" -> "Torre de Controle"
            else -> "Inspetor"
        }

    val initials: String
        get() {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> ""
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> "${parts.first().first()}${parts.last().first()}".uppercase()
            }
        }

    // Enquanto não carrega, vale o padrão de inspetor (não "pisca" módulos de gestão)
    fun canAccess(module: AppModule): Boolean =
        if (module.permission == null) role == "admin" else permissions[module.permission] == true

    fun visibleModules(): List<AppModule> = AllModules.filter { canAccess(it) }
}

/** Carrega o perfil do usuário logado no Firestore (coleção "users"). */
@Composable
fun rememberUserProfile(uid: String?): UserProfile {
    var profile by remember(uid) { mutableStateOf(UserProfile()) }
    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        val auth = FirebaseAuth.getInstance()
        val fallbackName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Usuário"
        // Foto da conta Google (quando o login foi pelo Google); pede uma versão maior que a padrão (96px)
        val photo = auth.currentUser?.photoUrl?.toString()?.replace("=s96-c", "=s256-c")
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = (doc.getString("role") ?: "inspector").lowercase().trim()
                profile = UserProfile(
                    name = doc.getString("name") ?: doc.getString("nome") ?: fallbackName,
                    role = role,
                    loaded = true,
                    photoUrl = photo,
                    permissions = NexCheckLogic.effectivePermissions(role, doc.get("permissions"))
                )
            }
            .addOnFailureListener {
                profile = UserProfile(name = fallbackName, loaded = true, photoUrl = photo)
            }
    }
    return profile
}
