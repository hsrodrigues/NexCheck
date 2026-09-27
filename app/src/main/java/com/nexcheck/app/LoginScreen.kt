package com.nexcheck.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.nexcheck.app.ui.components.NexTextField
import com.nexcheck.app.ui.components.PrimaryButton
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var infoMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()

    // ID do cliente Web (OAuth) do projeto Firebase
    val webClientId = "1058312594915-9c4lsl1hua7qgi8cqfi4e6t61fqlmf66.apps.googleusercontent.com"

    // Login Google via Credential Manager (substitui o GoogleSignIn, removido do Play Services)
    val credentialManager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    fun signInWithGoogle() {
        isLoading = true
        errorMessage = ""
        scope.launch {
            try {
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                    .build()
                val credential = credentialManager.getCredential(context, request).credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).addOnCompleteListener { authTask ->
                        isLoading = false
                        if (authTask.isSuccessful) {
                            onLoginSuccess()
                        } else {
                            errorMessage = "Erro no Firebase: ${authTask.exception?.message}"
                        }
                    }
                } else {
                    isLoading = false
                    errorMessage = "Tipo de credencial não suportado."
                }
            } catch (e: GetCredentialCancellationException) {
                isLoading = false // usuário fechou a janela do Google
            } catch (e: NoCredentialException) {
                isLoading = false
                errorMessage = "Nenhuma conta Google encontrada neste aparelho."
            } catch (e: GetCredentialException) {
                isLoading = false
                errorMessage = "Falha no Google Login: ${e.message}"
            }
        }
    }

    fun signInWithEmail() {
        if (email.isBlank() || password.isEmpty()) {
            errorMessage = "Informe e-mail e senha."
            return
        }
        isLoading = true
        errorMessage = ""
        infoMessage = ""
        auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
            isLoading = false
            if (task.isSuccessful) onLoginSuccess() else errorMessage = "E-mail ou senha incorretos."
        }
    }

    fun resetPassword() {
        infoMessage = ""
        if (email.isBlank()) {
            errorMessage = "Informe seu e-mail acima para redefinir a senha."
            return
        }
        isLoading = true
        errorMessage = ""
        auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { task ->
            isLoading = false
            if (task.isSuccessful) infoMessage = "Enviamos um link de redefinição para ${email.trim()}."
            else errorMessage = "Não foi possível enviar o e-mail de redefinição."
        }
    }

    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    val brandGradient = Brush.verticalGradient(listOf(Color(0xFF0A1020), Color(0xFF12275E), Color(0xFF1D4ED8)))

    Box(Modifier.fillMaxSize().background(brandGradient)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- MARCA (centralizada no espaço acima do cartão) ---
            Spacer(Modifier.statusBarsPadding().weight(1f).heightIn(min = 32.dp))
            run {
                // Logo oficial (já traz o nome NexCheck) sobre cartão branco, como no ícone do app.
                // O foreground do ícone adaptativo tem margem de segurança, por isso é ampliado e recortado.
                Box(
                    Modifier.size(136.dp)
                        // Brilho azul atrás da logo (desenhado fora dos limites, sem ocupar espaço)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(Color(0xFF3B82F6).copy(alpha = 0.45f), Color.Transparent),
                                    center = center, radius = size.minDimension * 1.15f
                                ),
                                radius = size.minDimension * 1.15f
                            )
                        }
                        .shadow(24.dp, RoundedCornerShape(36.dp), spotColor = Color(0xFF0A1020))
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = "NexCheck",
                        modifier = Modifier.requiredSize(232.dp)
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Inspeções eletromecânicas e gestão de frota",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))

            // --- CARTÃO DE ACESSO ---
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp)) {
                    Text("Entrar", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Use sua conta corporativa para continuar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    NexTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "E-mail",
                        leadingIcon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None
                    )
                    Spacer(Modifier.height(12.dp))
                    NexTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Senha",
                        leadingIcon = Icons.Default.Lock,
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.None,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha"
                                )
                            }
                        }
                    )
                    TextButton(
                        onClick = { resetPassword() },
                        enabled = !isLoading,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Esqueci minha senha", style = MaterialTheme.typography.labelLarge)
                    }

                    if (infoMessage.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(infoMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }

                    if (errorMessage.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(text = "Acessar conta", loading = isLoading, onClick = { signInWithEmail() })
                    Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "ou",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    OutlinedButton(
                        onClick = { signInWithGoogle() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Continuar com Google", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }

                    if (appVersion.isNotEmpty()) {
                        Text(
                            "Versão $appVersion",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp)
                        )
                    }
                }
            }
        }
    }
}
