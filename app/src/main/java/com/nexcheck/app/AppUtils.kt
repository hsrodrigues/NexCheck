package com.nexcheck.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import java.util.Date
import java.util.Locale
import com.google.firebase.Timestamp

// Locale fixo do app: datas e meses sempre no padrão brasileiro,
// independente do idioma configurado no aparelho.
val BrLocale: Locale = Locale.forLanguageTag("pt-BR")

// =========================================================================
// IMPRESSÃO DE RELATÓRIOS (HTML -> PDF)
// =========================================================================

// O WebView precisa de uma referência forte até terminar de carregar, senão o GC
// pode coletá-lo antes do onPageFinished e a impressão nunca abre. Guardamos no
// conjunto só durante o carregamento e soltamos logo depois (sem vazar a Activity).
private val pendingPrintJobs = mutableSetOf<WebView>()

fun printHtml(context: Context, html: String, jobName: String) {
    val webView = WebView(context)
    pendingPrintJobs.add(webView)
    webView.webViewClient = object : WebViewClient() {
        private var printed = false
        override fun onPageFinished(view: WebView, url: String) {
            // onPageFinished pode disparar mais de uma vez; imprime só uma.
            if (printed) return
            printed = true
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            printManager.print(jobName, view.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
            pendingPrintJobs.remove(view)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
}

// =========================================================================
// WHATSAPP
// =========================================================================

fun openWhatsApp(context: Context, message: String) {
    val intent = Intent(Intent.ACTION_VIEW, "https://api.whatsapp.com/send?text=${Uri.encode(message)}".toUri())
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum app disponível para abrir o WhatsApp.", Toast.LENGTH_SHORT).show()
    }
}

// =========================================================================
// ASSINATURAS / IMAGENS BASE64
// =========================================================================

fun decodeBase64ToBitmap(base64Str: String): ImageBitmap? {
    if (base64Str.isBlank()) return null
    return try {
        val bytes = Base64.decode(base64Str.substringAfter("base64,"), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// =========================================================================
// LEITURA SEGURA DE DADOS DO FIRESTORE
// =========================================================================

// Aceita Timestamp (padrão do Firestore) ou Date; qualquer outro formato vira null
// em vez de derrubar o app com ClassCastException.
fun Any?.asDate(): Date? = when (this) {
    is Timestamp -> toDate()
    is Date -> this
    else -> null
}

fun Any?.asStringMap(): Map<String, Any?>? =
    (this as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v }

fun Any?.asMapList(): List<Map<String, Any?>> =
    (this as? List<*>)?.mapNotNull { it.asStringMap() } ?: emptyList()

fun Any?.asStringList(): List<String> =
    (this as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
