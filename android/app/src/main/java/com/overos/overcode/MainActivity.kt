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
            val messages = remember { mutableStateListOf<Pair<Boolean,String>>() }
            val scope = rememberCoroutineScope()
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
                        if(messages.isEmpty()) item { Text("Converse com Kelly. Para executar tarefas, o motor OverCode precisa estar conectado no servidor.",color=Color.LightGray) }
                        items(messages) { m -> 
                            Surface(color=if(m.first) Color(0xFF26343B) else panel, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth()) {
                                Text(m.second,Modifier.padding(13.dp),color=Color.White)
                            }
                        }
                    }
                    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(input,{input=it},modifier=Modifier.weight(1f),placeholder={Text("Mensagem para Kelly…")},maxLines=4)
                        Button(enabled=!busy && input.isNotBlank() && !editing,onClick={
                            val msg=input.trim(); input=""; messages.add(true to msg); busy=true; status="Kelly está respondendo…"
                            scope.launch {
                                try {
                                    val answer=withContext(Dispatchers.IO) { postChat(base,token,msg) }
                                    messages.add(false to answer); status="Kelly conectada"
                                } catch(e:Exception) { messages.add(false to "Falha na conexão: ${e.message}\nConfira endereço, token e se a API está ligada."); status="API indisponível" }
                                busy=false
                            }
                        }) { Text(if(busy) "…" else "➤") }
                    }
                }
            }
        }
    }
    private fun postChat(base:String, token:String, message:String):String {
        val conn=URL(base.trimEnd('/')+"/api/chat").openConnection() as HttpURLConnection
        try {
            conn.requestMethod="POST"; conn.connectTimeout=10000; conn.readTimeout=60000
            conn.setRequestProperty("Content-Type","application/json")
            conn.setRequestProperty("Authorization","Bearer $token")
            conn.doOutput=true
            conn.outputStream.use { it.write(JSONObject().put("message",message).put("history",org.json.JSONArray()).toString().toByteArray()) }
            val stream=if(conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val body=stream.bufferedReader().use { it.readText() }
            if(conn.responseCode !in 200..299) throw IllegalStateException(JSONObject(body).optString("error","HTTP ${conn.responseCode}"))
            return JSONObject(body).optString("answer","Kelly não retornou uma resposta.")
        } finally { conn.disconnect() }
    }
}
