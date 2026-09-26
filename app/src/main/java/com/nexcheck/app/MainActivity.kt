package com.nexcheck.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        FirebaseApp.initializeApp(this)
        setContent {
            NexCheckApp()
        }
    }
}

@Composable
fun NexCheckApp() {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()

    val startDestination = if (auth.currentUser != null) "menu" else "login"

    NavHost(navController = navController, startDestination = startDestination) {

        // TELA 1: LOGIN
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("menu") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // TELA 2: MENU PRINCIPAL
        composable("menu") {
            MainMenuScreen(
                onNavigateToInspection = { tipoVeiculo ->
                    navController.navigate("inspection/$tipoVeiculo")
                },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToPendencies = { navController.navigate("pendencies") },
                onNavigateToSchedules = { navController.navigate("schedules") },
                onNavigateToUserManagement = { navController.navigate("userManagement") },

                onNavigateToSmokePendencies = { navController.navigate("smoke_pendencies") },
                onNavigateToSmokeForm = { navController.navigate("smoke_form?placa=&empresa=") },
                onNavigateToLogbooks = { navController.navigate("logbooks") },

                // Rota para abrir a Lista de Ocorrências
                onNavigateToIncidents = { navController.navigate("incidents") },

                onLogout = {
                    auth.signOut()
                    navController.navigate("login") {
                        popUpTo("menu") { inclusive = true }
                    }
                }
            )
        }

        // TELA 3 e 8: VISTORIA NOVA E VISUALIZAÇÃO DE HISTÓRICO
        composable(
            route = "inspection/{tipo}?placa={placa}&prefixo={prefixo}&modelo={modelo}&empresa={empresa}&motorista={motorista}&cliente={cliente}&acao={acao}&docId={docId}",
            arguments = listOf(
                navArgument("tipo") { type = NavType.StringType },
                navArgument("placa") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("prefixo") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("modelo") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("empresa") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("motorista") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("cliente") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("acao") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("docId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            InspectionScreen(
                tipo = backStackEntry.arguments?.getString("tipo") ?: "cavalo",
                prePlaca = backStackEntry.arguments?.getString("placa"),
                prePrefixo = backStackEntry.arguments?.getString("prefixo"),
                preModelo = backStackEntry.arguments?.getString("modelo"),
                preEmpresa = backStackEntry.arguments?.getString("empresa"),
                preMotorista = backStackEntry.arguments?.getString("motorista"),
                preCliente = backStackEntry.arguments?.getString("cliente"),
                preAcao = backStackEntry.arguments?.getString("acao"),
                docId = backStackEntry.arguments?.getString("docId"),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // TELA 4: VISTORIAS REALIZADAS (HISTÓRICO)
        composable("history") {
            InspectionsListScreen(
                onBack = { navController.popBackStack() },
                onViewInspection = { idVistoria ->
                    navController.navigate("inspection/view?docId=$idVistoria")
                }
            )
        }

        // TELA 5: PENDÊNCIAS GERAIS
        composable("pendencies") {
            PendenciesScreen(
                onBack = { navController.popBackStack() },
                onStartInspection = { tipo, placa, prefixo, modelo, empresa, motorista, cliente, acao ->
                    val p = Uri.encode(placa)
                    val pr = Uri.encode(prefixo)
                    val m = Uri.encode(modelo)
                    val e = Uri.encode(empresa)
                    val mot = Uri.encode(motorista)
                    val cli = Uri.encode(cliente)
                    val act = Uri.encode(acao)
                    navController.navigate("inspection/$tipo?placa=$p&prefixo=$pr&modelo=$m&empresa=$e&motorista=$mot&cliente=$cli&acao=$act")
                }
            )
        }

        // TELA 6: AGENDAMENTOS
        composable("schedules") {
            SchedulingScreen(onBack = { navController.popBackStack() })
        }

        // TELA 7: GESTÃO DE USUÁRIOS E PERMISSÕES
        composable("userManagement") {
            UserManagementScreen(onBack = { navController.popBackStack() })
        }

        // =========================================================
        // MÓDULO DE FUMAÇA
        // =========================================================
        composable("smoke_pendencies") {
            SmokePendenciesScreen(
                onBack = { navController.popBackStack() },
                onNewSmokeInspection = { placa, empresa ->
                    val p = Uri.encode(placa)
                    val e = Uri.encode(empresa)
                    navController.navigate("smoke_form?placa=$p&empresa=$e")
                }
            )
        }

        composable(
            route = "smoke_form?placa={placa}&empresa={empresa}",
            arguments = listOf(
                navArgument("placa") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("empresa") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            SmokeFormScreen(
                prePlaca = backStackEntry.arguments?.getString("placa"),
                preEmpresa = backStackEntry.arguments?.getString("empresa"),
                onBack = { navController.popBackStack() }
            )
        }

        // =========================================================
        // MÓDULO DIÁRIO DE BORDO
        // =========================================================
        composable("logbooks") {
            LogbookListScreen(
                onBack = { navController.popBackStack() },
                onNewLogbook = { navController.navigate("logbook_form") }
            )
        }

        composable("logbook_form") {
            LogbookFormScreen(onBack = { navController.popBackStack() })
        }

        // =========================================================
        // 🔥 NOVO: MÓDULO DE OCORRÊNCIAS / ACIDENTES
        // =========================================================

        // Tela 13: Lista de Ocorrências
        composable("incidents") {
            IncidentsScreen(
                onBack = { navController.popBackStack() },
                onNewIncident = { navController.navigate("incident_form") }
            )
        }

        // Tela 14: Formulário de Ocorrências com a IA
        composable("incident_form") {
            IncidentFormScreen(onBack = { navController.popBackStack() })
        }
    }
}