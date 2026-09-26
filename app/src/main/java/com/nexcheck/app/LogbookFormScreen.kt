package com.nexcheck.app

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nexcheck.app.ui.components.*

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

    val sections = checklistConfig?.get("sections").asMapList()
    val allItems = sections.flatMap { it["items"].asStringList() }
    val answered = allItems.count { responses.containsKey(it) }

    fun salvar() {
        if (placa.isBlank() || transportadora.isBlank() || motorista.isBlank() || signature.value.isEmpty) {
            Toast.makeText(context, "Preencha todos os campos obrigatórios e a assinatura.", Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true
        // Fora da transação: ela pode ser reexecutada pelo Firestore
        val signatureBase64 = captureSignatureToBase64(signature.value)

        db.runTransaction { transaction ->
            val counterRef = db.collection("settings").document("logbook_counters")
            val nextId = (transaction.get(counterRef).getLong("current") ?: 0L) + 1
            // set+merge cria o contador se ele ainda não existir
            transaction.set(counterRef, mapOf("current" to nextId), SetOptions.merge())

            // Mesma estrutura do sistema web
            var statusGlobal = "Aprovado"
            val failedItems = mutableListOf<String>()
            val fullChecklistData = mutableListOf<Map<String, Any>>()

            sections.forEach { section ->
                val sTitle = section["title"].toString()
                val sectionItems = mutableListOf<Map<String, String>>()
                section["items"].asStringList().forEach { itmName ->
                    val st = responses[itmName] ?: "NA"
                    if (st == "NOK") {
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
                "bodyType" to carroceria,
                "status" to statusGlobal,
                "failedItems" to failedItems,
                "checklistStruct" to fullChecklistData,
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
    }

    NexScaffold(
        title = "Novo diário de bordo",
        subtitle = "Checklist pré-carregamento",
        onBack = onBack,
        bottomBar = {
            if (!isLoading) {
                BottomActionBar {
                    if (allItems.isNotEmpty()) {
                        Text(
                            "$answered de ${allItems.size} itens verificados",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    PrimaryButton("Salvar diário", onClick = { salvar() }, loading = isSaving, icon = Icons.Default.Check)
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
            item { SectionTitle("Veículo e motorista") }
            item {
                NexCard {
                    NexTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = "Placa *")
                    Spacer(Modifier.height(12.dp))
                    CarrierField(value = transportadora, onValueChange = { transportadora = it }, carriers = allCarriers)
                    Spacer(Modifier.height(12.dp))
                    NexTextField(value = motorista, onValueChange = { motorista = it.uppercase() }, label = "Motorista *")

                    Spacer(Modifier.height(16.dp))
                    Text("Composição", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(listOf("PADRÃO" to "Padrão", "RODOTREM" to "Rodotrem"), type, { type = it })

                    Spacer(Modifier.height(16.dp))
                    Text("Carroceria", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    SegmentedChoice(listOf("SIDER" to "Sider", "GRADE BAIXA" to "Grade baixa"), carroceria, { carroceria = it })
                }
            }

            sections.forEach { section ->
                val title = section["title"]?.toString() ?: ""
                val items = section["items"].asStringList()
                item { SectionTitle(title, Modifier.padding(top = 8.dp)) }
                items(items) { item ->
                    ChecklistItemCard(
                        itemName = item,
                        selected = responses[item] ?: "",
                        options = LogbookOptions,
                        onSelect = { responses[item] = it }
                    )
                }
            }

            item { SectionTitle("Assinatura", Modifier.padding(top = 8.dp)) }
            item { SignaturePad("Assinatura do motorista *", signature) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
