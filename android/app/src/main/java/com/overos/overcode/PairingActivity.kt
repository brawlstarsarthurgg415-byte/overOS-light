package com.overos.overcode

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

private val pairBackground = Color(0xFF0B1020)
private val pairPanel = Color(0xFF151F35)
private val pairAccent = Color(0xFF61D9E7)

private data class PairSession(
    val baseUrl: String,
    val sessionId: String,
    val nonce: String,
    val fingerprint: String
)

class PairingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var stage by remember { mutableStateOf("scan") }
            var session by remember { mutableStateOf<PairSession?>(null) }
            var code by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf("Escaneie o QR Code exibido no computador OverOS.") }
            var pairedName by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()

            val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
                val raw = result.contents
                if (raw.isNullOrBlank()) {
                    message = "Leitura cancelada. Você pode tentar novamente."
                } else {
                    try {
                        val data = JSONObject(raw)
                        val url = URL(data.getString("url"))
                        val fp = data.getString("tls_sha256").lowercase()
                        val id = data.getString("session_id")
                        val nonce = data.getString("nonce")
                        require(data.optString("type") == "overos-pair" && data.optInt("version") == 1) {
                            "Este QR Code não pertence ao OverOS Pair."
                        }
                        require(url.protocol == "https" && url.port == 8766 && url.host.isNotBlank()) {
                            "Endereço de pareamento inválido."
                        }
                        require(fp.matches(Regex("[0-9a-f]{64}")) &&
                            id.matches(Regex("[0-9a-f]{32}")) &&
                            nonce.matches(Regex("[0-9a-f]{64}"))) {
                            "Dados de segurança do QR Code inválidos."
                        }
                        val parsed = PairSession(url.toString().trimEnd('/'), id, nonce, fp)
                        busy = true
                        message = "Validando o QR Code com o computador…"
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    pairPost(parsed, "/api/v1/pair/scan",
                                        JSONObject().put("session_id", parsed.sessionId).put("nonce", parsed.nonce))
                                }
                                session = parsed
                                stage = "code"
                                message = "QR validado. Digite o código que apareceu na tela do computador."
                            } catch (e: Exception) {
                                message = "Não foi possível validar este QR: " + (e.message ?: "falha de conexão")
                                stage = "scan"
                            } finally {
                                busy = false
                            }
                        }
                    } catch (e: Exception) {
                        message = e.message ?: "QR Code inválido."
                        stage = "scan"
                    }
                }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                background = pairBackground,
                surface = pairPanel,
                primary = pairAccent
            )) {
                Column(
                    Modifier.fillMaxSize().background(pairBackground).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("OVEROS CONTROL", color = pairAccent, style = MaterialTheme.typography.labelMedium)
                            Text("Parear computador", color = Color.White, style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { finish() }) { Text("Fechar", color = Color.LightGray) }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = pairPanel),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(if (stage == "scan") "1 · Ler QR Code" else if (stage == "code") "2 · Confirmar código" else "Computador pareado",
                                color = pairAccent, style = MaterialTheme.typography.titleMedium)
                            Text(message, color = Color.White)
                            if (stage == "scan") {
                                Text("No computador deve aparecer somente o QR Code e a opção Pular. O código numérico ainda não deve estar visível.",
                                    color = Color(0xFFB8C6DC), style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = {
                                        val options = ScanOptions()
                                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            .setPrompt("Aponte para o QR Code do OverOS")
                                            .setBeepEnabled(false)
                                            .setOrientationLocked(false)
                                        scanLauncher.launch(options)
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (busy) "Validando…" else "Abrir câmera e escanear") }
                            } else if (stage == "code") {
                                Text("Digite os seis números mostrados pelo computador. O código expira junto com esta sessão.",
                                    color = Color(0xFFB8C6DC), style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = code,
                                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                                    label = { Text("Código do computador") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        val current = session ?: return@Button
                                        busy = true
                                        message = "Conferindo o código…"
                                        scope.launch {
                                            try {
                                                val prefs = getSharedPreferences("overos_pairing", MODE_PRIVATE)
                                                val deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString()
                                                val deviceName = android.os.Build.MODEL?.take(80) ?: "Android"
                                                val result = withContext(Dispatchers.IO) {
                                                    pairPost(current, "/api/v1/pair/complete",
                                                        JSONObject()
                                                            .put("session_id", current.sessionId)
                                                            .put("code", code)
                                                            .put("device_id", deviceId)
                                                            .put("device_name", deviceName))
                                                }
                                                val accessToken = result.getString("access_token")
                                                PairingTokenStore.save(this@PairingActivity, current.baseUrl, accessToken, deviceId)
                                                pairedName = result.optString("device_name", "Este celular")
                                                stage = "done"
                                                message = "Pareamento concluído. A credencial foi protegida pelo Android Keystore."
                                            } catch (e: Exception) {
                                                message = "Não foi possível parear: " + (e.message ?: "erro desconhecido")
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    },
                                    enabled = !busy && code.length == 6,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (busy) "Verificando…" else "Validar e parear") }
                                TextButton(onClick = {
                                    session = null
                                    code = ""
                                    stage = "scan"
                                    message = "Escaneie um novo QR Code gerado pelo computador."
                                }) { Text("Voltar e ler outro QR") }
                            } else {
                                Text("✓", color = pairAccent, style = MaterialTheme.typography.displayMedium)
                                Text(pairedName, color = Color.White, style = MaterialTheme.typography.titleLarge)
                                Text("Este celular agora possui uma identidade própria. A chave de acesso foi guardada criptografada pelo Android Keystore.",
                                    color = Color(0xFFB8C6DC))
                                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Concluir") }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("QR e código são aleatórios, temporários e de uso único. O certificado HTTPS é conferido pelo fingerprint do QR.",
                        color = Color(0xFF8FA3C1), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun pairPost(session: PairSession, path: String, payload: JSONObject): JSONObject {
    val expectedFingerprint = session.fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val trustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            throw CertificateException("Cliente não autorizado")
        }
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (chain.isEmpty()) throw CertificateException("Certificado ausente")
            chain[0].checkValidity()
            val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
            if (!MessageDigest.isEqual(expectedFingerprint, actual)) {
                throw CertificateException("A identidade HTTPS não corresponde ao QR Code")
            }
        }
    }
    val ssl = SSLContext.getInstance("TLS")
    ssl.init(null, arrayOf(trustManager), SecureRandom())
    val connection = URL(session.baseUrl + path).openConnection() as HttpsURLConnection
    try {
        connection.sslSocketFactory = ssl.socketFactory
        // A confiança é baseada no fingerprint lido presencialmente no QR, não numa CA pública.
        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        val json = JSONObject(body)
        if (status !in 200..299) throw IllegalStateException(json.optString("error", "HTTP $status"))
        return json
    } finally {
        connection.disconnect()
    }
}

private object PairingTokenStore {
    fun save(activity: ComponentActivity, baseUrl: String, token: String, deviceId: String) {
        val alias = "overos_pair_access_token"
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var key = keyStore.getKey(alias, null) as? javax.crypto.SecretKey
        if (key == null) {
            val generator = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            generator.init(spec)
            key = generator.generateKey()
        }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        activity.getSharedPreferences("overos_pairing", 0).edit()
            .putString("access_token_ciphertext", Base64.encodeToString(packed, Base64.NO_WRAP))
            .putString("paired_base_url", baseUrl)
            .putString("device_id", deviceId)
            .putBoolean("paired", true)
            .apply()
    }
}
