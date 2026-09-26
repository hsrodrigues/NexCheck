package com.nexcheck.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexcheck.app.ui.components.SegmentedChoice
import com.nexcheck.app.ui.components.UserAvatar
import com.nexcheck.app.ui.theme.ThemeMode

/** Menu lateral com perfil, todos os módulos liberados, aparência e sair. */
@Composable
fun AppDrawer(
    profile: UserProfile,
    email: String,
    appVersion: String,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigate: (String) -> Unit,
    onHome: () -> Unit,
    onLogout: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
            // --- CABEÇALHO DO PERFIL ---
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF12275E), Color(0xFF1D4ED8), Color(0xFF3B82F6))))
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    UserAvatar(
                        photoUrl = profile.photoUrl,
                        initials = profile.initials,
                        size = 56.dp,
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        profile.name.ifBlank { "Carregando…" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (email.isNotBlank()) {
                        Text(email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape) {
                        Text(
                            profile.roleDisplay.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            DrawerItem("Início", Icons.Default.Home, MaterialTheme.colorScheme.primary, onClick = onHome)

            // --- MÓDULOS (só os que o usuário pode acessar) ---
            val visible = profile.visibleModules()
            ModuleSection.entries.forEach { section ->
                val modules = visible.filter { it.section == section }
                if (modules.isNotEmpty()) {
                    Text(
                        section.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 6.dp)
                    )
                    modules.forEach { module ->
                        DrawerItem(module.title, module.icon, module.color) { onNavigate(module.route) }
                    }
                }
            }

            // --- APARÊNCIA ---
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "APARÊNCIA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, bottom = 8.dp)
            )
            SegmentedChoice(
                options = ThemeMode.entries.map { it.name to it.label },
                selected = themeMode.name,
                onSelect = { onThemeChange(ThemeMode.valueOf(it)) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(16.dp))
            DrawerItem("Sair da conta", Icons.AutoMirrored.Filled.Logout, MaterialTheme.colorScheme.error, labelColor = MaterialTheme.colorScheme.error, onClick = onLogout)

            Spacer(Modifier.weight(1f))
            Text(
                "NexCheck · v$appVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.navigationBarsPadding().padding(start = 28.dp, top = 16.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.labelLarge, color = labelColor) },
        icon = { Icon(icon, contentDescription = null, tint = tint) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp).height(48.dp)
    )
}
