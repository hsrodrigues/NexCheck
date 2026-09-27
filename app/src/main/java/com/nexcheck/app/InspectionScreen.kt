package com.nexcheck.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.nexcheck.app.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

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

    // Chaves de todos os itens do checklist atual, para o progresso
    val allKeys = checklist?.sections?.flatMap { s -> s.items.map { "${s.id}|${s.title}|$it" } } ?: emptyList()
    val answered = allKeys.count { responses.containsKey(it) }
    val vehicleLabel = if (isCavalo) "Cavalo mecânico" else "Implemento / Carreta"

    // Retrato do formulário logo após carregar (inclui o pré-preenchimento da revistoria),
    // para só pedir confirmação ao sair se o usuário realmente alterou algo
    fun formState(): List<Any> = listOf(
        inspectionType, clientName, transportadora, placa, marca, modelo, motorista, km,
        composicao1, composicao2, composicao3, linkedCavaloPlate, inspectionDate, nextInspectionDate,
        observacoes, responses.toMap()
    )
    var initialState by remember { mutableStateOf<List<Any>?>(null) }
    LaunchedEffect(isLoading) { if (!isLoading && initialState == null) initialState = formState() }
    val hasChanges = !isReadOnly && initialState != null && (
        formState() != initialState || !signatureDriver.value.isEmpty || !signatureInspector.value.isEmpty
    )
    var showDiscardDialog by remember { mutableStateOf(false) }
    fun tryLeave() { if (hasChanges && !isSaving) showDiscardDialog = true else onNavigateBack() }
    BackHandler(enabled = hasChanges && !isSaving) { showDiscardDialog = true }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text("Descartar vistoria?") },
            text = { Text("Os dados preenchidos e os itens marcados no checklist serão perdidos.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onNavigateBack()
                }) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Continuar editando") } }
        )
    }

    fun salvar() {
        val placaFinal = if (isCavalo) placa else composicao1
        if (placaFinal.isBlank() || transportadora.isBlank()) {
            Toast.makeText(context, "Atenção: Placa e Transportadora são obrigatórios!", Toast.LENGTH_SHORT).show()
            return
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
            tipoVeiculo = checklist?.title ?: if (isCavalo) "Inspeção Eletromecânica - Cavalo" else "Inspeção Eletromecânica - Implemento",
            inspectorName = inspetor,
            onSuccess = {
                isSaving = false
                Toast.makeText(context, "Vistoria salva com sucesso!", Toast.LENGTH_LONG).show()
                onNavigateBack()
            },
            onError = { erro ->
                isSaving = false
                Toast.makeText(context, "Erro no Servidor: $erro", Toast.LENGTH_LONG).show()
            }
        )
    }

    NexScaffold(
        title = when {
            isReadOnly -> "Detalhes da vistoria"
            inspectionType == "Revistoria" -> "Revistoria"
            else -> "Nova vistoria"
        },
        subtitle = vehicleLabel,
        onBack = { tryLeave() },
        bottomBar = {
            if (!isReadOnly && !isLoading) {
                BottomActionBar {
                    if (allKeys.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Checklist", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("$answered de ${allKeys.size} itens", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { if (allKeys.isEmpty()) 0f else answered / allKeys.size.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            drawStopIndicator = {}
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    PrimaryButton(text = "Salvar vistoria", icon = Icons.Default.Check, loading = isSaving, onClick = { salvar() })
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            LoadingState(Modifier.padding(padding))
            return@NexScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- RESUMO (modo visualização) ---
            if (isReadOnly) {
                item {
                    NexCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlateTag(if (isCavalo) placa else composicao1, large = true)
                            Spacer(Modifier.weight(1f))
                            StatusPill(statusFinal)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()) {
                            InfoItem("Tipo", inspectionType, Modifier.weight(1f))
                            InfoItem("Data", inspectionDate, Modifier.weight(1f))
                        }
                        if (isCavalo && nextInspectionDate.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            InfoItem("Próxima inspeção", nextInspectionDate, valueColor = MaterialTheme.colorScheme.primary, emphasize = true)
                        }
                    }
                }
            }

            // --- INFORMAÇÕES GERAIS ---
            item { SectionTitle("Informações gerais") }
            item {
                NexCard {
                    if (!isReadOnly) {
                        Text("Tipo de vistoria", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        SegmentedChoice(
                            options = listOf("Vistoria" to "Vistoria inicial", "Revistoria" to "Revistoria"),
                            selected = inspectionType,
                            onSelect = { inspectionType = it },
                            toneOf = { if (it == "Revistoria") StatusTone.Revisit else StatusTone.Info }
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    SelectField(
                        value = clientName,
                        options = allClients.mapNotNull { it["name"] },
                        onSelect = { name ->
                            clientName = name
                            clientId = allClients.firstOrNull { it["name"] == name }?.get("id")
                        },
                        label = "Cliente",
                        readOnly = isReadOnly
                    )
                    Spacer(Modifier.height(12.dp))
                    CarrierField(value = transportadora, onValueChange = { transportadora = it }, carriers = allCarriers, readOnly = isReadOnly)
                    Spacer(Modifier.height(12.dp))

                    if (isCavalo) {
                        NexTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = "Placa *", readOnly = isReadOnly)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NexTextField(value = marca, onValueChange = { marca = it.uppercase() }, label = "Prefixo / Marca", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                            NexTextField(value = modelo, onValueChange = { modelo = it.uppercase() }, label = "Modelo", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NexTextField(value = km, onValueChange = { km = it }, label = "Hodômetro", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), readOnly = isReadOnly)
                            NexTextField(value = motorista, onValueChange = { motorista = it.uppercase() }, label = "Motorista *", modifier = Modifier.weight(1.6f), readOnly = isReadOnly)
                        }
                    } else {
                        NexTextField(value = linkedCavaloPlate, onValueChange = { linkedCavaloPlate = it.uppercase() }, label = "Cavalo vinculado (placa) *", readOnly = isReadOnly)
                        Spacer(Modifier.height(12.dp))
                        NexTextField(value = composicao1, onValueChange = { composicao1 = it.uppercase() }, label = "1ª composição (placa) *", readOnly = isReadOnly)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NexTextField(value = composicao2, onValueChange = { composicao2 = it.uppercase() }, label = "2ª composição", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                            NexTextField(value = composicao3, onValueChange = { composicao3 = it.uppercase() }, label = "3ª composição", modifier = Modifier.weight(1f), readOnly = isReadOnly)
                        }
                    }

                    if (!isReadOnly) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NexTextField(value = inspectionDate, onValueChange = { inspectionDate = it }, label = "Data / hora", modifier = Modifier.weight(1f))
                            NexTextField(value = nextInspectionDate, onValueChange = { nextInspectionDate = it }, label = "Vencimento", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // --- CHECKLIST ---
            checklist?.sections?.forEach { section ->
                val keys = section.items.map { "${section.id}|${section.title}|$it" }
                val done = keys.count { responses.containsKey(it) }
                item(key = "header-${section.id}") {
                    SectionTitle(
                        title = section.title,
                        modifier = Modifier.padding(top = 8.dp),
                        trailing = {
                            if (!isReadOnly && done < keys.size) {
                                TextButton(onClick = { keys.forEach { k -> if (!responses.containsKey(k)) responses[k] = "bom" } }) {
                                    Text("Restantes: Bom", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Text("$done/${keys.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
                items(section.items, key = { "${section.id}|$it" }) { itemDesc ->
                    val uniqueKey = "${section.id}|${section.title}|$itemDesc"
                    ChecklistItemCard(
                        itemName = itemDesc,
                        selected = responses[uniqueKey] ?: "",
                        options = InspectionOptions,
                        readOnly = isReadOnly,
                        onSelect = { responses[uniqueKey] = it }
                    )
                }
            }

            // --- CONCLUSÃO ---
            item { SectionTitle("Conclusão", Modifier.padding(top = 8.dp)) }
            item {
                NexCard {
                    NexTextField(value = observacoes, onValueChange = { observacoes = it.uppercase() }, label = "Observações gerais", singleLine = false, minLines = 3, readOnly = isReadOnly)
                    Spacer(Modifier.height(12.dp))
                    NexTextField(value = servicos, onValueChange = { servicos = it.uppercase() }, label = "Serviços necessários", singleLine = false, minLines = 3, readOnly = isReadOnly)
                    Spacer(Modifier.height(16.dp))
                    Text("Resultado final", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(
                        options = listOf("Liberado" to "Liberado", "Não Liberado" to "Não liberado"),
                        selected = statusFinal,
                        onSelect = { statusFinal = it },
                        enabled = !isReadOnly,
                        toneOf = { toneFor(it) }
                    )
                }
            }

            // --- ASSINATURAS ---
            if (!isReadOnly) {
                item { SectionTitle("Assinaturas", Modifier.padding(top = 8.dp)) }
                item { SignaturePad(if (isCavalo) "Motorista" else "Responsável", signatureDriver) }
                item { SignaturePad("Vistoriador", signatureInspector) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
