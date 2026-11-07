package com.overos.overcode
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
private val systemBg=Color(0xFF0B1020)
private val systemPanel=Color(0xFF151F35)
private val systemAccent=Color(0xFF61D9E7)
class SystemActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{
  var data by remember{mutableStateOf<JSONObject?>(null)}
  var message by remember{mutableStateOf("Conecte seu computador OverOS para ver os dados.")}
  var loading by remember{mutableStateOf(false)}
  val scope= rememberCoroutineScope()
  fun refresh(){scope.launch{loading=true;message="Consultando o computador…";try{
   val credentials=PairingTokenStore.load(this@SystemActivity)?:throw IllegalStateException("Nenhum computador pareado. Toque em Conectar na tela inicial.")
   data=withContext(Dispatchers.IO){fetchStatus(credentials)};message="Conectado · dados atualizados"
  }catch(e:Exception){message=e.message?:"Não foi possível consultar o computador."}finally{loading=false}}}
  MaterialTheme(colorScheme=darkColorScheme(background=systemBg,surface=systemPanel,primary=systemAccent)){
   Column(Modifier.fillMaxSize().background(systemBg).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("MEU OVEROS",color=systemAccent,style=MaterialTheme.typography.labelMedium);Text("Meu computador",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)};TextButton(onClick={finish()}){Text("Voltar")}}
    Text(message,color=Color(0xFFB8C6DC),style=MaterialTheme.typography.bodySmall)
    Button(onClick={refresh()},enabled=!loading,modifier=Modifier.fillMaxWidth()){Text(if(loading)"Atualizando…" else "Atualizar status")}
    data?.let{d->
     Text(d.optString("hostname","OverOS"),color=Color.White,style=MaterialTheme.typography.titleLarge)
     Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){MetricCard("CPU",d.optString("cpu_load_1m","—")+" load",d.optInt("cpu_cores").toString()+" núcleos",Modifier.weight(1f));MetricCard("RAM",d.optInt("memory_percent").toString()+"%",d.optInt("memory_used_mb").toString()+" / "+d.optInt("memory_total_mb")+" MB",Modifier.weight(1f))}
     Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){val battery=d.optInt("battery_percent",-1);MetricCard("BATERIA",if(battery>=0)"$battery%" else "N/D",d.optString("battery_status","—"),Modifier.weight(1f));MetricCard("DISCO",d.optInt("storage_used_gb").toString()+" GB","de "+d.optInt("storage_total_gb")+" GB",Modifier.weight(1f))}
     val uptime=d.optLong("uptime_seconds");Text("Tempo ligado: "+(uptime/3600)+" h "+((uptime%3600)/60)+" min",color=Color.LightGray)
    }
    Spacer(Modifier.weight(1f))
    Text("A conexão valida o certificado HTTPS do pareamento e usa a credencial protegida pelo Android Keystore.",color=Color(0xFF8FA3C1),style=MaterialTheme.typography.bodySmall)
   }
  }
 }}
}
@Composable private fun MetricCard(title:String,value:String,detail:String,modifier:Modifier=Modifier){
 Card(modifier=modifier,colors=CardDefaults.cardColors(containerColor=systemPanel),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,color=systemAccent,style=MaterialTheme.typography.labelSmall);Text(value,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(detail,color=Color.LightGray,style=MaterialTheme.typography.bodySmall)}}
}
private fun fetchStatus(credentials:PairCredentials):JSONObject{
 val expected=credentials.fingerprint.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
 val trust=object:X509TrustManager{
  override fun getAcceptedIssuers():Array<X509Certificate> = emptyArray()
  override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String){throw CertificateException("Cliente não autorizado")}
  override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String){if(chain.isEmpty())throw CertificateException("Certificado ausente");chain[0].checkValidity();val actual=MessageDigest.getInstance("SHA-256").digest(chain[0].encoded);if(!MessageDigest.isEqual(expected,actual))throw CertificateException("Certificado mudou. Faça o pareamento novamente.")}
 }
 val ssl=SSLContext.getInstance("TLS");ssl.init(null,arrayOf(trust),SecureRandom())
 val connection=URL(credentials.baseUrl.trimEnd('/')+"/api/v1/system/status").openConnection() as HttpsURLConnection
 try{connection.sslSocketFactory=ssl.socketFactory;connection.hostnameVerifier=javax.net.ssl.HostnameVerifier{_,_->true};connection.requestMethod="GET";connection.connectTimeout=7000;connection.readTimeout=7000;connection.setRequestProperty("Authorization","Bearer "+credentials.token);connection.setRequestProperty("Accept","application/json");val status=connection.responseCode;val stream=if(status in 200..299)connection.inputStream else connection.errorStream;val body=stream?.bufferedReader()?.use{it.readText()}?:"{}";val json=JSONObject(body);if(status !in 200..299)throw IllegalStateException(json.optString("error","HTTP $status"));return json}finally{connection.disconnect()}
}
