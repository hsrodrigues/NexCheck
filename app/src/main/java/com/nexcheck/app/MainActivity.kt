package com.nexcheck.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.activity.enableEdgeToEdge
import com.nexcheck.app.ui.components.SystemBarIcons
import com.nexcheck.app.ui.theme.NexCheckTheme
import com.nexcheck.app.ui.theme.ThemeMode
import com.nexcheck.app.ui.theme.ThemePreference
import kotlinx.coroutines.launch

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
            val context = LocalContext.current
            var themeMode by remember { mutableStateOf(ThemePreference.load(context)) }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            NexCheckTheme(darkTheme = darkTheme) {
                // Surface define a cor de texto padrão (onBackground) para todo o app;
                // sem ela, textos sem cor explícita saem pretos no tema escuro.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NexCheckApp(
                        themeMode = themeMode,
                        isDarkTheme = darkTheme,
                        onThemeChange = { mode ->
                            themeMode = mode
                            ThemePreference.save(context, mode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NexCheckApp(themeMode: ThemeMode, isDarkTheme: Boolean, onThemeChange: (ThemeMode) -> Unit) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val startDestination = remember { if (auth.currentUser != null) "menu" else "login" }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    val profile = rememberUserProfile(uid)
    val appVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var showLogoutDialog by remember { mutableStateOf(false) }

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    fun openModule(route: String) {
        // Abre o módulo por cima da tela inicial (voltar sempre retorna ao início)
        navController.navigate(route) {
            popUpTo("menu")
            launchSingleTop = true
        }
    }

    // Ícones das barras do sistema sempre no contraste certo:
    // claros no tema escuro, no login (fundo azul-noite) e com o menu aberto (cabeçalho azul)
    val drawerOpen = drawerState.targetValue == DrawerValue.Open
    val lightBackgroundOnTop = !isDarkTheme && currentRoute != "login" && !drawerOpen
    SystemBarIcons(darkStatusIcons = lightBackgroundOnTop, darkNavigationIcons = !isDarkTheme && currentRoute != "login")

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Arrastar da borda só na tela inicial (não atrapalha assinaturas nem o gesto de voltar)
        gesturesEnabled = currentRoute == "menu" || drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                profile = profile,
                email = auth.currentUser?.email.orEmpty(),
                appVersion = appVersion,
                themeMode = themeMode,
                onThemeChange = onThemeChange,
                onNavigate = { route -> closeDrawerThen { openModule(route) } },
                onHome = { closeDrawerThen { navController.popBackStack("menu", inclusive = false) } },
                onLogout = { closeDrawerThen { showLogoutDialog = true } }
            )
        }
    ) {
    // Transição: a tela nova desliza levemente da direita, a anterior esmaece
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(tween(280)) { it / 6 } + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(280)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(240)) }
    ) {

        // TELA 1: LOGIN
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    uid = auth.currentUser?.uid
                    navController.navigate("menu") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // TELA 2: MENU PRINCIPAL
        composable("menu") {
            MainMenuScreen(
                profile = profile,
                appVersion = appVersion,
                isDarkTheme = isDarkTheme,
                onToggleTheme = { onThemeChange(if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK) },
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onNavigate = { route -> openModule(route) }
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

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text("Sair da conta?") },
            text = { Text("Você precisará entrar novamente para usar o app.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    auth.signOut()
                    uid = null
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                }) { Text("Sair", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") } }
        )
    }
}
