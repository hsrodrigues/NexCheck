package com.nexcheck.app

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nexcheck.app.ui.components.*
import com.nexcheck.app.ui.theme.NexTheme
import java.text.SimpleDateFormat
import java.util.*

val IncidentTypes = listOf("ACIDENTE", "INCIDENTE", "FALHA MECÂNICA", "FALHA ELÉTRICA", "OUTRO")

@Composable
fun IncidentFormScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val repository = remember { InspectionRepository() }

    var tipo by remember { mutableStateOf("FALHA MECÂNICA") }
    var dataStr by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", BrLocale).format(Date())) }
    var horaStr by remember { mutableStateOf(SimpleDateFormat("HH:mm", BrLocale).format(Date())) }
    var placa by remember { mutableStateOf("") }
    var motorista by remember { mutableStateOf("") }
    var transportadora by remember { mutableStateOf("") }
    var local by remember { mutableStateOf("") }
    var relato by remember { mutableStateOf("") }

    var allCarriers by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { repository.getCarriers { allCarriers = it } }

    // Classificação automática da causa a partir do relato (atualiza enquanto digita)
    val causaIA = remember(relato) { NexCheckLogic.predictCauseFromReport(relato) }

    fun salvar() {
        if (placa.isBlank() || transportadora.isBlank() || motorista.isBlank()) {
            Toast.makeText(context, "Preencha Placa, Transportadora e Motorista.", Toast.LENGTH_SHORT).show()
            return
        }
        val finalDateObj = try {
            SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale).apply { isLenient = false }.parse("${dataStr.trim()} ${horaStr.trim()}")
        } catch (e: Exception) { null }
        if (finalDateObj == null) {
            Toast.makeText(context, "Data ou hora inválida. Use DD/MM/AAAA e HH:MM.", Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true

        val payload = mapOf(
            "type" to tipo,
            "date" to Timestamp(finalDateObj),
            "plate" to placa.trim(),
            "driver" to motorista.trim(),
            "company" to transportadora.trim(),
            "location" to local,
            "relato" to relato,
            "cause" to causaIA,
            "createdAt" to FieldValue.serverTimestamp(),
            "createdBy" to (auth.currentUser?.displayName ?: "App Android")
        )

        db.collection("incidents").add(payload)
            .addOnSuccessListener {
                Toast.makeText(context, "Ocorrência salva com sucesso!", Toast.LENGTH_SHORT).show()
                onBack()
            }.addOnFailureListener { e ->
                isSaving = false
                Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    NexScaffold(
        title = "Registrar ocorrência",
        onBack = onBack,
        bottomBar = {
            BottomActionBar {
                PrimaryButton(
                    "Registrar ocorrência",
                    onClick = { salvar() },
                    loading = isSaving,
                    icon = Icons.Default.Report,
                    containerColor = NexTheme.status.danger,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("Classificação")
            NexCard {
                SelectField(value = tipo, options = IncidentTypes, onSelect = { tipo = it }, label = "Tipo de ocorrência")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NexTextField(value = dataStr, onValueChange = { dataStr = it }, label = "Data", modifier = Modifier.weight(1.3f))
                    NexTextField(value = horaStr, onValueChange = { horaStr = it }, label = "Hora", modifier = Modifier.weight(1f))
                }
            }

            SectionTitle("Envolvidos", Modifier.padding(top = 8.dp))
            NexCard {
                NexTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = "Placa *")
                Spacer(Modifier.height(12.dp))
                NexTextField(value = motorista, onValueChange = { motorista = it.uppercase() }, label = "Motorista *")
                Spacer(Modifier.height(12.dp))
                CarrierField(value = transportadora, onValueChange = { transportadora = it }, carriers = allCarriers)
            }

            SectionTitle("Relato", Modifier.padding(top = 8.dp))
            NexCard {
                NexTextField(value = local, onValueChange = { local = it.uppercase() }, label = "Localização (BR, KM, referência)", leadingIcon = Icons.Default.Place)
                Spacer(Modifier.height(12.dp))
                NexTextField(
                    value = relato,
                    onValueChange = { relato = it.uppercase() },
                    label = "Descreva detalhadamente a ocorrência",
                    singleLine = false,
                    minLines = 5
                )
                Spacer(Modifier.height(12.dp))

                val undefined = causaIA == "A ANALISAR / OUTROS" || causaIA == "A ANALISAR"
                Surface(
                    color = if (undefined) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (undefined) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Causa raiz sugerida", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                causaIA,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (undefined) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
