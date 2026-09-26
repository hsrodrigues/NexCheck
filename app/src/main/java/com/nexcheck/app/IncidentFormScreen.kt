package com.nexcheck.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentFormScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val repository = remember { InspectionRepository() }
    val sdfDate = SimpleDateFormat("dd/MM/yyyy", BrLocale)
    val sdfTime = SimpleDateFormat("HH:mm", BrLocale)

    var tipo by remember { mutableStateOf("FALHA MECÂNICA") }
    var dataStr by remember { mutableStateOf(sdfDate.format(Date())) }
    var horaStr by remember { mutableStateOf(sdfTime.format(Date())) }
    var placa by remember { mutableStateOf("") }
    var motorista by remember { mutableStateOf("") }
    var transportadora by remember { mutableStateOf("") }
    var local by remember { mutableStateOf("") }
    var relato by remember { mutableStateOf("") }

    // Este campo é atualizado pela sua IA local quando o relato muda!
    var causaIA by remember { mutableStateOf("A ANALISAR") }

    var allCarriers by remember { mutableStateOf<List<String>>(emptyList()) }
    var carrierDropdownExpanded by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.getCarriers { allCarriers = it }
    }

    // 🔥 MAGICA DA IA: Sempre que o relato mudar, ele atualiza a causa no fundo
    LaunchedEffect(relato) {
        causaIA = NexCheckLogic.predictCauseFromReport(relato)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text("Registrar Ocorrência", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Classificação", fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))

                    ExposedDropdownMenuBox(expanded = typeDropdownExpanded, onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }) {
                        FintechTextField(value = tipo, onValueChange = {}, label = "Tipo de Ocorrência", modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable), readOnly = true)
                        ExposedDropdownMenu(expanded = typeDropdownExpanded, onDismissRequest = { typeDropdownExpanded = false }) {
                            listOf("ACIDENTE", "INCIDENTE", "FALHA MECÂNICA", "FALHA ELÉTRICA", "OUTRO").forEach { t ->
                                DropdownMenuItem(text = { Text(t, fontWeight = FontWeight.Bold) }, onClick = { tipo = t; typeDropdownExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FintechTextField(value = dataStr, onValueChange = { dataStr = it }, label = "Data (DD/MM/AAAA)", modifier = Modifier.weight(1f))
                        FintechTextField(value = horaStr, onValueChange = { horaStr = it }, label = "Hora (HH:MM)", modifier = Modifier.weight(1f))
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Envolvidos", fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                    FintechTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = "Placa *")
                    Spacer(Modifier.height(12.dp))
                    FintechTextField(value = motorista, onValueChange = { motorista = it.uppercase() }, label = "Motorista *")
                    Spacer(Modifier.height(12.dp))
                    ExposedDropdownMenuBox(expanded = carrierDropdownExpanded, onExpandedChange = { carrierDropdownExpanded = !carrierDropdownExpanded }) {
                        FintechTextField(value = transportadora, onValueChange = { transportadora = it.uppercase(); carrierDropdownExpanded = true }, label = "Transportadora *", modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable))
                        val filtered = allCarriers.filter { it.contains(transportadora, true) }
                        if (filtered.isNotEmpty() && transportadora.isNotEmpty()) {
                            ExposedDropdownMenu(expanded = carrierDropdownExpanded, onDismissRequest = { carrierDropdownExpanded = false }) {
                                filtered.take(5).forEach { carrier ->
                                    DropdownMenuItem(text = { Text(carrier, fontWeight = FontWeight.Bold) }, onClick = { transportadora = carrier; carrierDropdownExpanded = false })
                                }
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    FintechTextField(value = local, onValueChange = { local = it.uppercase() }, label = "Localização (BR, KM, Referência)")
                    Spacer(Modifier.height(12.dp))
                    FintechTextField(value = relato, onValueChange = { relato = it.uppercase() }, label = "Descreva detalhadamente a ocorrência", singleLine = false, modifier = Modifier.height(120.dp))

                    Spacer(Modifier.height(16.dp))
                    Surface(color = Color(0xFFF3F4F6), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Causa Raiz Automática (IA)", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(causaIA, fontWeight = FontWeight.Black, color = if(causaIA == "A ANALISAR / OUTROS") Color(0xFFDC2626) else Color(0xFF2563EB), fontSize = 14.sp)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (placa.isBlank() || transportadora.isBlank() || motorista.isBlank()) {
                        Toast.makeText(context, "Preencha Placa, Transportadora e Motorista.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val finalDateObj = try {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale).apply { isLenient = false }
                            .parse("${dataStr.trim()} ${horaStr.trim()}")
                    } catch (e: Exception) { null }
                    if (finalDateObj == null) {
                        Toast.makeText(context, "Data ou hora inválida. Use DD/MM/AAAA e HH:MM.", Toast.LENGTH_SHORT).show()
                        return@Button
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
                        "cause" to causaIA, // Salva o resultado da IA
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
                },
                modifier = Modifier.fillMaxWidth().height(60.dp), enabled = !isSaving,
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else Text("REGISTRAR OCORRÊNCIA", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}