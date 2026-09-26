package com.nexcheck.app

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

val FintechBg = Color(0xFFF8FAFC)
val CardBorder = Color(0xFFE2E8F0)
val PrimaryBlue = Color(0xFF2563EB)
val InputBg = Color(0xFFF1F5F9)
val TextDark = Color(0xFF0F172A)
val TextMuted = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    tipo: String,
    prePlaca: String? = null,
    prePrefixo: String? = null,
    preModelo: String? = null,
    preEmpresa: String? = null,
    preMotorista: String? = null,
    preCliente: String? = null,
    preAcao: String? = null,
    docId: String? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { InspectionRepository() }
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale)

    val now = remember { Calendar.getInstance() }
    val nextCal = remember { Calendar.getInstance().apply { add(Calendar.MONTH, 6) } }

    val isReadOnly = docId != null

    var checklist by remember { mutableStateOf<ChecklistData?>(null) }
    var allCarriers by remember { mutableStateOf<List<String>>(emptyList()) }
    var allClients by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    var inspectionType by remember { mutableStateOf(preAcao ?: "Vistoria") }
    var clientName by remember { mutableStateOf(preCliente ?: "") }
    var transportadora by remember { mutableStateOf(preEmpresa ?: "") }
    var placa by remember { mutableStateOf(prePlaca ?: "") }
    var marca by remember { mutableStateOf(prePrefixo ?: "") }
    var modelo by remember { mutableStateOf(preModelo ?: "") }
    var motorista by remember { mutableStateOf(preMotorista ?: "") }

    var clientId by remember { mutableStateOf<String?>(null) }
    var clientDropdownExpanded by remember { mutableStateOf(false) }
    var carrierDropdownExpanded by remember { mutableStateOf(false) }

    // Na visualização (vinda do histórico) o tipo real só é conhecido após carregar o documento
    var isCavalo by remember { mutableStateOf(tipo == "cavalo") }

    var km by remember { mutableStateOf("") }
    var composicao1 by remember { mutableStateOf(if(!isCavalo) prePlaca ?: "" else "") }
    var composicao2 by remember { mutableStateOf("") }
    var composicao3 by remember { mutableStateOf("") }
    var linkedCavaloPlate by remember { mutableStateOf("") }

    var inspectionDate by remember { mutableStateOf("") }
    var nextInspectionDate by remember { mutableStateOf("") }

    var observacoes by remember { mutableStateOf("") }
    var servicos by remember { mutableStateOf("") }
    var statusFinal by remember { mutableStateOf("Liberado") }

    val responses = remember { mutableStateMapOf<String, String>() }
    val signatureDriver = remember { mutableStateOf(Path()) }
    val signatureInspector = remember { mutableStateOf(Path()) }

    LaunchedEffect(docId, tipo) {
        repository.getCarriers { carriers -> allCarriers = carriers }
        repository.getClients { clients -> allClients = clients }

        if (docId != null) {
            // MODO VISUALIZAÇÃO: Carrega do Banco
            db.collection("inspections").document(docId).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val data = doc.data
                    val vehicleType = data?.get("vehicleType")?.toString() ?: ""
                    val savedInfo = data?.get("vehicleInfo") as? Map<*, *>
                    isCavalo = vehicleType.contains("Cavalo", ignoreCase = true) || savedInfo?.get("placa") != null

                    // Mostra exatamente as seções gravadas, na ordem do checklist atual quando possível
                    val savedSections = (data?.get("items") as? Map<*, *>)?.mapNotNull { (sectionId, sectionData) ->
                        val sectionObj = sectionData as? Map<*, *> ?: return@mapNotNull null
                        ChecklistSection(
                            id = sectionId.toString(),
                            title = sectionObj["title"]?.toString() ?: "",
                            items = (sectionObj["items"] as? Map<*, *>)?.keys?.map { it.toString() } ?: emptyList()
                        )
                    } ?: emptyList()
                    checklist = ChecklistData(title = vehicleType, sections = savedSections.sortedBy { it.id })
                    repository.getChecklistSchema(if (isCavalo) "cavalo" else "implemento") { schema ->
                        val order = schema?.sections?.map { it.id } ?: return@getChecklistSchema
                        checklist = ChecklistData(
                            title = vehicleType,
                            sections = savedSections.sortedBy { s -> order.indexOf(s.id).let { if (it < 0) Int.MAX_VALUE else it } }
                        )
                    }

                    inspectionType = data?.get("inspectionType")?.toString() ?: "Vistoria"
                    clientName = data?.get("clientName")?.toString() ?: ""
                    transportadora = data?.get("company")?.toString() ?: ""
                    motorista = data?.get("driverName")?.toString() ?: ""
                    observacoes = data?.get("observations")?.toString() ?: ""
                    servicos = data?.get("servicesNeeded")?.toString() ?: ""
                    statusFinal = data?.get("status")?.toString() ?: "Liberado"

                    val vInfo = data?.get("vehicleInfo") as? Map<*, *>
                    placa = vInfo?.get("placa")?.toString() ?: ""
                    marca = vInfo?.get("marca")?.toString() ?: ""
                    modelo = vInfo?.get("modelo")?.toString() ?: ""
                    km = vInfo?.get("km")?.toString() ?: ""
                    composicao1 = vInfo?.get("composicao1")?.toString() ?: ""
                    composicao2 = vInfo?.get("composicao2")?.toString() ?: ""
                    composicao3 = vInfo?.get("composicao3")?.toString() ?: ""
                    linkedCavaloPlate = vInfo?.get("linkedCavaloPlate")?.toString() ?: ""

                    (data?.get("inspectionDate") as? Timestamp)?.let { inspectionDate = sdf.format(it.toDate()) }
                    (data?.get("nextInspectionDate") as? Timestamp)?.let { nextInspectionDate = SimpleDateFormat("dd/MM/yyyy", BrLocale).format(it.toDate()) }

                    // 🔥 TRADUTOR DO FORMATO JS PARA O ANDROID (Modo Leitura)
                    val itemsMap = data?.get("items") as? Map<*, *>
                    itemsMap?.forEach { (sectionId, sectionData) ->
                        val sectionObj = sectionData as? Map<*, *>
                        val sectionTitle = sectionObj?.get("title")?.toString() ?: ""
                        val innerItems = sectionObj?.get("items") as? Map<*, *>

                        innerItems?.forEach { (itemName, itemStatus) ->
                            val uniqueKey = "${sectionId}|${sectionTitle}|$itemName"
                            responses[uniqueKey] = itemStatus.toString()
                        }
                    }

                    // Fallback: se tiver algo no formato antigo, ele puxa
                    (data?.get("responses") as? Map<*, *>)?.forEach { (k, v) -> responses[k.toString()] = v.toString() }
                } else {
                    Toast.makeText(context, "Vistoria não encontrada.", Toast.LENGTH_SHORT).show()
                }
                isLoading = false
            }.addOnFailureListener { e ->
                isLoading = false
                Toast.makeText(context, "Erro ao carregar vistoria: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            repository.getChecklistSchema(tipo) { result -> checklist = result }

            // MODO NOVA VISTORIA
            inspectionDate = sdf.format(now.time)
            nextInspectionDate = SimpleDateFormat("dd/MM/yyyy", BrLocale).format(nextCal.time)

            // Pega os parâmetros originais para não depender de estado não atualizado
            val placaBusca = prePlaca ?: placa
            val acaoBusca = preAcao ?: inspectionType

            // 🔥 INTELIGÊNCIA DE REVISTORIA: Puxa o último laudo para auto-preencher!
            if (acaoBusca == "Revistoria" && placaBusca.isNotEmpty()) {
                val campoBusca = if (isCavalo) "vehicleInfo.placa" else "vehicleInfo.composicao1"

                db.collection("inspections")
                    .whereEqualTo(campoBusca, placaBusca)
                    .orderBy("inspectionDate", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { query ->
                        if (!query.isEmpty) {
                            val lastDoc = query.documents[0]
                            val data = lastDoc.data // <- O Kotlin sabe que isso pode ser null

                            // 🔥 CORREÇÃO: Usando data?.get(...) no lugar de data[...]
                            observacoes = data?.get("observations")?.toString() ?: ""
                            servicos = data?.get("servicesNeeded")?.toString() ?: ""

                            val itemsMap = data?.get("items") as? Map<*, *>
                            itemsMap?.forEach { (sectionId, sectionData) ->
                                val sectionObj = sectionData as? Map<*, *>
                                val sectionTitle = sectionObj?.get("title")?.toString() ?: ""
                                val innerItems = sectionObj?.get("items") as? Map<*, *>

                                innerItems?.forEach { (itemName, itemStatus) ->
                                    val uniqueKey = "${sectionId}|${sectionTitle}|$itemName"
                                    responses[uniqueKey] = itemStatus.toString()
                                }
                            }
                        }
                        isLoading = false
                    }
                    .addOnFailureListener {
                        isLoading = false
                    }
            } else {
                isLoading = false
            }
        }
    }

    LaunchedEffect(responses.toMap()) {
        if (!isReadOnly) {
            val hasRuim = responses.values.contains("ruim")
            statusFinal = if (hasRuim) "Não Liberado" else "Liberado"
            val badItems = responses.filter { it.value == "ruim" }.keys.map { it.split("|")[2] }
            servicos = if (badItems.isNotEmpty()) badItems.joinToString(", ").uppercase() else ""
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text(if (isReadOnly) "Visualizar Vistoria" else (checklist?.title ?: "Inspeção"), fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = TextDark),
                modifier = Modifier.shadow(elevation = 2.dp)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PrimaryBlue) }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {

                item {
                    SectionTitle("Informações Gerais")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Tipo de Vistoria", fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = inspectionType == "Vistoria", onClick = { if(!isReadOnly) inspectionType = "Vistoria" })
                                Text("Vistoria Inicial", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(16.dp))
                                RadioButton(selected = inspectionType == "Revistoria", onClick = { if(!isReadOnly) inspectionType = "Revistoria" })
                                Text("Revistoria", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEA580C))
                            }
                            Spacer(Modifier.height(16.dp))

                            ExposedDropdownMenuBox(
                                expanded = clientDropdownExpanded,
                                onExpandedChange = { if(!isReadOnly) clientDropdownExpanded = !clientDropdownExpanded }
                            ) {
                                FintechTextField(
                                    value = clientName, onValueChange = {}, label = "Cliente",
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(), readOnly = true
                                )
                                ExposedDropdownMenu(expanded = clientDropdownExpanded, onDismissRequest = { clientDropdownExpanded = false }) {
                                    allClients.forEach { client ->
                                        DropdownMenuItem(
                                            text = { Text(client["name"] ?: "", fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                clientName = client["name"] ?: ""
                                                clientId = client["id"]
                                                clientDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            ExposedDropdownMenuBox(
                                expanded = carrierDropdownExpanded,
                                onExpandedChange = { if(!isReadOnly) carrierDropdownExpanded = !carrierDropdownExpanded }
                            ) {
                                FintechTextField(
                                    value = transportadora,
                                    onValueChange = { transportadora = it.uppercase(); carrierDropdownExpanded = true },
                                    label = "Transportadora *",
                                    readOnly = isReadOnly,
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth()
                                )
                                val filteredCarriers = allCarriers.filter { it.contains(transportadora, ignoreCase = true) }
                                if (filteredCarriers.isNotEmpty() && transportadora.isNotEmpty() && !isReadOnly) {
                                    ExposedDropdownMenu(expanded = carrierDropdownExpanded, onDismissRequest = { carrierDropdownExpanded = false }) {
                                        filteredCarriers.take(5).forEach { selectionOption ->
                                            DropdownMenuItem(
                                                text = { Text(selectionOption, fontWeight = FontWeight.Bold) },
                                                onClick = { transportadora = selectionOption; carrierDropdownExpanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            if (isCavalo) {
                                FintechTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = "Placa *", readOnly = isReadOnly)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FintechTextField(value = marca, onValueChange = { marca = it.uppercase() }, label = "Prefixo / Marca", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                                    FintechTextField(value = modelo, onValueChange = { modelo = it.uppercase() }, label = "Modelo", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FintechTextField(value = km, onValueChange = { km = it }, label = "Hodômetro", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), readOnly = isReadOnly)
                                    FintechTextField(value = motorista, onValueChange = { motorista = it.uppercase() }, label = "Motorista *", modifier = Modifier.weight(2f), readOnly = isReadOnly)
                                }
                            } else {
                                FintechTextField(value = linkedCavaloPlate, onValueChange = { linkedCavaloPlate = it.uppercase() }, label = "Vincular Cavalo (Placa) *", readOnly = isReadOnly)
                                Spacer(Modifier.height(12.dp))
                                FintechTextField(value = composicao1, onValueChange = { composicao1 = it.uppercase() }, label = "1ª Composição (Placa) *", readOnly = isReadOnly)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FintechTextField(value = composicao2, onValueChange = { composicao2 = it.uppercase() }, label = "2ª Composição", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                                    FintechTextField(value = composicao3, onValueChange = { composicao3 = it.uppercase() }, label = "3ª Composição", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FintechTextField(value = inspectionDate, onValueChange = { inspectionDate = it }, label = "Data/Hora", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                                FintechTextField(value = nextInspectionDate, onValueChange = { nextInspectionDate = it }, label = "Vencimento", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                            }
                        }
                    }
                }

                checklist?.sections?.forEach { section ->
                    item { SectionTitle(section.title) }
                    items(section.items) { itemDesc ->
                        val uniqueKey = "${section.id}|${section.title}|$itemDesc"
                        ItemRow(
                            itemName = itemDesc,
                            selectedStatus = responses[uniqueKey] ?: "",
                            isReadOnly = isReadOnly,
                            onStatusSelected = { if(!isReadOnly) responses[uniqueKey] = it }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                item {
                    SectionTitle("Conclusão")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            FintechTextField(value = observacoes, onValueChange = { observacoes = it.uppercase() }, label = "Observações Gerais", singleLine = false, modifier = Modifier.height(100.dp), readOnly = isReadOnly)
                            Spacer(Modifier.height(12.dp))
                            FintechTextField(value = servicos, onValueChange = { servicos = it.uppercase() }, label = "Serviços Necessários", singleLine = false, modifier = Modifier.height(100.dp), readOnly = isReadOnly)

                            Spacer(Modifier.height(16.dp))
                            Text("Resultado Final", fontWeight = FontWeight.Bold, color = TextMuted, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = statusFinal == "Liberado", onClick = { if(!isReadOnly) statusFinal = "Liberado" })
                                Text("Liberado", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF16A34A))
                                Spacer(Modifier.width(16.dp))
                                RadioButton(selected = statusFinal == "Não Liberado", onClick = { if(!isReadOnly) statusFinal = "Não Liberado" })
                                Text("Não Liberado", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFDC2626))
                            }
                        }
                    }
                }

                if (!isReadOnly) {
                    item {
                        SectionTitle("Assinaturas")
                        SignatureBox(if(isCavalo) "Motorista" else "Responsável", signatureDriver)
                        Spacer(Modifier.height(16.dp))
                        SignatureBox("Vistoriador", signatureInspector)
                        Spacer(Modifier.height(32.dp))
                    }

                    item {
                        Button(
                            onClick = {
                                val placaFinal = if (isCavalo) placa else composicao1
                                if (placaFinal.isBlank() || transportadora.isBlank()) {
                                    Toast.makeText(context, "Atenção: Placa e Transportadora são obrigatórios!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isSaving = true
                                val inspetor = auth.currentUser?.displayName ?: "Inspetor Android"
                                val base64Motorista = captureSignatureToBase64(signatureDriver.value)
                                val base64Inspetor = captureSignatureToBase64(signatureInspector.value)

                                val finalInspDate = try { sdf.parse(inspectionDate) ?: now.time } catch (e: Exception) { now.time }
                                val finalNextDate = try { SimpleDateFormat("dd/MM/yyyy", BrLocale).parse(nextInspectionDate) ?: nextCal.time } catch (e: Exception) { nextCal.time }

                                repository.salvarVistoria(
                                    inspectionType = inspectionType, clientId = clientId, clientName = clientName.ifEmpty { "Cliente Avulso" },
                                    placa = placaFinal, transportadora = transportadora, motorista = motorista,
                                    marca = marca, modelo = modelo, km = km, composicao2 = composicao2, composicao3 = composicao3,
                                    linkedCavaloPlate = linkedCavaloPlate, inspectionDate = finalInspDate, nextInspectionDate = finalNextDate,
                                    respostas = responses, observacoes = observacoes, servicos = servicos, statusFinal = statusFinal,
                                    assinaturaMotoristaBase64 = base64Motorista, assinaturaInspetorBase64 = base64Inspetor,
                                    isCavalo = isCavalo,
                                    tipoVeiculo = checklist?.title ?: if (isCavalo) "Inspeção Eletromecânica - Cavalo" else "Inspeção Eletromecânica - Implemento", inspectorName = inspetor,
                                    onSuccess = { numero ->
                                        isSaving = false
                                        Toast.makeText(context, "Vistoria salva com sucesso!", Toast.LENGTH_LONG).show()
                                        onNavigateBack()
                                    },
                                    onError = { erro ->
                                        isSaving = false
                                        Toast.makeText(context, "Erro no Servidor: $erro", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp), enabled = !isSaving,
                            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            else Text("SALVAR VISTORIA", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

// =========================================================================
// COMPONENTES COMPARTILHADOS (PÚBLICOS PARA TODO O APP ENXERGAR)
// =========================================================================

@Composable
fun SectionTitle(title: String) {
    Text(text = title.uppercase(), fontWeight = FontWeight.Black, fontSize = 13.sp, color = TextMuted, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FintechTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, keyboardType: KeyboardType = KeyboardType.Text, readOnly: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label, fontWeight = FontWeight.SemiBold) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, readOnly = readOnly, keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = InputBg, unfocusedContainerColor = InputBg,
            focusedBorderColor = PrimaryBlue, unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = PrimaryBlue, unfocusedLabelColor = TextMuted,
            focusedTextColor = TextDark, unfocusedTextColor = TextDark
        )
    )
}

@Composable
fun SignatureBox(title: String, pathState: MutableState<Path>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, color = TextDark)
                Text("Limpar", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { pathState.value = Path() })
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(InputBg)) {
                Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> pathState.value.moveTo(offset.x, offset.y) },
                        onDrag = { change, _ -> pathState.value.lineTo(change.position.x, change.position.y); val newPath = Path(); newPath.addPath(pathState.value); pathState.value = newPath }
                    )
                }) { drawPath(path = pathState.value, color = Color.Black, style = Stroke(width = 6f)) }
            }
        }
    }
}

@Composable
fun ItemRow(itemName: String, selectedStatus: String, isReadOnly: Boolean, onStatusSelected: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(itemName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusButton("BOM", "bom", selectedStatus == "bom", Color(0xFF10B981), Modifier.weight(1f)) { if(!isReadOnly) onStatusSelected("bom") }
                Spacer(Modifier.width(8.dp))
                StatusButton("RUIM", "ruim", selectedStatus == "ruim", Color(0xFFEF4444), Modifier.weight(1f)) { if(!isReadOnly) onStatusSelected("ruim") }
                Spacer(Modifier.width(8.dp))
                StatusButton("N/A", "na", selectedStatus == "na", Color(0xFF94A3B8), Modifier.weight(1f)) { if(!isReadOnly) onStatusSelected("na") }
            }
        }
    }
}

@Composable
fun StatusButton(label: String, value: String, isSelected: Boolean, activeColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) activeColor else InputBg, contentColor = if (isSelected) Color.White else TextMuted),
        shape = RoundedCornerShape(12.dp), modifier = modifier.height(44.dp), elevation = ButtonDefaults.buttonElevation(if(isSelected) 4.dp else 0.dp)
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Black) }
}

fun captureSignatureToBase64(path: androidx.compose.ui.graphics.Path): String {
    if (path.isEmpty) return ""
    val bounds = path.getBounds()
    val padding = 40f
    val width = (bounds.width + padding * 2).toInt()
    val height = (bounds.height + padding * 2).toInt()
    if (width <= 0 || height <= 0) return ""
    val bitmap = androidx.core.graphics.createBitmap(width, height)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    // Copia para não deslocar a assinatura que está na tela (o transform altera o path)
    val androidPath = android.graphics.Path(path.asAndroidPath())
    val matrix = android.graphics.Matrix()
    matrix.setTranslate(-bounds.left + padding, -bounds.top + padding)
    androidPath.transform(matrix)
    canvas.drawPath(androidPath, paint)
    val outputStream = java.io.ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
    val byteArray = outputStream.toByteArray()
    return "data:image/png;base64," + android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
}