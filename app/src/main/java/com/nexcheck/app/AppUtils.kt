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
import androidx.compose.ui.graphics.asAndroidPath
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

// Converte a assinatura desenhada em PNG base64 (formato usado pelo sistema web)
fun captureSignatureToBase64(path: androidx.compose.ui.graphics.Path): String {
    if (path.isEmpty) return ""
    val bounds = path.getBounds()
    val padding = 40f
    val width = (bounds.width + padding * 2).toInt()
    val height = (bounds.height + padding * 2).toInt()
    if (width <= 0 || height <= 0) return ""
    val bitmap = androidx.core.graphics.createBitmap(width, height)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    // Copia para não deslocar a assinatura que está na tela (o transform altera o path)
    val androidPath = android.graphics.Path(path.asAndroidPath())
    val matrix = android.graphics.Matrix()
    matrix.setTranslate(-bounds.left + padding, -bounds.top + padding)
    androidPath.transform(matrix)
    canvas.drawPath(androidPath, paint)
    val outputStream = java.io.ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
    val byteArray = outputStream.toByteArray()
    return "data:image/png;base64," + android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
}
