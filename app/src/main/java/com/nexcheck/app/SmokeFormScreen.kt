package com.nexcheck.app

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nexcheck.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

// Tons da escala Ringelmann (20% a 100% de opacidade)
private val RingelmannShades = listOf(
    Color(0xFFE5E7EB), Color(0xFF9CA3AF), Color(0xFF6B7280), Color(0xFF4B5563), Color(0xFF1F2937)
)

@Composable
fun SmokeFormScreen(
    prePlaca: String? = null,
    preEmpresa: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val repository = remember { InspectionRepository() }

    var placa by remember { mutableStateOf(prePlaca ?: "") }
    var transportadora by remember { mutableStateOf(preEmpresa ?: "") }
    var inspectionDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", BrLocale).format(Date())) }
    var allCarriers by remember { mutableStateOf<List<String>>(emptyList()) }

    // Nível Ringelmann de 1 a 5
    var selectedLevel by remember { mutableStateOf<Int?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Nível 3, 4 ou 5 reprova; 1 ou 2 aprova (mesma regra do sistema web)
    val resultStatus = NexCheckLogic.avaliarFumaca(selectedLevel ?: 0)

    LaunchedEffect(Unit) {
        repository.getCarriers { carriers -> allCarriers = carriers }
    }

    fun salvar() {
        if (placa.isBlank() || transportadora.isBlank() || selectedLevel == null) {
            Toast.makeText(context, "Preencha Placa, Transportadora e Nível.", Toast.LENGTH_SHORT).show()
            return
        }
        val inspDate = try {
            SimpleDateFormat("dd/MM/yyyy", BrLocale).apply { isLenient = false }.parse(inspectionDate.trim())
        } catch (e: Exception) { null }
        if (inspDate == null) {
            Toast.makeText(context, "Data inválida. Use DD/MM/AAAA.", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        // +6 meses para a próxima aferição
        val nextDate = Calendar.getInstance().apply { time = inspDate; add(Calendar.MONTH, 6) }.time

        val data = mapOf(
            "vehiclePlate" to placa.trim(),
            "companyName" to transportadora.trim(),
            "inspectionDate" to Timestamp(inspDate),
            "nextInspectionDate" to Timestamp(nextDate),
            "result" to resultStatus,
            "ringelmannValue" to selectedLevel,
            "inspectorId" to (auth.currentUser?.uid ?: "anonimo"),
            "inspectorName" to (auth.currentUser?.displayName ?: "Inspetor"),
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("smokeInspections").add(data)
            .addOnSuccessListener {
                isSaving = false
                Toast.makeText(context, "Inspeção de fumaça salva com sucesso!", Toast.LENGTH_LONG).show()
                onBack()
            }
            .addOnFailureListener { e ->
                isSaving = false
                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    NexScaffold(
        title = "Aferição de fumaça",
        subtitle = "Escala Ringelmann",
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                PrimaryButton("Salvar aferição", onClick = { salvar() }, loading = isSaving, icon = Icons.Default.Check)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("Veículo")
            NexCard {
                NexTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = "Placa *")
                Spacer(Modifier.height(12.dp))
                CarrierField(value = transportadora, onValueChange = { transportadora = it }, carriers = allCarriers)
                Spacer(Modifier.height(12.dp))
                NexTextField(value = inspectionDate, onValueChange = { inspectionDate = it }, label = "Data (DD/MM/AAAA) *")
            }

            SectionTitle("Opacidade observada", Modifier.padding(top = 8.dp))
            NexCard {
                Text(
                    "Compare a fumaça com a escala e toque no nível correspondente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (level in 1..5) {
                        val isSelected = selectedLevel == level
                        val shade = RingelmannShades[level - 1]
                        val textColor = if (level >= 3) Color.White else Color(0xFF111827)
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    MaterialTheme.shapes.medium
                                )
                                .clickable { selectedLevel = level }
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(64.dp).background(shade),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$level", style = MaterialTheme.typography.headlineSmall, color = textColor)
                            }
                            Text(
                                "${level * 20}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = selectedLevel != null) {
                    val aprovado = resultStatus == "Aprovado"
                    val (fg, bg) = toneColors(toneFor(resultStatus))
                    Surface(color = bg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (aprovado) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = fg)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(if (aprovado) "Aprovado" else "Reprovado", style = MaterialTheme.typography.titleMedium, color = fg)
                                Text(
                                    if (aprovado) "Opacidade dentro do limite permitido."
                                    else "Opacidade acima do permitido (nível 3+). O veículo requer manutenção.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
