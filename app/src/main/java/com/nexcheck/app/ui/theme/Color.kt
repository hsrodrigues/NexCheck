package com.nexcheck.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =========================================================================
// PALETA NEXCHECK
// Azul da marca (o mesmo do sistema web) + neutros "slate".
// =========================================================================

// Marca
val Blue50 = Color(0xFFEFF5FF)
val Blue100 = Color(0xFFDBE7FE)
val Blue300 = Color(0xFF93B4FD)
val Blue400 = Color(0xFF6B96FA)
val Blue600 = Color(0xFF2563EB)
val Blue700 = Color(0xFF1D4ED8)
val Blue900 = Color(0xFF172554)

// Neutros
val Slate50 = Color(0xFFF6F8FB)
val Slate100 = Color(0xFFEEF2F7)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF0A1020)

// Superfícies do tema escuro
val Night900 = Color(0xFF0B1220)
val Night800 = Color(0xFF111A2B)
val Night700 = Color(0xFF18233A)
val Night600 = Color(0xFF223050)

// =========================================================================
// CORES DE STATUS (semânticas) — usadas em selos, faixas e botões de checklist
// =========================================================================

@Immutable
data class StatusColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val revisit: Color,
    val revisitContainer: Color
)

val LightStatusColors = StatusColors(
    success = Color(0xFF15803D), successContainer = Color(0xFFDCFCE7),
    warning = Color(0xFFB45309), warningContainer = Color(0xFFFEF3C7),
    danger = Color(0xFFDC2626), dangerContainer = Color(0xFFFEE2E2),
    info = Blue600, infoContainer = Blue100,
    neutral = Slate500, neutralContainer = Slate100,
    revisit = Color(0xFFC2410C), revisitContainer = Color(0xFFFFEDD5)
)

val DarkStatusColors = StatusColors(
    success = Color(0xFF4ADE80), successContainer = Color(0xFF12301F),
    warning = Color(0xFFFBBF24), warningContainer = Color(0xFF3A2A0B),
    danger = Color(0xFFF87171), dangerContainer = Color(0xFF3B1418),
    info = Blue300, infoContainer = Color(0xFF14254A),
    neutral = Slate400, neutralContainer = Night700,
    revisit = Color(0xFFFB923C), revisitContainer = Color(0xFF3A1D0C)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

// Cores de destaque dos módulos do menu (ícones)
object ModuleColors {
    val Cavalo = Blue600
    val Carreta = Color(0xFF059669)
    val Historico = Color(0xFF6366F1)
    val Pendencias = Color(0xFFD97706)
    val Fumaca = Color(0xFF8B5CF6)
    val FumacaPendencias = Color(0xFFCA8A04)
    val Diario = Color(0xFF0D9488)
    val Agenda = Color(0xFF0891B2)
    val Ocorrencias = Color(0xFFDC2626)
    val Admin = Slate500
}
