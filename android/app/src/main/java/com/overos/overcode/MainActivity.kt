package com.overos.overcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
private enum class AgentMode { KELLY, CODE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("overcode", MODE_PRIVATE)
        setContent {
            var base by remember { mutableStateOf(prefs.getString("base", "http://192.168.3.30:8765") ?: "") }
            var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
            var editing by remember { mutableStateOf(base.isBlank() || token.isBlank()) }
            var input by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("Conecte à Kelly no OverOS") }
            var mode by remember { mutableStateOf(AgentMode.KELLY) }
            var activeTaskId by remember { mutableStateOf<String?>(null) }
            var executionExpanded by remember { mutableStateOf(true) }
            val messages = remember { mutableStateListOf<ChatMessage>() }
            val executionLog = remember { mutableStateListOf<String>() }
            val scope = rememberCoroutineScope()

            MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = cyan)) {
                Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("overcode", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Text(status, style = MaterialTheme.typography.labelSmall, color = cyan)
                        }
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

                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            input, { input = it }, modifier = Modifier.weight(1f),
                            placeholder = { Text(if (mode == AgentMode.KELLY) "Mensagem para Kelly…" else "Tarefa para Kelly Code…") },
                            maxLines = 4
                        )
                        Button(enabled = !busy && input.isNotBlank() && !editing, onClick = {
                            val prompt = input.trim()
                            input = ""
                            messages.add(ChatMessage(true, prompt))
                            busy = true
                            if (mode == AgentMode.KELLY) {
                                status = "Kelly está respondendo…"
                                scope.launch {
                                    try {
                                        val answer = withContext(Dispatchers.IO) { postChat(base, token, prompt) }
                                        messages.add(ChatMessage(false, answer))
                                        status = "Kelly conectada"
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
                                        val taskId = withContext(Dispatchers.IO) { submitTask(base, token, prompt) }
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
            }
        }
    }

    private fun authorizedConnection(url: String, token: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 0
        conn.setRequestProperty("Authorization", "Bearer $token")
        return conn
    }

    private fun postChat(base: String, token: String, message: String): String {
        val conn = authorizedConnection(base.trimEnd('/') + "/api/chat", token, "POST")
        try {
            conn.readTimeout = 60000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use {
                it.write(JSONObject().put("message", message).put("history", org.json.JSONArray())
                    .toString().toByteArray())
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) throw IllegalStateException(
                JSONObject(body).optString("error", "HTTP ${conn.responseCode}"))
            return JSONObject(body).optString("answer", "Kelly não retornou uma resposta.")
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
