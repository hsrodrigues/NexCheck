package com.nexcheck.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
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
import com.nexcheck.app.ui.components.IconBadge
import com.nexcheck.app.ui.components.NexCard
import com.nexcheck.app.ui.components.SectionTitle
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
    var isOnline by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) { isOnline = false }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        onDispose { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    val visible = profile.visibleModules()

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
            // Avatar também abre o menu lateral
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onOpenDrawer),
                contentAlignment = Alignment.Center
            ) {
                Text(profile.initials, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(Modifier.height(16.dp))
        StatusHeroCard(isOnline = isOnline, role = profile.roleDisplay, version = appVersion)

        // --- MÓDULOS ---
        ModuleSection.entries.forEach { section ->
            val modules = visible.filter { it.section == section }
            if (modules.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionTitle(section.title)
                ModuleGrid(modules, onNavigate)
            }
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
                Text(today, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape) {
                    Text(role.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (isOnline) "Operação online" else "Modo offline", style = MaterialTheme.typography.headlineSmall, color = Color.White)
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
private fun ModuleGrid(items: List<AppModule>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item -> ModuleTile(item, Modifier.weight(1f)) { onNavigate(item.route) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModuleTile(item: AppModule, modifier: Modifier, onClick: () -> Unit) {
    NexCard(modifier = modifier, onClick = onClick) {
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
