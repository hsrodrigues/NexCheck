package com.nexcheck.app.ui.components

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.nexcheck.app.ui.theme.NexTheme

// =========================================================================
// ESTRUTURA DE TELA
// =========================================================================

/**
 * Força a cor dos ícones da barra de status enquanto a tela estiver visível
 * (ex.: login com fundo escuro) e restaura o valor anterior ao sair.
 */
@Composable
fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(darkIcons) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = darkIcons
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content
    )
}

/** Diálogo em tela cheia para detalhes/relatórios (histórico, diário, ocorrência). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenDetail(
    title: String,
    onClose: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fechar") } },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

/** Barra fixa no rodapé com a ação principal (salvar). */
@Composable
fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            content = content
        )
    }
}

// =========================================================================
// CARTÕES E TEXTOS
// =========================================================================

@Composable
fun NexCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable () -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (accent != null) Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
            Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) { inner() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) { inner() }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun InfoItem(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, emphasize: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            value.ifBlank { "—" },
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, modifier: Modifier = Modifier) = EmptyState(icon, title, null, modifier)

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.onSurfaceVariant, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

// =========================================================================
// STATUS
// =========================================================================

enum class StatusTone { Success, Warning, Danger, Info, Neutral, Revisit }

/** Converte os status gravados no banco para um tom visual. */
fun toneFor(status: String): StatusTone = when (status.trim().lowercase()) {
    "liberado", "aprovado", "ok", "bom", "em dia", "emdia", "realizado", "concluído" -> StatusTone.Success
    "não liberado", "reprovado", "ruim", "nok", "vencida", "vencido" -> StatusTone.Danger
    "a vencer", "avencer" -> StatusTone.Warning
    "revistoria" -> StatusTone.Revisit
    "agendado" -> StatusTone.Info
    else -> StatusTone.Neutral
}

@Composable
fun toneColors(tone: StatusTone): Pair<Color, Color> {
    val s = NexTheme.status
    return when (tone) {
        StatusTone.Success -> s.success to s.successContainer
        StatusTone.Warning -> s.warning to s.warningContainer
        StatusTone.Danger -> s.danger to s.dangerContainer
        StatusTone.Info -> s.info to s.infoContainer
        StatusTone.Neutral -> s.neutral to s.neutralContainer
        StatusTone.Revisit -> s.revisit to s.revisitContainer
    }
}

/** Selo de status com o tom deduzido do próprio texto ("Liberado", "Vencida"...). */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier) = StatusPill(text, toneFor(text), modifier)

@Composable
fun StatusPill(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val (fg, bg) = toneColors(tone)
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// =========================================================================
// PLACA (estilo Mercosul)
// =========================================================================

@Composable
fun PlateTag(plate: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val shape = RoundedCornerShape(if (large) 8.dp else 6.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, Color(0xFF1E293B), shape)
            .width(IntrinsicSize.Max)
    ) {
        Box(
            Modifier.fillMaxWidth().background(Color(0xFF1D4ED8)).padding(vertical = if (large) 2.dp else 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("BRASIL", color = Color.White, fontSize = if (large) 8.sp else 6.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 9.sp)
        }
        Text(
            plate.ifBlank { "S/ PLACA" }.uppercase(),
            color = Color(0xFF0F172A),
            fontSize = if (large) 22.sp else 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = if (large) 12.dp else 8.dp, vertical = if (large) 2.dp else 1.dp),
            maxLines = 1
        )
    }
}

// =========================================================================
// CAMPOS DE FORMULÁRIO
// =========================================================================

@Composable
fun nexFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun NexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Characters,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = visualTransformation,
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailingIcon,
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        shape = MaterialTheme.shapes.small,
        colors = nexFieldColors()
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, contentDescription = "Limpar busca") }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

