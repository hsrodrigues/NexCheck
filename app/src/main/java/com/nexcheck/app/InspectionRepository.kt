package com.nexcheck.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class InspectionRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 1. Busca as Perguntas Dinâmicas
    fun getChecklistSchema(tipo: String, onResult: (ChecklistData?) -> Unit) {
        db.collection("checklists").document(tipo).get()
            .addOnSuccessListener { doc -> onResult(if (doc.exists()) doc.toObject(ChecklistData::class.java) else null) }
            .addOnFailureListener { onResult(null) }
    }

    // 2. Busca Transportadoras
    fun getCarriers(onResult: (List<String>) -> Unit) {
        db.collection("carriers").get()
            .addOnSuccessListener { snap ->
                val carriers = snap.documents.mapNotNull { it.getString("name") }
                onResult(carriers)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // 3. Busca Clientes
    fun getClients(onResult: (List<Map<String, String>>) -> Unit) {
        db.collection("clients").get()
            .addOnSuccessListener { snap ->
                val clients = snap.documents.mapNotNull { doc ->
                    val name = doc.getString("name")
                    if (name != null) mapOf("id" to doc.id, "name" to name) else null
                }
                onResult(clients)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // 4. Busca as Vistorias. O histórico usa as 50 mais recentes; pendências e agenda
    // passam limit = null, porque precisam da última vistoria de CADA placa.
    fun getAllInspections(limit: Long? = 50, onResult: (List<Map<String, Any>>) -> Unit) {
        var query = db.collection("inspections")
            .orderBy("inspectionDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
        if (limit != null) query = query.limit(limit)
        query.get()
            .addOnSuccessListener { snap ->
                onResult(snap.documents.map { doc -> doc.data.orEmpty() + ("id" to doc.id) })
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // 5. Salva a Vistoria e dispara as Automações Web (Torre e Agenda)
    fun salvarVistoria(
        inspectionType: String,
        clientId: String?,
        clientName: String,
        transportadora: String,
        placa: String,
        marca: String,
        modelo: String,
        km: String,
        motorista: String,
        composicao2: String,
        composicao3: String,
        linkedCavaloPlate: String,
        inspectionDate: Date,
        nextInspectionDate: Date,
        respostas: Map<String, String>,
        observacoes: String,
        servicos: String,
        statusFinal: String,
        assinaturaMotoristaBase64: String,
        assinaturaInspetorBase64: String,
        isCavalo: Boolean,
        tipoVeiculo: String,
        inspectorName: String,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val counterRef = db.collection("settings").document("counters")
        val mainPlate = placa

        // =================================================================
        // DATA DE VENCIMENTO: usa a data informada na tela (padrão: +6 meses),
        // às 08:00. Se não liberado, vence na hora (vira revistoria).
        // =================================================================
        val calAgendamento = Calendar.getInstance()
        calAgendamento.time = nextInspectionDate

        val dataVencimentoReal: Date = if (statusFinal == "Liberado") {
            calAgendamento.set(Calendar.HOUR_OF_DAY, 8)
            calAgendamento.set(Calendar.MINUTE, 0)
            calAgendamento.set(Calendar.SECOND, 0)
            calAgendamento.set(Calendar.MILLISECOND, 0)
            calAgendamento.time
        } else {
            inspectionDate
        }

        db.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val currentNumber = snapshot.getLong("currentInspectionNumber") ?: 0
            val nextNumber = currentNumber + 1

            // set+merge cria o documento de contadores se ele ainda não existir
            transaction.set(counterRef, mapOf("currentInspectionNumber" to nextNumber), SetOptions.merge())

            val vehicleInfo = hashMapOf<String, Any>()
            if (isCavalo) {
                vehicleInfo["placa"] = placa.uppercase()
                vehicleInfo["marca"] = marca.uppercase()
                vehicleInfo["modelo"] = modelo.uppercase()
                vehicleInfo["km"] = km
            } else {
                vehicleInfo["composicao1"] = placa.uppercase()
                vehicleInfo["composicao2"] = composicao2.uppercase()
                vehicleInfo["composicao3"] = composicao3.uppercase()
                vehicleInfo["linkedCavaloPlate"] = linkedCavaloPlate.uppercase()
            }

            // Chave da resposta: "idSeção|títuloSeção|item"
            val sectionTitles = mutableMapOf<String, String>()
            val sectionItems = mutableMapOf<String, MutableMap<String, String>>()
            respostas.forEach { (itemKey, status) ->
                val parts = itemKey.split("|", limit = 3)
                if (parts.size == 3) {
                    val (sectionId, sectionTitle, itemName) = parts
                    sectionTitles[sectionId] = sectionTitle
                    sectionItems.getOrPut(sectionId) { mutableMapOf() }[itemName] = status
                }
            }
            val itemsStructured = sectionItems.mapValues { (sectionId, items) ->
                mapOf("title" to sectionTitles[sectionId], "items" to items)
            }

            val vistoriaData = hashMapOf(
                "inspectionNumber" to nextNumber,
                "vehicleType" to tipoVeiculo,
                "inspectionType" to inspectionType,
                "vehicleInfo" to vehicleInfo,
                "clientId" to clientId,
                "clientName" to clientName,
                "company" to transportadora.uppercase(),
                "driverName" to motorista.uppercase(),
                "status" to statusFinal,
                "observations" to observacoes.uppercase(),
                "servicesNeeded" to servicos.uppercase(),
                "inspectionDate" to inspectionDate,
                "nextInspectionDate" to dataVencimentoReal, // Usa a data calculada aqui fora!
                "driverSignature" to assinaturaMotoristaBase64,
                "inspectorSignature" to assinaturaInspetorBase64,
                "inspectorId" to auth.currentUser?.uid,
                "inspectorName" to inspectorName,
                "items" to itemsStructured,
                "isOfflineData" to false
            )

            val docId = "${mainPlate.uppercase()}-$nextNumber"
            transaction.set(db.collection("inspections").document(docId), vistoriaData)

            nextNumber
        }.addOnSuccessListener { numeroGerado ->

            // =================================================================
            // MÁGICA WEB 1: ATUALIZA A TORRE DE CONTROLE
            // =================================================================
            val sdfTorre = SimpleDateFormat("dd/MM/yyyy", BrLocale)
            val dateStringBR = sdfTorre.format(inspectionDate)
            val towerId = "${mainPlate.uppercase()}-${dateStringBR.replace("/", "-")}"

            val payloadTorre = hashMapOf(
                "vehiclePlate" to mainPlate.uppercase(),
                "clientName" to transportadora.uppercase(),
                "status" to statusFinal,
                "dateString" to dateStringBR,
                "updatedAt" to FieldValue.serverTimestamp(),
                "inspectionId" to numeroGerado,
                "lastEditor" to inspectorName,
                "lastEditorRole" to "ANDROID"
            )
            db.collection("tower_events").document(towerId).set(payloadTorre, SetOptions.merge())

            // =================================================================
            // MÁGICA WEB 2: LIMPA AGENDAMENTOS FANTASMAS
            // =================================================================
            val placasParaLimpar = listOf(placa, linkedCavaloPlate).filter { it.isNotBlank() }.distinct()
            placasParaLimpar.forEach { placaAlvo ->
                db.collection("schedules").whereEqualTo("vehiclePlate", placaAlvo.uppercase()).get()
                    .addOnSuccessListener { snap ->
                        val batch = db.batch()
                        snap.documents.forEach { docAg ->
                            val statusAg = docAg.getString("status")
                            if (statusAg != "Realizado" && statusAg != "Concluído") {
                                batch.delete(docAg.reference)
                            }
                        }
                        batch.commit()
                    }
            }

            // =================================================================
            // MÁGICA WEB 3: BUSCA VAGA DE 1 EM 1 HORA E AGENDA
            // =================================================================
            if (statusFinal == "Liberado" && isCavalo) {
                agendarComVagaDisponivel(
                    baseDate = dataVencimentoReal,
                    mainPlate = mainPlate,
                    clientId = clientId,
                    transportadora = transportadora,
                    numeroGerado = numeroGerado,
                    inspectorId = auth.currentUser?.uid,
                    inspectorName = inspectorName
                )
            }

            onSuccess(numeroGerado.toInt())

        }.addOnFailureListener { e ->
            onError(e.message ?: "Erro ao gravar no banco")
        }
    }

    // =========================================================================
    // 🤖 O ROBÔ QUE CAÇA VAGAS NA AGENDA (LÓGICA DIRETO DO JS)
    // =========================================================================
    private fun agendarComVagaDisponivel(
        baseDate: Date,
        mainPlate: String,
        clientId: String?,
        transportadora: String,
        numeroGerado: Long,
        inspectorId: String?,
        inspectorName: String
    ) {
        val calStart = Calendar.getInstance().apply {
            time = baseDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val calEnd = Calendar.getInstance().apply {
            time = calStart.time
            add(Calendar.DAY_OF_YEAR, 15) // Olha 15 dias pra frente
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }

        // 1. Puxa tudo o que tá agendado nos próximos 15 dias
        db.collection("schedules")
            .whereGreaterThanOrEqualTo("scheduledDate", calStart.time)
            .whereLessThanOrEqualTo("scheduledDate", calEnd.time)
            .get()
            .addOnSuccessListener { snap ->
                val occupiedSlots = mutableSetOf<String>()
                val sdfKey = SimpleDateFormat("yyyy-MM-dd-HH", BrLocale)

                // Mapeia todas as horas já ocupadas (ex: "2026-11-25-08")
                snap.documents.forEach { doc ->
                    val date = doc.getTimestamp("scheduledDate")?.toDate()
                    if (date != null) {
                        occupiedSlots.add(sdfKey.format(date))
                    }
                }

                // 2. Tenta achar o buraco
                var slotFound: Date? = null
                val searchCal = Calendar.getInstance().apply { time = calStart.time }

                loop@ for (day in 0..14) {
                    for (h in 8..17) { // Procura das 08:00 às 17:00
                        searchCal.set(Calendar.HOUR_OF_DAY, h)
                        searchCal.set(Calendar.MINUTE, 0)

                        val key = sdfKey.format(searchCal.time)

                        // Se a hora não estiver no Set de ocupadas, ACHAMOS A VAGA!
                        if (!occupiedSlots.contains(key)) {
                            slotFound = searchCal.time
                            break@loop
                        }
                    }
                    searchCal.add(Calendar.DAY_OF_YEAR, 1) // Pula pro dia seguinte
                }

                // 3. Fallback (Se lotar 15 dias seguidos, joga pras 08:00 do dia inicial mesmo)
                val finalDate = slotFound ?: Calendar.getInstance().apply {
                    time = baseDate
                    set(Calendar.HOUR_OF_DAY, 8)
                    set(Calendar.MINUTE, 0)
                }.time

                // 4. Salva o Agendamento Oficial
                val newSchedule = hashMapOf(
                    "vehiclePlate" to mainPlate.uppercase().trim(),
                    "clientId" to clientId,
                    "clientName" to transportadora.uppercase().trim(),
                    "scheduledDate" to com.google.firebase.Timestamp(finalDate),
                    "status" to "Agendado",
                    "originStatus" to "Em Dia",
                    "notes" to "Agendamento automático criado pela Vistoria Nº$numeroGerado (App Android)",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "inspectorId" to inspectorId,
                    "inspectorName" to inspectorName
                )

                db.collection("schedules").add(newSchedule)
                    .addOnSuccessListener { println("✅ Vaga encontrada e agendada para: $finalDate") }
            }
            .addOnFailureListener {
                // Em caso de erro na consulta, salva bruto nas 08:00
                val fallbackDate = Calendar.getInstance().apply {
                    time = baseDate
                    set(Calendar.HOUR_OF_DAY, 8)
                    set(Calendar.MINUTE, 0)
                }.time

                val newSchedule = hashMapOf(
                    "vehiclePlate" to mainPlate.uppercase().trim(),
                    "clientId" to clientId,
                    "clientName" to transportadora.uppercase().trim(),
                    "scheduledDate" to com.google.firebase.Timestamp(fallbackDate),
                    "status" to "Agendado",
                    "originStatus" to "Em Dia",
                    "notes" to "Agendamento automático (FALLBACK) Vistoria Nº$numeroGerado",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "inspectorId" to inspectorId,
                    "inspectorName" to inspectorName
                )
                db.collection("schedules").add(newSchedule)
            }
    }
    // Busca a logo da empresa salva nas configurações
    fun getCompanyLogo(onResult: (String?) -> Unit) {
        db.collection("settings").document("branding").get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onResult(doc.getString("companyLogoBase64"))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
    }
    // =========================================================================
    // 📅 MÓDULO DE AGENDAMENTOS
    // =========================================================================

    // Busca todos os agendamentos (Para o calendário)
    fun getSchedules(onResult: (List<Map<String, Any>>) -> Unit) {
        db.collection("schedules")
            .orderBy("scheduledDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.map { doc ->
                    val map = doc.data?.toMutableMap() ?: mutableMapOf()
                    map["id"] = doc.id
                    map
                }
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // Salva ou Atualiza um agendamento
    fun saveSchedule(
        id: String?,
        plate: String,
        carrier: String,
        date: Date,
        notes: String,
        statusOrigin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val data = hashMapOf<String, Any>(
            "vehiclePlate" to plate.uppercase().trim(),
            "clientName" to carrier.uppercase().trim(),
            "scheduledDate" to com.google.firebase.Timestamp(date),
            "status" to "Agendado",
            "originStatus" to statusOrigin,
            "notes" to notes,
            "inspectorName" to (auth.currentUser?.displayName ?: "Inspetor")
        )

        if (id.isNullOrEmpty()) {
            data["createdAt"] = FieldValue.serverTimestamp()
            db.collection("schedules").add(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it.message ?: "Erro") }
        } else {
            db.collection("schedules").document(id).update(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it.message ?: "Erro") }
        }
    }

    // Atualiza só o status de origem (usado pelo auto-expirar da agenda)
    fun updateScheduleOriginStatus(id: String, originStatus: String) {
        db.collection("schedules").document(id).update("originStatus", originStatus)
    }

    // Exclui um agendamento
    fun deleteSchedule(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("schedules").document(id).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Erro") }
    }
}