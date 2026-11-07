package com.overos.overcode

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val bg = Color(0xFF101218)
private val panel = Color(0xFF1A1F29)
private val cyan = Color(0xFF69D9DC)

private data class ChatMessage(val user: Boolean, val text: String)

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
            var lastTaskId by remember { mutableStateOf(prefs.getString("last_task_id", "") ?: "") }
            val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(loadHistory(prefs)) } }
            val scope = rememberCoroutineScope()

            fun saveHistory() {
                val array = JSONArray()
                messages.takeLast(100).forEach {
                    array.put(JSONObject().put("user", it.user).put("text", it.text))
                }
                prefs.edit().putString("history", array.toString()).apply()
            }
            fun addMessage(user: Boolean, text: String) {
                messages.add(ChatMessage(user, text))
                while (messages.size > 100) messages.removeAt(0)
                saveHistory()
            }

            MaterialTheme(colorScheme = darkColorScheme(background=bg, surface=panel, primary=cyan)) {
                Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("overcode", style=MaterialTheme.typography.titleLarge, color=Color.White)
                            Text(status, style=MaterialTheme.typography.labelSmall, color=cyan)
                        }
                        TextButton(onClick={ editing=!editing }) { Text("Conexão") }
                    }
                    if(editing) {
                        OutlinedTextField(base,{base=it},label={Text("Endereço da Kelly API")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(token,{token=it},label={Text("Token da API")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        Button(onClick={
                            prefs.edit().putString("base",base.trim().trimEnd('/')).putString("token",token.trim()).apply()
                            base=base.trim().trimEnd('/'); token=token.trim(); editing=false
                            status="Configuração salva"
                        },modifier=Modifier.fillMaxWidth()) { Text("Salvar conexão") }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical=12.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        if(messages.isEmpty()) item { Text("Converse com Kelly. Use /help para ver os comandos do OverCode.",color=Color.LightGray) }
                        items(messages) { m ->
                            Surface(color=if(m.user) Color(0xFF26343B) else panel, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth()) {
                                Text(m.text,Modifier.padding(13.dp),color=Color.White)
                            }
                        }
                    }
                    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(input,{input=it},modifier=Modifier.weight(1f),placeholder={Text("Mensagem para Kelly…")},maxLines=4)
                        Button(enabled=input.isNotBlank() && !editing && (!busy || input.trim().startsWith("/cancel")),onClick={
                            val msg=input.trim(); input=""
                            scope.launch {
                                try {
                                    when {
                                        msg.equals("/help", true) -> addMessage(false, helpText())
                                        msg.equals("/clear", true) -> {
                                            messages.clear(); saveHistory()
                                            addMessage(false, "Histórico desta conversa limpo.")
                                        }
                                        msg.equals("/rewind", true) -> {
                                            if(messages.isNotEmpty()) messages.removeAt(messages.lastIndex)
                                            if(messages.isNotEmpty() && messages.last().user) messages.removeAt(messages.lastIndex)
                                            saveHistory()
                                            addMessage(false, "Última interação removida do histórico local.")
                                        }
                                        msg.equals("/context", true) -> addMessage(false, "Contexto local: ${messages.size} mensagens, ${messages.sumOf { it.text.length }} caracteres. As últimas 100 mensagens são salvas neste aparelho.")
                                        msg.equals("/status", true) -> {
                                            status="Verificando API…"
                                            val health=withContext(Dispatchers.IO) { getJson(base, "/api/health", token) }
                                            val ready=health.optBoolean("executor_configured", false)
                                            status=if(ready) "Kelly API conectada · executor configurado" else "Kelly API conectada · executor não configurado"
                                            addMessage(false, "API: ${health.optString("service", "Kelly")}\nExecutor Kelly Code: ${if(ready) "configurado" else "ainda não conectado"}\nRaiz de projetos: ${health.optString("project_root", "não informada")}")
                                        }
                                        msg.startsWith("/plan ", true) -> {
                                            addMessage(true, msg)
                                            busy=true; status="Kelly está planejando…"
                                            val planPrompt="MODO PLANEJAMENTO: não execute ferramentas nem afirme que alterou arquivos. Produza apenas um plano numerado, dependências, riscos, arquivos prováveis e testes para esta tarefa: ${msg.substringAfter(' ')}"
                                            val answer=withContext(Dispatchers.IO) { postChat(base,token,planPrompt,chatHistory(messages.dropLast(1))) }
                                            addMessage(false, answer); status="Plano pronto"
                                        }
                                        msg.startsWith("/task ", true) || msg.startsWith("/code ", true) -> {
                                            val taskPrompt=msg.substringAfter(' ').trim()
                                            if(taskPrompt.isBlank()) addMessage(false, "Descreva a tarefa depois de /task.")
                                            else {
                                                addMessage(true, msg); busy=true; status="Enviando tarefa para Kelly Code…"
                                                val task=withContext(Dispatchers.IO) { createTask(base,token,taskPrompt) }
                                                lastTaskId=task.optString("id")
                                                prefs.edit().putString("last_task_id",lastTaskId).apply()
                                                addMessage(false, "Tarefa ${lastTaskId.take(8)} recebida: ${task.optString("status")}. Acompanhando eventos…")
                                                val finalTask=followTask(base,token,lastTaskId,::addMessage) { status=it }
                                                val result=finalTask.optString("result")
                                                val error=finalTask.optString("error")
                                                if(result.isNotBlank()) addMessage(false,result)
                                                else if(error.isNotBlank()) addMessage(false,"Kelly Code não conseguiu concluir: $error")
                                                status="Kelly Code · ${finalTask.optString("status","finalizado")}"
                                            }
                                        }
                                        msg.startsWith("/cancel", true) -> {
                                            val id=msg.substringAfter(' ',lastTaskId).trim()
                                            if(id.isBlank()) addMessage(false, "Não há tarefa recente para cancelar.")
                                            else {
                                                val result=withContext(Dispatchers.IO) { deleteTask(base,token,id) }
                                                addMessage(false, "Cancelamento solicitado: ${result.optString("status", "solicitado")}.")
                                            }
                                        }
                                        msg.startsWith("/diff", true) -> addMessage(false, "O diff ainda não está exposto pela API desta versão. Não vou simular alterações que o servidor não reportou.")
                                        msg.startsWith("/permissions", true) -> addMessage(false, "A API exige token Bearer e limita projetos à raiz configurada. A execução real depende de um KELLY_EXECUTOR com validação de permissões no servidor.")
                                        msg.equals("/save-session", true) || msg.equals("/resume", true) -> {
                                            saveHistory()
                                            addMessage(false, "Sessão salva automaticamente neste aparelho. O histórico será restaurado quando você abrir o OverCode.")
                                        }
                                        msg.equals("/compact", true) -> addMessage(false, "A compactação inteligente de contexto ainda não está implementada. O histórico local permanece salvo.")
                                        else -> {
                                            addMessage(true, msg); busy=true; status="Kelly está respondendo…"
                                            val answer=withContext(Dispatchers.IO) { postChat(base,token,msg,chatHistory(messages.dropLast(1))) }
                                            addMessage(false, answer); status="Kelly conectada"
                                        }
                                    }
                                } catch(e:Exception) {
                                    addMessage(false, "Falha: ${e.message ?: "erro desconhecido"}. Confira endereço, token e se a Kelly API está ativa.")
                                    status="API indisponível"
                                } finally { busy=false }
                            }
                        }) { Text(if(busy) "…" else "➤") }
                    }
                }
            }
        }
    }
}