/** Campo de transportadora com sugestões enquanto digita (usado em vários formulários). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarrierField(
    value: String,
    onValueChange: (String) -> Unit,
    carriers: List<String>,
    modifier: Modifier = Modifier,
    label: String = "Transportadora *",
    readOnly: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(value, carriers) {
        if (value.isBlank()) emptyList() else carriers.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }.take(6)
    }
    ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty() && !readOnly, onExpandedChange = { if (!readOnly) expanded = it }, modifier = modifier) {
        NexTextField(
            value = value,
            onValueChange = { onValueChange(it.uppercase()); expanded = true },
            label = label,
            readOnly = readOnly,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty() && !readOnly,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            suggestions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                    onClick = { onValueChange(option); expanded = false }
                )
            }
        }
    }
}

/** Campo de seleção fechada (lista de opções). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectField(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!readOnly) expanded = it }, modifier = modifier) {
        NexTextField(
            value = value,
            onValueChange = {},
            label = label,
            readOnly = true,
            trailingIcon = { if (!readOnly) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = MaterialTheme.colorScheme.surface) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

/** Escolha única entre poucas opções (substitui os RadioButtons). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedChoice(
    options: List<Pair<String, String>>, // valor -> rótulo
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    toneOf: ((String) -> StatusTone)? = null
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            val (toneFg, toneBg) = toneOf?.let { toneColors(it(value)) } ?: (MaterialTheme.colorScheme.onPrimaryContainer to MaterialTheme.colorScheme.primaryContainer)
            SegmentedButton(
                selected = selected == value,
                onClick = { if (enabled) onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = toneBg,
                    activeContentColor = toneFg,
                    activeBorderColor = MaterialTheme.colorScheme.outline,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline
                ),
                label = { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            )
        }
    }
}

// =========================================================================
// CHECKLIST
// =========================================================================

data class ChecklistOption(val value: String, val label: String, val tone: StatusTone)

val InspectionOptions = listOf(
    ChecklistOption("bom", "Bom", StatusTone.Success),
    ChecklistOption("ruim", "Ruim", StatusTone.Danger),
    ChecklistOption("na", "N/A", StatusTone.Neutral)
)

val LogbookOptions = listOf(
    ChecklistOption("OK", "OK", StatusTone.Success),
    ChecklistOption("NOK", "NOK", StatusTone.Danger),
    ChecklistOption("NA", "N/A", StatusTone.Neutral)
)

@Composable
fun ChecklistItemCard(
    itemName: String,
    selected: String,
    options: List<ChecklistOption>,
    onSelect: (String) -> Unit,
    readOnly: Boolean = false
) {
    val selectedTone = options.firstOrNull { it.value == selected }?.tone
    val accent = selectedTone?.let { toneColors(it).first }
    NexCard(accent = accent, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(itemName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option.value == selected
                val fg = toneColors(option.tone).first
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (isSelected) fg else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (isSelected) fg else Color.Transparent, MaterialTheme.shapes.small)
                        .clickable(enabled = !readOnly) { onSelect(option.value) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        option.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        // surface = branco no tema claro / azul-noite no escuro: contrasta com o tom sólido
                        color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =========================================================================
// ASSINATURA
// =========================================================================

@Composable
fun SignaturePad(title: String, pathState: MutableState<Path>, modifier: Modifier = Modifier) {
    val inkColor = MaterialTheme.colorScheme.onSurface
    NexCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { pathState.value = Path() }) { Text("Limpar") }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (pathState.value.isEmpty) {
                Text(
                    "Assine aqui",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // Linha de base da assinatura
            Box(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 28.dp)
                    .fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline)
            )
            Canvas(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> pathState.value.moveTo(offset.x, offset.y) },
                        onDrag = { change, _ ->
                            pathState.value.lineTo(change.position.x, change.position.y)
                            pathState.value = Path().apply { addPath(pathState.value) }
                        }
                    )
                }
            ) {
                drawPath(pathState.value, color = inkColor, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// =========================================================================
// FILTROS
// =========================================================================

data class FilterOption(val value: String, val label: String, val count: Int? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipsRow(options: List<FilterOption>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
                label = {
                    Text(if (option.count != null) "${option.label} · ${option.count}" else option.label, style = MaterialTheme.typography.labelLarge)
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = option.value == selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }
}

// =========================================================================
// BOTÕES
// =========================================================================

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        if (loading) {
            CircularProgressIndicator(color = contentColor, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 15.sp)
        }
    }
}
