package com.nexcheck.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentsScreen(onBack: () -> Unit, onNewIncident: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val sdfDate = SimpleDateFormat("dd/MM/yyyy HH:mm", BrLocale)

    var incidents by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }

    // Variável que controla a abertura do Modal de Detalhes
    var selectedIncident by remember { mutableStateOf<Map<String, Any>?>(null) }

    // Escuta em tempo real e remove o listener ao sair da tela
    DisposableEffect(Unit) {
        val registration = db.collection("incidents").orderBy("date", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) incidents = snapshot.documents.map { it.data.orEmpty() + ("id" to it.id) }
                isLoading = false
            }
        onDispose { registration.remove() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        containerColor = FintechBg,
        topBar = {
            TopAppBar(
                title = { Text("Ocorrências", fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewIncident, containerColor = Color(0xFFDC2626), contentColor = Color.White) {
                Icon(Icons.Default.Add, "Novo")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = searchText, onValueChange = { searchText = it.uppercase() },
                placeholder = { Text("Buscar Placa, Motorista ou Empresa...") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp), leadingIcon = { Icon(Icons.Default.Search, null) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedBorderColor = PrimaryBlue, unfocusedBorderColor = CardBorder
                )
            )

            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFDC2626))
            else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val filtered = incidents.filter {
                        val placa = it["plate"]?.toString() ?: ""
                        val mot = it["driver"]?.toString() ?: ""
                        val comp = it["company"]?.toString() ?: ""
                        placa.contains(searchText) || mot.contains(searchText) || comp.contains(searchText)
                    }
                    items(filtered) { inc ->
                        // Passamos a ação de clique para salvar a ocorrência selecionada
                        IncidentCard(inc, sdfDate) { selectedIncident = inc }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL DE DETALHES DA OCORRÊNCIA
    // =========================================================================
    selectedIncident?.let { data ->
        val type = data["type"]?.toString() ?: "OUTRO"
        val date = (data["date"] as? Timestamp)?.toDate()?.let { sdfDate.format(it) } ?: "--/--/----"
        val cause = data["cause"]?.toString() ?: "A ANALISAR"

        val badgeColor = when (type) {
            "ACIDENTE" -> Color(0xFFDC2626)
            "INCIDENTE" -> Color(0xFF2563EB)
            "FALHA MECÂNICA" -> Color(0xFFEA580C)
            else -> Color(0xFF4B5563)
        }

        Dialog(
            onDismissRequest = { selectedIncident = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = FintechBg) {
                Column {
                    TopAppBar(
                        title = { Text("Detalhes da Ocorrência", fontWeight = FontWeight.Black) },
                        navigationIcon = {
                            IconButton(onClick = { selectedIncident = null }) {
                                Icon(Icons.Default.Close, "Fechar")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )

                    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                        // CABEÇALHO COM DADOS DO VEÍCULO E MOTORISTA
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(data["plate"]?.toString() ?: "S/P", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                        Surface(color = badgeColor.copy(alpha=0.1f), shape = RoundedCornerShape(4.dp)) {
                                            Text(type, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text("Data e Hora:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text(date, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark, modifier = Modifier.padding(bottom = 8.dp))

                                    Text("Transportadora:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text(data["company"]?.toString() ?: "-", fontSize = 15.sp, color = TextDark, modifier = Modifier.padding(bottom = 8.dp))

                                    Text("Motorista:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text(data["driver"]?.toString() ?: "-", fontSize = 15.sp, color = TextDark, modifier = Modifier.padding(bottom = 8.dp))

                                    Text("Localização:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text(data["location"]?.toString() ?: "Não informado", fontSize = 15.sp, color = TextDark)
                                }
                            }
                        }

                        // DESTAQUE DA CAUSA RAIZ (INTELIGÊNCIA ARTIFICIAL)
                        item {
                            SectionTitle("CAUSA RAIZ / SISTEMA")
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)), border = BorderStroke(1.dp, Color(0xFFBFDBFE)), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    val causeColor = if(cause == "A ANALISAR / OUTROS") Color(0xFFDC2626) else Color(0xFF1D4ED8)
                                    Text(cause, fontWeight = FontWeight.Black, color = causeColor, fontSize = 16.sp)
                                }
                            }
                        }

                        // TEXTO COMPLETO DO RELATO
                        item {
                            SectionTitle("RELATO DETALHADO")
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, CardBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(data["relato"]?.toString() ?: "Sem descrição preenchida pelo vistoriador.", fontSize = 14.sp, color = TextDark, lineHeight = 22.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Atualizado para receber o onClick
@Composable
private fun IncidentCard(inc: Map<String, Any>, sdf: SimpleDateFormat, onClick: () -> Unit) {
    val type = inc["type"]?.toString() ?: "OUTRO"
    val date = (inc["date"] as? Timestamp)?.toDate()?.let { sdf.format(it) } ?: "--/--/----"

    val badgeColor = when (type) {
        "ACIDENTE" -> Color(0xFFDC2626)
        "INCIDENTE" -> Color(0xFF2563EB)
        "FALHA MECÂNICA" -> Color(0xFFEA580C)
        else -> Color(0xFF4B5563)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth().clickable { onClick() } // AÇÃO DE CLIQUE AQUI
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(badgeColor))
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(inc["plate"]?.toString() ?: "S/P", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Surface(color = badgeColor.copy(alpha=0.1f), shape = RoundedCornerShape(4.dp)) {
                        Text(type, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text(inc["company"]?.toString() ?: "-", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Motorista: ${inc["driver"]?.toString() ?: "-"}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(date, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                }

                Surface(color = FintechBg, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(inc["relato"]?.toString() ?: "Sem descrição", fontSize = 11.sp, color = TextDark, modifier = Modifier.padding(8.dp), maxLines = 2)
                }
            }
        }
    }
}