private fun loadHistory(prefs: android.content.SharedPreferences): List<ChatMessage> {
    return try {
        val array=JSONArray(prefs.getString("history","[]") ?: "[]")
        (0 until array.length()).map { i ->
            val item=array.getJSONObject(i)
            ChatMessage(item.optBoolean("user"),item.optString("text"))
        }
    } catch(_:Exception) { emptyList() }
}

private fun chatHistory(messages: List<ChatMessage>): JSONArray {
    val array=JSONArray()
    messages.takeLast(30).forEach { m ->
        array.put(JSONObject().put("role",if(m.user) "user" else "assistant").put("content",m.text))
    }
    return array
}

private fun helpText() = """Comandos OverCode 2.8:
 /plan <tarefa> — cria um plano sem executar.
 /task <tarefa> — delega para Kelly Code.
 /code <tarefa> — atalho para /task.
 /status — verifica API e executor.
 /cancel [id] — solicita cancelamento da última tarefa.
 /diff — informa disponibilidade do diff.
 /context — mostra tamanho do histórico local.
 /rewind — remove a última interação local.
 /save-session — salva o histórico neste aparelho.
 /resume — restaura automaticamente a sessão salva.
 /compact — compactação inteligente (ainda não disponível).
 /clear — limpa o histórico local.
 /permissions — mostra os limites de segurança.

Mensagens normais continuam no chat da Kelly."""

