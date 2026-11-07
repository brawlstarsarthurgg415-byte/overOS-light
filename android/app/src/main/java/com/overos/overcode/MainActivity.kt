package com.overos.overcode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import java.util.Locale
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val bg = Color(0xFF101218)
private val panel = Color(0xFF1A1F29)
private val cyan = Color(0xFF69D9DC)

private data class ChatMessage(val fromUser: Boolean, val text: String)
private data class UploadedFile(val id: String, val name: String)
private enum class AgentMode { KELLY, CODE }

class MainActivity : ComponentActivity() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var selectedVoiceName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) textToSpeech?.language = Locale("pt", "BR")
        }
        val prefs = getSharedPreferences("overcode", MODE_PRIVATE)
        setContent {
            var base by remember { mutableStateOf(prefs.getString("base", "http://192.168.3.30:8765") ?: "") }
            var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
            var editing by remember { mutableStateOf(base.isBlank() || token.isBlank()) }
            var input by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("Conecte à Kelly no OverOS") }
            var sessionId by remember { mutableStateOf(prefs.getString("session_id", "") ?: "") }
            var mode by remember { mutableStateOf(AgentMode.KELLY) }
            var activeTaskId by remember { mutableStateOf<String?>(null) }
            var executionExpanded by remember { mutableStateOf(true) }
            var liveMode by remember { mutableStateOf(false) }
            var showVoicePicker by remember { mutableStateOf(false) }
            var voiceOptions by remember { mutableStateOf(emptyList<String>()) }
            var selectedVoice by remember { mutableStateOf(prefs.getString("voice", "") ?: "") }
            val pendingFiles = remember { mutableStateListOf<UploadedFile>() }
            val messages = remember { mutableStateListOf<ChatMessage>() }
            val executionLog = remember { mutableStateListOf<String>() }
            val scope = rememberCoroutineScope()
            fun startLiveListening() {
                if (!liveMode) return
                startVoiceListening(
                    onResult = { spoken ->
                        messages.add(ChatMessage(true, spoken))
                        busy = true
                        scope.launch {
                            try {
                                val reply = withContext(Dispatchers.IO) {
                                    postChat(base, token, spoken, sessionId, emptyList())
                                }
                                sessionId = reply.second
                                prefs.edit().putString("session_id", sessionId).apply()
                                messages.add(ChatMessage(false, reply.first))
                                status = "Kelly respondeu · ouvindo novamente"
                                if (reply.third.isNotBlank()) {
                                    activeTaskId = reply.third
                                    executionLog.add("Kelly Code recebeu a tarefa " + reply.third.take(8))
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                streamTaskEvents(base, token, reply.third) { eventText, terminal ->
                                                    scope.launch(Dispatchers.Main) {
                                                        executionLog.add(eventText)
                                                        if (terminal) activeTaskId = null
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) { executionLog.add("Eventos: " + e.message) }
                                    }
                                }
                                speakVoice(reply.first) { if (liveMode) startLiveListening() }
                            } catch (e: Exception) {
                                messages.add(ChatMessage(false, "Falha na conversa por voz: ${e.message}"))
                                status = "Falha na voz"
                                if (liveMode) startLiveListening()
                            } finally { busy = false }
                        }
                    },
                    onError = { message ->
                        status = message
                        if (liveMode && message.startsWith("Não entendi")) {
                            scope.launch {
                                kotlinx.coroutines.delay(700)
                                if (liveMode) startLiveListening()
                            }
                        }
                    }
                )
            }

            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    try {
                        val uploaded = withContext(Dispatchers.IO) { uploadFile(base, token, uri) }
                        pendingFiles.add(uploaded)
                        status = "Arquivo anexado: ${uploaded.name}"
                    } catch (e: Exception) { status = "Falha no anexo: ${e.message}" }
                }
            }
            val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    liveMode = true
                    startLiveListening()
                } else status = "Permissão de microfone negada"
            }

            MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = cyan)) {
                Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("overcode", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(status, style = MaterialTheme.typography.labelSmall, color = cyan)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                status = "Verificando API…"
                                try {
                                    status = withContext(Dispatchers.IO) { getApiStatus(base) }
                                } catch (e: Exception) { status = "API indisponível: ${e.message}" }
                            }
                        }) { Text("Status") }
                        TextButton(onClick = { editing = !editing }) { Text("Conexão") }
                    }

                    if (editing) {
                        OutlinedTextField(base, { base = it }, label = { Text("Endereço da Kelly API") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(token, { token = it }, label = { Text("Token da API") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            prefs.edit().putString("base", base.trim().trimEnd('/'))
                                .putString("token", token.trim()).apply()
                            base = base.trim().trimEnd('/')
                            token = token.trim()
                            editing = false
                            status = "Configuração salva"
                        }, modifier = Modifier.fillMaxWidth()) { Text("Salvar conexão") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        FilterChip(selected = mode == AgentMode.KELLY, onClick = { mode = AgentMode.KELLY },
                            label = { Text("Kelly") })
                        FilterChip(selected = mode == AgentMode.CODE, onClick = { mode = AgentMode.CODE },
                            label = { Text("Kelly Code") })
                    }

                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (messages.isEmpty()) {
                            item {
                                Text(
                                    if (mode == AgentMode.KELLY)
                                        "Converse com Kelly. Para executar tarefas no projeto, selecione Kelly Code."
                                    else
                                        "Kelly Code recebe tarefas de programação pelo motor do OverOS. O servidor precisa ter um executor configurado.",
                                    color = Color.LightGray
                                )
                            }
                        }
                        items(messages) { message ->
                            Surface(
                                color = if (message.fromUser) Color(0xFF26343B) else panel,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(message.text, Modifier.padding(13.dp), color = Color.White)
                            }
                        }
                    }

                    if (executionLog.isNotEmpty()) {
                        Surface(color = panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Execução Kelly Code", color = cyan, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { executionExpanded = !executionExpanded }) {
                                        Text(if (executionExpanded) "Recolher" else "Ver")
                                    }
                                    if (activeTaskId != null) {
                                        TextButton(onClick = {
                                            val id = activeTaskId
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { cancelTask(base, token, id ?: return@withContext) }
                                                    status = "Cancelamento solicitado"
                                                } catch (e: Exception) {
                                                    executionLog.add("Falha ao cancelar: ${e.message}")
                                                }
                                            }
                                        }) { Text("Parar") }
                                    }
                                }
                                if (executionExpanded) {
                                    executionLog.takeLast(8).forEach { line ->
                                        Text(line, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !busy) {
                            Text("+ Anexar")
                        }
                        TextButton(onClick = {
                            voiceOptions = textToSpeech?.voices?.filter { it.locale.language == "pt" }?.map { it.name } ?: emptyList()
                            showVoicePicker = true
                        }) { Text("Voz") }
                        TextButton(onClick = {
                            if (liveMode) {
                                liveMode = false
                                stopVoice()
                                status = "Modo Live desligado"
                            } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                liveMode = true
                                startLiveListening()
                            } else {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) { Text(if (liveMode) "■ Parar Live" else "◉ Live") }
                        pendingFiles.forEach { file ->
                            TextButton(onClick = { pendingFiles.remove(file) }) {
                                Text("📎 ${file.name} ×", maxLines = 1)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            input, { input = it }, modifier = Modifier.weight(1f),
                            placeholder = { Text(if (mode == AgentMode.KELLY) "Mensagem para Kelly…" else "Tarefa para Kelly Code…") },
                            maxLines = 4
                        )
                        Button(enabled = !busy && (input.isNotBlank() || pendingFiles.isNotEmpty()) && !editing, onClick = {
                            val prompt = input.trim().ifBlank { "Analise os arquivos anexados." }
                            val attachmentIds = pendingFiles.map { it.id }
                            val attachmentNames = pendingFiles.map { it.name }
                            pendingFiles.clear()
                            input = ""
                            messages.add(ChatMessage(true, prompt + if (attachmentNames.isNotEmpty()) "\nAnexos: " + attachmentNames.joinToString() else ""))
                            busy = true
                            if (mode == AgentMode.KELLY) {
                                status = "Kelly está respondendo…"
                                scope.launch {
                                    try {
                                        val reply = withContext(Dispatchers.IO) { postChat(base, token, prompt, sessionId, attachmentIds) }
                                        sessionId = reply.second
                                        prefs.edit().putString("session_id", sessionId).apply()
                                        messages.add(ChatMessage(false, reply.first))
                                        status = "Kelly conectada"
                                        if (reply.third.isNotBlank()) {
                                            activeTaskId = reply.third
                                            executionLog.clear()
                                            executionLog.add("Kelly Code recebeu a tarefa " + reply.third.take(8))
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        streamTaskEvents(base, token, reply.third) { eventText, terminal ->
                                                            scope.launch(Dispatchers.Main) {
                                                                executionLog.add(eventText)
                                                                if (terminal) {
                                                                    activeTaskId = null
                                                                    status = "Tarefa de Kelly Code finalizada"
                                                                    messages.add(ChatMessage(false, eventText))
                                                                }
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    executionLog.add("Eventos: " + e.message)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        messages.add(ChatMessage(false, "Falha na conexão: ${e.message}\nConfira endereço, token e se a API está ligada."))
                                        status = "API indisponível"
                                    } finally { busy = false }
                                }
                            } else {
                                status = "Enviando tarefa para Kelly Code…"
                                executionLog.clear()
                                executionLog.add("Enviando tarefa ao servidor…")
                                scope.launch {
                                    try {
                                        val delegated = withContext(Dispatchers.IO) {
                                            postChat(base, token, "@code " + prompt, sessionId, attachmentIds)
                                        }
                                        sessionId = delegated.second
                                        prefs.edit().putString("session_id", sessionId).apply()
                                        messages.add(ChatMessage(false, delegated.first))
                                        val taskId = delegated.third
                                        if (taskId.isBlank()) throw IllegalStateException("Kelly Code não criou uma tarefa.")
                                        activeTaskId = taskId
                                        executionLog.add("Tarefa criada: ${taskId.take(8)}")
                                        status = "Kelly Code está trabalhando…"
                                        withContext(Dispatchers.IO) {
                                            streamTaskEvents(base, token, taskId) { eventText, terminal ->
                                                scope.launch(Dispatchers.Main) {
                                                    executionLog.add(eventText)
                                                    if (terminal) {
                                                        activeTaskId = null
                                                        status = "Tarefa finalizada"
                                                        messages.add(ChatMessage(false, eventText))
                                                    }
                                                }
                                            }
                                        }
                                        if (activeTaskId != null) {
                                            activeTaskId = null
                                            status = "Conexão de eventos encerrada"
                                        }
                                    } catch (e: Exception) {
                                        executionLog.add("Erro: ${e.message}")
                                        messages.add(ChatMessage(false, "Kelly Code não conseguiu concluir a tarefa: ${e.message}"))
                                        status = "Falha na execução"
                                        activeTaskId = null
                                    } finally { busy = false }
                                }
                            }
                        }) { Text(if (busy) "…" else "➤") }
                    }
                }
                if (showVoicePicker) {
                    AlertDialog(
                        onDismissRequest = { showVoicePicker = false },
                        title = { Text("Voz da Kelly") },
                        text = {
                            Column {
                                if (voiceOptions.isEmpty()) Text("Nenhuma voz em português foi encontrada no mecanismo de voz do Android.")
                                voiceOptions.forEach { voiceName ->
                                    TextButton(onClick = {
                                        val voice = textToSpeech?.voices?.firstOrNull { it.name == voiceName }
                                        if (voice != null) textToSpeech?.voice = voice
                                        selectedVoice = voiceName
                                        selectedVoiceName = voiceName
                                        prefs.edit().putString("voice", voiceName).apply()
                                        showVoicePicker = false
                                    }) { Text(if (voiceName == selectedVoice) "✓ $voiceName" else voiceName) }
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { showVoicePicker = false }) { Text("Fechar") } }
                    )
                }
            }
        }
    }

    private fun startVoiceListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onError("Reconhecimento de voz indisponível neste dispositivo.")
            return
        }
        speechRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de microfone necessária."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Falha de rede no reconhecimento de voz."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não entendi; tente falar novamente."
                    else -> "Erro no reconhecimento de voz ($error)."
                }
                onError(message)
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotBlank()) onResult(spoken) else onError("Não consegui reconhecer a fala.")
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }

    private fun stopVoice() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
    }

    private fun speakVoice(text: String, onDone: () -> Unit) {
        val tts = textToSpeech
        if (tts == null) {
            onDone()
            return
        }
        selectedVoiceName?.let { name -> tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it } }
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { runOnUiThread { onDone() } }
            override fun onError(id: String?) { runOnUiThread { onDone() } }
            override fun onError(id: String?, errorCode: Int) { runOnUiThread { onDone() } }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun uploadFile(base: String, token: String, uri: Uri): UploadedFile {
        val resolver = contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: (uri.lastPathSegment ?: "arquivo")
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Não foi possível abrir o arquivo.")
        if (bytes.isEmpty()) throw IllegalStateException("O arquivo está vazio.")
        if (bytes.size > 8 * 1024 * 1024) throw IllegalStateException("Cada arquivo pode ter no máximo 8 MB.")
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val payload = JSONObject()
            .put("name", displayName)
            .put("content_type", mime)
            .put("content_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
        val conn = authorizedConnection(base.trimEnd('/') + "/api/files", token, "POST")
        try {
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) throw IllegalStateException(
                JSONObject(body).optString("error", "HTTP ${conn.responseCode}"))
            val response = JSONObject(body)
            return UploadedFile(response.getString("id"), response.optString("name", displayName))
        } finally { conn.disconnect() }
    }

    override fun onDestroy() {
        stopVoice()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    private fun getApiStatus(base: String): String {
        val conn = URL(base.trimEnd('/') + "/api/health").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body)
            return if (data.optBoolean("ok")) {
                if (data.optBoolean("executor_configured")) "API online · Kelly Code configurada"
                else "API online · Kelly disponível"
            } else "API respondeu, mas não está saudável"
        } finally { conn.disconnect() }
    }

    private fun authorizedConnection(url: String, token: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 0
        conn.setRequestProperty("Authorization", "Bearer $token")
        return conn
    }

    private fun postChat(base: String, token: String, message: String, sessionId: String, attachments: List<String> = emptyList()): Triple<String, String, String> {
        val conn = authorizedConnection(base.trimEnd('/') + "/api/chat", token, "POST")
        try {
            conn.readTimeout = 60000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use {
                val payload = JSONObject().put("message", message).put("history", org.json.JSONArray()).put("attachments", org.json.JSONArray(attachments))
                if (sessionId.isNotBlank()) payload.put("session_id", sessionId)
                it.write(payload.toString().toByteArray())
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) throw IllegalStateException(
                JSONObject(body).optString("error", "HTTP ${conn.responseCode}"))
            val response = JSONObject(body)
            return Triple(
                response.optString("answer", "Kelly não retornou uma resposta."),
                response.optString("session_id", sessionId),
                response.optString("task_id", "")
            )
        } finally { conn.disconnect() }
    }

    private fun submitTask(base: String, token: String, prompt: String): String {
        val conn = authorizedConnection(base.trimEnd('/') + "/api/tasks", token, "POST")
        try {
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(JSONObject().put("prompt", prompt).toString().toByteArray()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) throw IllegalStateException(
                JSONObject(body).optString("error", "HTTP ${conn.responseCode}"))
            return JSONObject(body).getString("id")
        } finally { conn.disconnect() }
    }

    private fun streamTaskEvents(
        base: String,
        token: String,
        taskId: String,
        onEvent: (String, Boolean) -> Unit
    ) {
        val conn = authorizedConnection(
            base.trimEnd('/') + "/api/tasks/$taskId/events", token, "GET")
        try {
            conn.setRequestProperty("Accept", "text/event-stream")
            if (conn.responseCode !in 200..299) {
                val body = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IllegalStateException("HTTP ${conn.responseCode}: $body")
            }
            conn.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("data:")) {
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isNotEmpty()) {
                            val event = JSONObject(payload)
                            val kind = event.optString("type", "event")
                            val message = event.optString("message", "Atualização da tarefa")
                            val terminal = kind in setOf("completed", "failed", "cancelled")
                            onEvent("$kind: $message", terminal)
                        }
                    }
                }
            }
        } finally { conn.disconnect() }
    }

    private fun cancelTask(base: String, token: String, taskId: String) {
        val conn = authorizedConnection(base.trimEnd('/') + "/api/tasks/$taskId", token, "DELETE")
        try {
            if (conn.responseCode !in 200..299 && conn.responseCode != 409) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            conn.inputStream?.close()
        } finally { conn.disconnect() }
    }
}
