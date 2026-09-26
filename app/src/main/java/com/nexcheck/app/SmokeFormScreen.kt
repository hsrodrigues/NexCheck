package com.nexcheck.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    val sdf = SimpleDateFormat("dd/MM/yyyy", BrLocale)

    var placa by remember { mutableStateOf(prePlaca ?: "") }
    var transportadora by remember { mutableStateOf(preEmpresa ?: "") }
    var inspectionDate by remember { mutableStateOf(sdf.format(Date())) }

    // --- VARIÁVEIS PARA O DROPDOWN DA TRANSPORTADORA ---
    var allCarriers by remember { mutableStateOf<List<String>>(emptyList()) }
    var carrierDropdownExpanded by remember { mutableStateOf(false) }

    // Nível Ringelmann de 1 a 5
    var selectedLevel by remember { mutableStateOf<Int?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val ringelmannColors = listOf(
        Color(0xFFE5E7EB), // Nível 1 - 20% (Cinza muito claro)
        Color(0xFF9CA3AF), // Nível 2 - 40% (Cinza claro)
        Color(0xFF6B7280), // Nível 3 - 60% (Cinza médio) - REPROVA A PARTIR DAQUI
        Color(0xFF4B5563), // Nível 4 - 80% (Cinza escuro)
        Color(0xFF1F2937)  // Nível 5 - 100% (Quase preto)
    )

    // Lógica da IA de Fumaça (Igual JS)
    val resultStatus = if ((selectedLevel ?: 0) >= 3) "Reprovado" else "Aprovado"

    // Busca as transportadoras ao abrir a tela
    LaunchedEffect(Unit) {
        repository.getCarriers { carriers ->
            allCarriers = carriers
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text("Aferição de Fumaça", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Dados do Veículo", fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))

                    FintechTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = "Placa *")
                    Spacer(Modifier.height(12.dp))

                    // --- DROPDOWN COM PESQUISA IGUAL AO DA INSPEÇÃO ---
                    ExposedDropdownMenuBox(
                        expanded = carrierDropdownExpanded,
                        onExpandedChange = { carrierDropdownExpanded = !carrierDropdownExpanded }
                    ) {
                        FintechTextField(
                            value = transportadora,
                            onValueChange = { transportadora = it.uppercase(); carrierDropdownExpanded = true },
                            label = "Transportadora *",
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                        )
                        val filteredCarriers = allCarriers.filter { it.contains(transportadora, ignoreCase = true) }
                        if (filteredCarriers.isNotEmpty() && transportadora.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = carrierDropdownExpanded,
                                onDismissRequest = { carrierDropdownExpanded = false }
                            ) {
                                filteredCarriers.take(5).forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            transportadora = selectionOption
                                            carrierDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    // ---------------------------------------------------

                    Spacer(Modifier.height(12.dp))

                    FintechTextField(value = inspectionDate, onValueChange = { inspectionDate = it }, label = "Data (DD/MM/AAAA) *")
                }
            }

            Text("ESCALA RINGELMANN", fontWeight = FontWeight.Black, fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Selecione a opacidade da fumaça observada:", fontSize = 13.sp, color = TextDark, modifier = Modifier.padding(bottom = 16.dp))

                    // Régua de Cores
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 1..5) {
                            val isSelected = selectedLevel == i
                            val bgColor = ringelmannColors[i - 1]
                            val textColor = if (i >= 3) Color.White else Color.Black

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .padding(horizontal = 4.dp)
                                    .background(bgColor, RoundedCornerShape(8.dp))
                                    .border(
                                        if (isSelected) BorderStroke(3.dp, PrimaryBlue) else BorderStroke(1.dp, Color.Transparent),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedLevel = i }
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Nível $i", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColor)
                                    Text("${i * 20}%", fontSize = 10.sp, color = textColor.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }

                    if (selectedLevel != null) {
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = CardBorder)
                        Spacer(Modifier.height(16.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Resultado da Aferição:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            val resultColor = if (resultStatus == "Aprovado") Color(0xFF16A34A) else Color(0xFFDC2626)
                            val resultBg = if (resultStatus == "Aprovado") Color(0xFFDCFCE7) else Color(0xFFFEE2E2)

                            Surface(color = resultBg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, resultColor.copy(alpha = 0.5f))) {
                                Text(resultStatus.uppercase(), color = resultColor, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                        if (resultStatus == "Reprovado") {
                            Text("Opacidade acima do permitido por lei (Nível 3+). O veículo requer manutenção.", fontSize = 11.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (placa.isBlank() || transportadora.isBlank() || selectedLevel == null) {
                        Toast.makeText(context, "Preencha Placa, Transportadora e Nível.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val inspDateJS = try {
                        SimpleDateFormat("dd/MM/yyyy", BrLocale).apply { isLenient = false }.parse(inspectionDate.trim())
                    } catch (e: Exception) { null }
                    if (inspDateJS == null) {
                        Toast.makeText(context, "Data inválida. Use DD/MM/AAAA.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSaving = true

                    // Lógica do JS: +6 meses para a próxima
                    val nextDateJS = Calendar.getInstance().apply {
                        time = inspDateJS
                        add(Calendar.MONTH, 6)
                    }.time

                    val data = mapOf(
                        "vehiclePlate" to placa.trim(),
                        "companyName" to transportadora.trim(),
                        "inspectionDate" to Timestamp(inspDateJS),
                        "nextInspectionDate" to Timestamp(nextDateJS),
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
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("SALVAR AFERIÇÃO", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
            }
        }
    }
}