package com.nexcheck.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmokePendenciesScreen(
    onBack: () -> Unit,
    onNewSmokeInspection: (String, String) -> Unit // Placa e Empresa
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val sdf = SimpleDateFormat("dd/MM/yyyy", BrLocale)

    var pendencies by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var searchText by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(Unit) {
        db.collection("smokeInspections").get().addOnSuccessListener { snapshot ->
            val allSmokeInspections = snapshot.documents.map { it.data ?: emptyMap<String, Any>() }

            // 1. Agrupa pela última inspeção de cada placa
            val latestByPlate = mutableMapOf<String, Map<String, Any>>()
            for (insp in allSmokeInspections) {
                val plate = insp["vehiclePlate"]?.toString() ?: continue
                val currentDate = insp["inspectionDate"].asDate()?.time ?: 0L
                val savedDate = latestByPlate[plate]?.get("inspectionDate").asDate()?.time ?: 0L

                if (currentDate >= savedDate) {
                    latestByPlate[plate] = insp
                }
            }

            val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
            val alertDaysFromNow = Calendar.getInstance().apply { time = today; add(Calendar.DAY_OF_YEAR, 15) }.time

            val processedPendencies = mutableListOf<Map<String, Any>>()

            // 2. Calcula os status
            latestByPlate.values.forEach { insp ->
                val nextDate = insp["nextInspectionDate"].asDate() ?: Date(0)
                val result = insp["result"]?.toString() ?: ""

                val statusNormalizado: String
                val colorClass: Color
                val bgClass: Color
                val labelStatus: String

                // Se o resultado foi REPROVADO, já entra como Vencida/Crítica
                if (result == "Reprovado") {
                    statusNormalizado = "vencida"
                    colorClass = Color(0xFFB91C1C) // Red
                    bgClass = Color(0xFFFEE2E2)
                    labelStatus = "REPROVADO"
                } else {
                    if (nextDate < today) {
                        statusNormalizado = "vencida"
                        colorClass = Color(0xFFB91C1C)
                        bgClass = Color(0xFFFEE2E2)
                        labelStatus = "VENCIDA"
                    } else if (nextDate <= alertDaysFromNow) {
                        statusNormalizado = "avencer"
                        colorClass = Color(0xFFB45309) // Yellow
                        bgClass = Color(0xFFFEF3C7)
                        labelStatus = "A VENCER"
                    } else {
                        statusNormalizado = "emdia"
                        colorClass = Color(0xFF15803D) // Green
                        bgClass = Color(0xFFDCFCE7)
                        labelStatus = "EM DIA"
                    }
                }

                val mutableInsp = insp.toMutableMap()
                mutableInsp["_statusNormalizado"] = statusNormalizado
                mutableInsp["_colorClass"] = colorClass
                mutableInsp["_bgClass"] = bgClass
                mutableInsp["_labelStatus"] = labelStatus
                mutableInsp["_validadeJS"] = nextDate
                processedPendencies.add(mutableInsp)
            }

            // Ordena por criticidade
            processedPendencies.sortBy { p ->
                when (p["_statusNormalizado"]) {
                    "vencida" -> 1
                    "avencer" -> 2
                    else -> 3
                }
            }

            pendencies = processedPendencies
            isLoading = false
        }.addOnFailureListener {
            isLoading = false
            Toast.makeText(context, "Erro ao carregar dados", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text("Pendências: Fumaça", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextDark) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNewSmokeInspection("", "") },
                containerColor = PrimaryBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Nova Aferição")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {

            OutlinedTextField(
                value = searchText, onValueChange = { searchText = it.uppercase() },
                placeholder = { Text("Buscar Placa ou Transportadora...") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterTab("TODOS", "ALL", activeFilter) { activeFilter = it }
                FilterTab("VENCIDOS/REPROVADOS", "vencida", activeFilter) { activeFilter = it }
                FilterTab("A VENCER", "avencer", activeFilter) { activeFilter = it }
                FilterTab("EM DIA", "emdia", activeFilter) { activeFilter = it }
            }

            if (isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrimaryBlue)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val filtered = pendencies.filter { p ->
                        val placa = p["vehiclePlate"]?.toString() ?: ""
                        val company = p["companyName"]?.toString() ?: ""
                        val matchSearch = searchText.isEmpty() || placa.contains(searchText) || company.contains(searchText)
                        val matchStatus = activeFilter == "ALL" || p["_statusNormalizado"] == activeFilter
                        matchSearch && matchStatus
                    }

                    if (filtered.isEmpty()) {
                        item { Text("Nenhuma pendência encontrada.", color = TextMuted, modifier = Modifier.padding(20.dp)) }
                    } else {
                        items(filtered) { item ->
                            SmokeCard(item, sdf, onNewSmokeInspection)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmokeCard(
    item: Map<String, Any>,
    sdf: SimpleDateFormat,
    onNewSmokeInspection: (String, String) -> Unit
) {
    val placa = item["vehiclePlate"]?.toString() ?: "S/P"
    val company = item["companyName"]?.toString() ?: "N/A"
    val nivel = item["ringelmannValue"]?.toString() ?: "0"
    val resultado = item["result"]?.toString() ?: ""

    val validadeObj = item["_validadeJS"] as? Date
    val vencimentoStr = validadeObj?.let { sdf.format(it) } ?: "-"

    val colorClass = item["_colorClass"] as? Color ?: TextMuted
    val bgClass = item["_bgClass"] as? Color ?: InputBg
    val labelStatus = item["_labelStatus"]?.toString() ?: ""

    val resultColor = if (resultado == "Aprovado") Color(0xFF16A34A) else Color(0xFFDC2626)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(colorClass))

            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(placa, fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextDark)
                    Surface(color = bgClass, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colorClass.copy(alpha = 0.5f))) {
                        Text(labelStatus, color = colorClass, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Transportadora", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                        Text(company, fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Nível Escala", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                        Text("Nível $nivel (${(nivel.toIntOrNull() ?: 0) * 20}%)", fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Black)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Vencimento", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                        Text(vencimentoStr, fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Black)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Resultado Anterior", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                        Text(resultado.uppercase(), fontSize = 12.sp, color = resultColor, fontWeight = FontWeight.Black)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { onNewSmokeInspection(placa, company) },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("NOVA AFERIÇÃO", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}