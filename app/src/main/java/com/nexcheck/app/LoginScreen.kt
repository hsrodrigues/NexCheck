package com.nexcheck.app

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) } // Adicionado para UX
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

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

    // Cores originais mantidas
    val slate900 = Color(0xFF0F172A)
    val blue600 = Color(0xFF2563EB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(slate900)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Logo",
            tint = blue600,
            modifier = Modifier.size(80.dp)
        )

        Text("NexCheck", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 8.dp))
        Text("GESTÃO DE INSPEÇÕES", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 32.dp))

        // BOTÃO GOOGLE COM ÍCONE
        Button(
            onClick = { signInWithGoogle() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google), // Certifique-se de ter o ic_google em res/drawable
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Continuar com Google", color = Color.DarkGray, fontWeight = FontWeight.Bold)
            }
        }

        // SEPARADOR "OU"
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.DarkGray)
            Text(" OU ", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.DarkGray)
        }

        // CAMPOS E-MAIL E SENHA COM ÍCONES E UX MELHORADA
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                focusedBorderColor = blue600,
                unfocusedBorderColor = Color.Transparent
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = null, tint = Color.Gray)
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                focusedBorderColor = blue600,
                unfocusedBorderColor = Color.Transparent
            )
        )

        if (errorMessage.isNotEmpty()) {
            Text(errorMessage, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        // BOTÃO ACESSAR
        Button(
            onClick = {
                if (email.isBlank() || password.isEmpty()) {
                    errorMessage = "Informe e-mail e senha."
                } else {
                    isLoading = true
                    errorMessage = ""
                    auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
                        isLoading = false
                        if (task.isSuccessful) onLoginSuccess() else errorMessage = "E-mail ou senha incorretos."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = blue600)
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("ACESSAR CONTA", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}