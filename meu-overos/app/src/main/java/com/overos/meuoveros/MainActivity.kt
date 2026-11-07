package com.overos.meuoveros

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private val Background = Color(0xFF070B16)
private val SurfaceDark = Color(0xFF111A2B)
private val SurfaceRaised = Color(0xFF18243A)
private val Accent = Color(0xFF5B9DFF)
private val Accent2 = Color(0xFF7C6BFF)
private val TextMain = Color(0xFFF4F7FF)
private val TextMuted = Color(0xFF91A0B8)
private val Green = Color(0xFF4DD6A0)

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
            var paired by remember { mutableStateOf(false) }
            var overfixAsked by remember { mutableStateOf(false) }
            var overfixEnabled by remember { mutableStateOf(false) }
            var showOverfixDialog by remember { mutableStateOf(false) }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent, background = Background, surface = SurfaceDark,
                onBackground = TextMain, onSurface = TextMain
            )) {
                Surface(Modifier.fillMaxSize(), color = Background) {
                    when (screen) {
                        "pair" -> PairScreen(
                            qrValue = qrValue, code = code, onCode = { code = it },
                            onScan = {
                                val options = ScanOptions().setPrompt("Aponte para o QR exibido no computador")
                                    .setBeepEnabled(false).setOrientationLocked(false)
                                scanner.launch(options)
                            },
                            onBack = { screen = "home" },
                            onConfirm = {
                                if (qrValue.isBlank() || code.length != 6) {
                                    Toast.makeText(this, "Leia o QR e informe o código de 6 dígitos", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, "O QR foi lido, mas o serviço de pareamento do computador ainda precisa validar esta sessão.", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        else -> HomeScreen(
                            paired = paired,
                            overfixEnabled = overfixEnabled,
                            onPair = { screen = "pair" },
                            onFiles = { Toast.makeText(this, "Transferência de arquivos será ativada quando o serviço do OverOS estiver integrado.", Toast.LENGTH_SHORT).show() },
                            onMusic = { Toast.makeText(this, "Áudio sem fio será ativado quando o serviço do OverOS estiver integrado.", Toast.LENGTH_SHORT).show() },
                            onMonitor = { Toast.makeText(this, "Monitoramento será ativado após a conexão real.", Toast.LENGTH_SHORT).show() },
                            onCommands = { Toast.makeText(this, "Somente ações autorizadas estarão disponíveis após a integração.", Toast.LENGTH_SHORT).show() }
                        )
                    }
                    if (showOverfixDialog) {
                        AlertDialog(
                            onDismissRequest = { showOverfixDialog = false; overfixAsked = true },
                            containerColor = SurfaceRaised,
                            title = { Text("Ativar Overfix IA?", color = TextMain, fontWeight = FontWeight.Bold) },
                            text = { Text("O Overfix IA é opcional. Você poderá configurar sua chave da API do ChatGPT depois. O Meu OverOS funciona sem IA.", color = TextMuted) },
                            confirmButton = {
                                TextButton(onClick = { overfixEnabled = true; overfixAsked = true; showOverfixDialog = false }) {
                                    Text("Sim, configurar depois", color = Accent)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { overfixEnabled = false; overfixAsked = true; showOverfixDialog = false }) {
                                    Text("Agora não", color = TextMuted)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    paired: Boolean, overfixEnabled: Boolean, onPair: () -> Unit,
    onFiles: () -> Unit, onMusic: () -> Unit, onMonitor: () -> Unit, onCommands: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier.size(42.dp).background(Brush.linearGradient(listOf(Accent, Accent2)), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text("O", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f)) {
                Text("OVEROS", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text("CENTRAL DE CONTROLE", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.6.sp)
            }
            Box(Modifier.size(9.dp).background(if (paired) Green else Color(0xFFFFB45B), CircleShape))
            Text(if (paired) "ONLINE" else "OFFLINE", color = if (paired) Green else Color(0xFFFFB45B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tudo no seu", color = TextMuted, fontSize = 16.sp)
            Text("Meu OverOS.", color = TextMain, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Text("Seu computador, conectado ao seu celular.", color = TextMuted, fontSize = 14.sp)
        }

        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF183C70), Color(0xFF25265C), Color(0xFF171D39))),
                RoundedCornerShape(26.dp)
            ).padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SEU COMPUTADOR", color = Color(0xFFB9D5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(if (paired) "OverOS conectado" else "Nenhum dispositivo", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(if (paired) "Conexão ativa" else "Faça o pareamento para começar", color = Color(0xFFC0CBE0), fontSize = 12.sp)
                    }
                    Box(Modifier.size(58.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Text("⌘", color = Color.White, fontSize = 31.sp)
                    }
                }
                Button(
                    onClick = onPair,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF14264A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (paired) "Gerenciar conexão" else "＋  Conectar computador", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RECURSOS", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text("DO MEU OVEROS", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile("Monitor", "CPU, memória e bateria", "◉", Modifier.weight(1f), onMonitor, Color(0xFF5B9DFF))
            FeatureTile("Arquivos", "Envie e receba arquivos", "⇄", Modifier.weight(1f), onFiles, Color(0xFF9B83FF))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile("Música", "Áudio sem fio", "♫", Modifier.weight(1f), onMusic, Color(0xFF45CDA6))
            FeatureTile("Comandos", "Ações com segurança", "⌘", Modifier.weight(1f), onCommands, Color(0xFFFFB45B))
        }

        Row(
            Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✦", color = Accent2, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (overfixEnabled) "Overfix IA selecionado" else "Overfix IA é opcional", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(if (overfixEnabled) "A configuração da chave será feita na etapa de integração." else "A ativação será oferecida após o pareamento.", color = TextMuted, fontSize = 11.sp)
            }
        }
        Text("MEU OVEROS  •  VERSÃO 1.1", Modifier.fillMaxWidth(), color = Color(0xFF64738D), fontSize = 9.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FeatureTile(title: String, subtitle: String, symbol: String, modifier: Modifier, onClick: () -> Unit, tint: Color) {
    Card(
        onClick = onClick,
        modifier = modifier.height(142.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(21.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(39.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text(symbol, color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = TextMuted, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun PairScreen(
    qrValue: String, code: String, onCode: (String) -> Unit,
    onScan: () -> Unit, onBack: () -> Unit, onConfirm: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("←  Voltar", color = Accent) }
        Text("Conecte seu", color = TextMuted, fontSize = 17.sp)
        Text("computador.", color = TextMain, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
        Text("Abra as configurações do Meu OverOS no computador e inicie o pareamento.", color = TextMuted, fontSize = 14.sp, lineHeight = 21.sp)
        Box(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(24.dp)).padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(Modifier.size(66.dp).background(Color(0xFF1B3152), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Text("▦", color = Accent, fontSize = 36.sp)
                }
                Text("ETAPA 1  •  LER QR CODE", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(if (qrValue.isBlank()) "Aguardando leitura" else "QR lido nesta sessão", color = TextMain, fontWeight = FontWeight.SemiBold)
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                    Text("Abrir câmera e escanear", fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("ETAPA 2  •  CÓDIGO DE SEGURANÇA", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            OutlinedTextField(
                value = code, onValueChange = { onCode(it.filter(Char::isDigit).take(6)) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Código de 6 dígitos") },
                singleLine = true, shape = RoundedCornerShape(14.dp)
            )
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) {
                Text("Confirmar pareamento", fontWeight = FontWeight.Bold)
            }
        }
        Text("A conexão só será concluída quando o serviço de pareamento do computador validar o QR e o código. Este aplicativo não inventa uma conexão.", color = TextMuted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}
