package com.overos.meuoveros

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF10243D)
private val Blue = Color(0xFF38A5FF)
private val Muted = Color(0xFF9CB0C8)

class MainActivity : ComponentActivity() {
    private var qrValue by mutableStateOf("")
    private val scanner = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) qrValue = result.contents
        else Toast.makeText(this, "Leitura cancelada", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var screen by remember { mutableStateOf("home") }
            var code by remember { mutableStateOf("") }
            var computerName by remember { mutableStateOf("Nenhum computador conectado") }
            var paired by remember { mutableStateOf(false) }
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Blue, background = Navy, surface = Panel,
                onBackground = Color.White, onSurface = Color.White
            )) {
                Surface(Modifier.fillMaxSize(), color = Navy) {
                    when (screen) {
                        "pair" -> PairScreen(
                            qrValue = qrValue, code = code, onCode = { code = it },
                            onScan = {
                                val options = ScanOptions().setPrompt("Aponte para o QR do OverOS")
                                    .setBeepEnabled(false).setOrientationLocked(false)
                                scanner.launch(options)
                            },
                            onBack = { screen = "home" },
                            onConfirm = {
                                if (qrValue.isBlank() || code.length != 6) {
                                    Toast.makeText(this, "Leia o QR e informe o código de 6 dígitos", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, "QR lido. A validação com o computador ainda precisa do serviço OverOS.", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        else -> HomeScreen(
                            paired = paired, computerName = computerName,
                            onPair = { screen = "pair" },
                            onFiles = { Toast.makeText(this, "Transferência será liberada após pareamento real.", Toast.LENGTH_SHORT).show() },
                            onMusic = { Toast.makeText(this, "Áudio remoto será liberado após pareamento real.", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    paired: Boolean, computerName: String, onPair: () -> Unit,
    onFiles: () -> Unit, onMusic: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("OVEROS  /  MOBILE", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Meu OverOS", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Seu computador. Sob seu controle.", color = Muted, fontSize = 15.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = if (paired) Color.Green else Color(0xFFFFB74D), fontSize = 18.sp)
                    Spacer(Modifier.width(9.dp))
                    Text(if (paired) "Conectado" else "Ainda não conectado", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Text(computerName, color = Muted)
                Button(onClick = onPair, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("  Conectar computador  ")
                }
            }
        }
        Text("CONTROLE DO COMPUTADOR", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("Monitor", "CPU • RAM • bateria", Modifier.weight(1f), "◉")
            FeatureCard("Arquivos", "Enviar e receber", Modifier.weight(1f), "⇄", onFiles)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("Música", "Áudio sem fio", Modifier.weight(1f), "♫", onMusic)
            FeatureCard("Comandos", "Ações autorizadas", Modifier.weight(1f), "⌘")
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1D31)), shape = RoundedCornerShape(16.dp)) {
            Text(
                "Versão inicial independente • 1.0\nOs recursos de controle serão ativados após a integração e validação do serviço no computador.",
                Modifier.padding(16.dp), color = Muted, fontSize = 12.sp, lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, modifier: Modifier, symbol: String, onClick: () -> Unit = {}) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(symbol, color = Blue, fontSize = 25.sp)
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PairScreen(
    qrValue: String, code: String, onCode: (String) -> Unit,
    onScan: () -> Unit, onBack: () -> Unit, onConfirm: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TextButton(onClick = onBack) { Text("← Voltar") }
        Text("Conectar ao OverOS", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("No computador, abra Configurações → Meu OverOS e mostre o QR de pareamento.", color = Muted)
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Escanear QR Code") }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("QR CODE", color = Blue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(if (qrValue.isBlank()) "Aguardando leitura" else "QR lido nesta sessão", color = Color.White)
            }
        }
        OutlinedTextField(
            value = code, onValueChange = { onCode(it.filter(Char::isDigit).take(6)) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Código de 6 dígitos") },
            singleLine = true
        )
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Validar pareamento")
        }
        Text("A validação final depende do serviço de pareamento instalado no computador. Este app não simula uma conexão.", color = Muted, fontSize = 12.sp)
    }
}