private fun postChat(base:String, token:String, message:String, history:JSONArray):String {
    val conn=URL(base.trimEnd('/')+"/api/chat").openConnection() as HttpURLConnection
    try {
        conn.requestMethod="POST"; conn.connectTimeout=10000; conn.readTimeout=60000
        conn.setRequestProperty("Content-Type","application/json")
        conn.setRequestProperty("Authorization","Bearer $token")
        conn.doOutput=true
        conn.outputStream.use { it.write(JSONObject().put("message",message).put("history",history).toString().toByteArray()) }
        val code=conn.responseCode
        val stream=if(code in 200..299) conn.inputStream else conn.errorStream
        val body=stream.bufferedReader().use { it.readText() }
        if(code !in 200..299) throw IllegalStateException(JSONObject(body).optString("error","HTTP $code"))
        return JSONObject(body).optString("answer","Kelly não retornou uma resposta.")
    } finally { conn.disconnect() }
}

private fun getJson(base:String,path:String,token:String):JSONObject {
    val conn=URL(base.trimEnd('/')+path).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout=10000; conn.readTimeout=15000
        if(token.isNotBlank()) conn.setRequestProperty("Authorization","Bearer $token")
        val code=conn.responseCode
        val stream=if(code in 200..299) conn.inputStream else conn.errorStream
        val body=stream.bufferedReader().use { it.readText() }
        if(code !in 200..299) throw IllegalStateException(JSONObject(body).optString("error","HTTP $code"))
        return JSONObject(body)
    } finally { conn.disconnect() }
}

private fun createTask(base:String,token:String,prompt:String):JSONObject {
    val conn=URL(base.trimEnd('/')+"/api/tasks").openConnection() as HttpURLConnection
    try {
        conn.requestMethod="POST"; conn.connectTimeout=10000; conn.readTimeout=20000
        conn.setRequestProperty("Content-Type","application/json")
        conn.setRequestProperty("Authorization","Bearer $token")
        conn.doOutput=true
        conn.outputStream.use { it.write(JSONObject().put("prompt",prompt).toString().toByteArray()) }
        val code=conn.responseCode
        val stream=if(code in 200..299) conn.inputStream else conn.errorStream
        val body=stream.bufferedReader().use { it.readText() }
        if(code !in 200..299) throw IllegalStateException(JSONObject(body).optString("error","HTTP $code"))
        return JSONObject(body)
    } finally { conn.disconnect() }
}

private fun deleteTask(base:String,token:String,id:String):JSONObject {
    val conn=URL(base.trimEnd('/')+"/api/tasks/"+id).openConnection() as HttpURLConnection
    try {
        conn.requestMethod="DELETE"; conn.connectTimeout=10000; conn.readTimeout=15000
        conn.setRequestProperty("Authorization","Bearer $token")
        val code=conn.responseCode
        val stream=if(code in 200..299) conn.inputStream else conn.errorStream
        val body=stream.bufferedReader().use { it.readText() }
        if(code !in 200..299) throw IllegalStateException(JSONObject(body).optString("error","HTTP $code"))
        return JSONObject(body)
    } finally { conn.disconnect() }
}

private suspend fun followTask(
    base:String, token:String, id:String,
    addMessage:(Boolean,String)->Unit, setStatus:(String)->Unit
): JSONObject {
    return withContext(Dispatchers.IO) {
        val conn=URL(base.trimEnd('/')+"/api/tasks/"+id+"/events").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout=10000; conn.readTimeout=30000
            conn.setRequestProperty("Authorization","Bearer $token")
            val code=conn.responseCode
            if(code !in 200..299) {
                val body=(conn.errorStream ?: conn.inputStream).bufferedReader().use { it.readText() }
                throw IllegalStateException(JSONObject(body).optString("error","HTTP $code"))
            }
            val main=Handler(Looper.getMainLooper())
            conn.inputStream.bufferedReader().use { reader ->
                while(true) {
                    val line=reader.readLine() ?: break
                    if(!line.startsWith("data: ")) continue
                    val event=JSONObject(line.removePrefix("data: "))
                    val kind=event.optString("type")
                    val message=event.optString("message")
                    val label=when(kind) {
                        "queued" -> "Na fila"
                        "working" -> "Executando"
                        "planning" -> "Planejando"
                        "researching" -> "Pesquisando"
                        "testing" -> "Testando"
                        "awaiting_approval" -> "Aguardando aprovação"
                        "completed" -> "Concluído"
                        "failed" -> "Falhou"
                        "cancelled" -> "Cancelado"
                        else -> kind.ifBlank { "Kelly Code" }
                    }
                    main.post {
                        addMessage(false,"[$label] $message")
                        setStatus("Kelly Code · $label")
                    }
                }
            }
            getJson(base,"/api/tasks/"+id,token)
        } finally { conn.disconnect() }
    }
}
