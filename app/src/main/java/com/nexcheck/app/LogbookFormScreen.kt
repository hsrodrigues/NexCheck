package com.nexcheck.app

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookFormScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val repository = remember { InspectionRepository() }

    var placa by remember { mutableStateOf("") }
    var transportadora by remember { mutableStateOf("") }
    var motorista by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("PADRÃO") }
    var carroceria by remember { mutableStateOf("SIDER") }

    var allCarriers by remember { mutableStateOf<List<String>>(emptyList()) }
    var carrierDropdownExpanded by remember { mutableStateOf(false) }
    var checklistConfig by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    val responses = remember { mutableStateMapOf<String, String>() }
    val signature = remember { mutableStateOf(Path()) }

    LaunchedEffect(Unit) {
        repository.getCarriers { allCarriers = it }
        val fallback = mapOf(
            "sections" to listOf(mapOf("title" to "Itens de Segurança", "items" to listOf("Pneus", "Luzes", "EPIs")))
        )
        db.collection("checklists").document("logbook").get()
            .addOnSuccessListener { doc ->
                checklistConfig = if (doc.exists()) doc.data else fallback
                isLoading = false
            }
            .addOnFailureListener {
                checklistConfig = fallback
                isLoading = false
                Toast.makeText(context, "Sem conexão: usando checklist padrão.", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text("Novo Diário", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = PrimaryBlue) }
        } else {
            Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        FintechTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = "Placa *")
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
                        Spacer(Modifier.height(12.dp))
                        FintechTextField(value = motorista, onValueChange = { motorista = it.uppercase() }, label = "Motorista *")

                        Spacer(Modifier.height(16.dp))
                        Text("Tipo de Veículo", fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = type == "PADRÃO", onClick = { type = "PADRÃO" })
                            Text("Padrão", fontSize = 14.sp); Spacer(Modifier.width(16.dp))
                            RadioButton(selected = type == "BITREM", onClick = { type = "BITREM" })
                            Text("Bitrem", fontSize = 14.sp)
                        }
                        Text("Carroceria", fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = carroceria == "SIDER", onClick = { carroceria = "SIDER" })
                            Text("Sider", fontSize = 14.sp); Spacer(Modifier.width(16.dp))
                            RadioButton(selected = carroceria == "GRADE BAIXA", onClick = { carroceria = "GRADE BAIXA" })
                            Text("Grade Baixa", fontSize = 14.sp)
                        }
                    }
                }

                val sections = checklistConfig?.get("sections").asMapList()
                sections.forEach { section ->
                    SectionTitle(section["title"]?.toString() ?: "")
                    section["items"].asStringList().forEach { item ->
                        LogbookItemRow(item, responses[item] ?: "", onSelect = { responses[item] = it })
                    }
                    Spacer(Modifier.height(16.dp))
                }

                SectionTitle("Assinatura")
                SignatureBox("Assinatura do Motorista", signature)
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (placa.isBlank() || transportadora.isBlank() || motorista.isBlank() || signature.value.isEmpty) {
                            Toast.makeText(context, "Preencha todos os campos obrigatórios.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSaving = true
                        // Fora da transação: ela pode ser reexecutada pelo Firestore
                        val signatureBase64 = captureSignatureToBase64(signature.value)

                        db.runTransaction { transaction ->
                            val counterRef = db.collection("settings").document("logbook_counters")
                            val nextId = (transaction.get(counterRef).getLong("current") ?: 0L) + 1
                            // set+merge cria o contador se ele ainda não existir
                            transaction.set(counterRef, mapOf("current" to nextId), SetOptions.merge())

                            // MOLDANDO A ESTRUTURA IGUAL O SEU JS
                            var statusGlobal = "Aprovado"
                            val failedItems = mutableListOf<String>()
                            val fullChecklistData = mutableListOf<Map<String, Any>>()

                            sections.forEach { section ->
                                val sTitle = section["title"].toString()
                                val sectionItems = mutableListOf<Map<String, String>>()

                                section["items"].asStringList().forEach { itmName ->
                                    val st = responses[itmName] ?: "NA"
                                    if(st == "NOK") {
                                        statusGlobal = "Reprovado"
                                        failedItems.add("${sTitle}: ${itmName}".uppercase())
                                    }
                                    sectionItems.add(mapOf("name" to itmName, "status" to st))
                                }
                                fullChecklistData.add(mapOf("sectionTitle" to sTitle, "items" to sectionItems))
                            }

                            val logData = mapOf(
                                "logNumber" to nextId,
                                "plate" to placa.trim(),
                                "carrier" to transportadora.trim(),
                                "driverName" to motorista.trim(),
                                "compositionType" to type,
                                "bodyType" to carroceria, // ADICIONADO AQUI
                                "status" to statusGlobal,
                                "failedItems" to failedItems,
                                "checklistStruct" to fullChecklistData, // IGUAL O JS
                                "signature" to signatureBase64,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "createdBy" to (auth.currentUser?.displayName ?: "App")
                            )
                            transaction.set(db.collection("daily_logbooks").document(), logData)
                        }.addOnSuccessListener {
                            onBack()
                            Toast.makeText(context, "Diário salvo com sucesso!", Toast.LENGTH_LONG).show()
                        }.addOnFailureListener { e ->
                            isSaving = false
                            Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp), enabled = !isSaving,
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else Text("SALVAR DIÁRIO", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun LogbookItemRow(label: String, selected: String, onSelect: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row {
                StatusCircle("OK", selected == "OK", Color(0xFF10B981)) { onSelect("OK") }
                Spacer(Modifier.width(8.dp))
                StatusCircle("NOK", selected == "NOK", Color(0xFFEF4444)) { onSelect("NOK") }
                Spacer(Modifier.width(8.dp))
                StatusCircle("NA", selected == "NA", Color(0xFF9CA3AF)) { onSelect("NA") }
            }
        }
    }
}

@Composable
private fun StatusCircle(label: String, isSelected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) color else Color(0xFFF1F5F9), shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable { onClick() }.size(width = 45.dp, height = 35.dp),
        border = BorderStroke(1.dp, if (isSelected) color else Color(0xFFE2E8F0))
    ) { Box(contentAlignment = Alignment.Center) { Text(label, color = if (isSelected) Color.White else Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Black) } }
}