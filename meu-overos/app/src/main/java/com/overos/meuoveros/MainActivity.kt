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
    var tab by remember { mutableStateOf("Início") }
    val tabs = listOf("Início", "Recursos", "Atividade", "Ajustes")
    Column(Modifier.fillMaxSize().background(Background)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(Brush.linearGradient(listOf(Accent, Accent2)), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("O", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("MEU OVEROS", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    Text("CONTROLE DO COMPUTADOR", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.1.sp)
                }
                Box(Modifier.size(9.dp).background(if (paired) Green else Color(0xFFFFB45B), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(if (paired) "CONECTADO" else "DESCONECTADO", color = if (paired) Green else Color(0xFFFFB45B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            when (tab) {
                "Início" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Seu computador.", color = TextMuted, fontSize = 15.sp)
                        Text("Tudo conectado.", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
                        Text("Controle seu OverOS direto do celular.", color = TextMuted, fontSize = 13.sp)
                    }
                    Box(
                        Modifier.fillMaxWidth().background(
                            Brush.linearGradient(listOf(Color(0xFF244F91), Color(0xFF34306F), Color(0xFF171F3B))),
                            RoundedCornerShape(26.dp)
                        ).padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("MEU COMPUTADOR", color = Color(0xFFC5D9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                                    Text(if (paired) "OverOS online" else "Nenhum PC vinculado", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                    Text(if (paired) "Dispositivo autenticado" else "Vincule seu computador para começar", color = Color(0xFFD0D9EC), fontSize = 12.sp)
                                }
                                Box(Modifier.size(62.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                    Text("▣", color = Color.White, fontSize = 34.sp)
                                }
                            }
                            Button(
                                onClick = onPair, modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF172A4C)),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text(if (paired) "Gerenciar dispositivo" else "＋  Vincular computador", fontWeight = FontWeight.Bold) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ACESSO RÁPIDO", color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Text("4 FERRAMENTAS", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        FeatureTile("Monitor", "Desempenho do PC", "◉", Modifier.weight(1f), onMonitor, Color(0xFF65A8FF))
                        FeatureTile("Arquivos", "Enviar e receber", "⇄", Modifier.weight(1f), onFiles, Color(0xFFAB91FF))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        FeatureTile("Música", "Áudio sem fio", "♫", Modifier.weight(1f), onMusic, Color(0xFF52D8B0))
                        FeatureTile("Comandos", "Ações rápidas", "⌘", Modifier.weight(1f), onCommands, Color(0xFFFFC16B))
                    }
                    Row(
                        Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(20.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(42.dp).background(Color(0xFF30275A), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Text("✦", color = Color(0xFFB5A2FF), fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Overfix IA", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (overfixEnabled) "Ativação escolhida; aguardando configuração segura." else "Assistente opcional para diagnóstico e correções.", color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Text("›", color = Accent, fontSize = 25.sp)
                    }
                }
                "Recursos" -> {
                    Text("Recursos", color = TextMain, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Ferramentas para usar com seu computador.", color = TextMuted, fontSize = 13.sp)
                    FeatureTile("Monitor", "CPU, memória, bateria e armazenamento", "◉", Modifier.fillMaxWidth(), onMonitor, Color(0xFF65A8FF))
                    FeatureTile("Arquivos", "Transferência entre celular e PC", "⇄", Modifier.fillMaxWidth(), onFiles, Color(0xFFAB91FF))
                    FeatureTile("Música", "Controle de áudio sem fio", "♫", Modifier.fillMaxWidth(), onMusic, Color(0xFF52D8B0))
                    FeatureTile("Comandos", "Atalhos permitidos e confirmados", "⌘", Modifier.fillMaxWidth(), onCommands, Color(0xFFFFC16B))
                }
                "Atividade" -> {
                    Text("Atividade", color = TextMain, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Alertas e ações recentes do computador.", color = TextMuted, fontSize = 13.sp)
                    EmptyState("Tudo tranquilo por aqui", "Quando o computador estiver conectado, os eventos e alertas aparecerão nesta área.")
                }
                else -> {
                    Text("Configurações", color = TextMain, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Preferências do Meu OverOS.", color = TextMuted, fontSize = 13.sp)
                    SettingRow("Overfix IA", if (overfixEnabled) "Ativação selecionada" else "Opcional • após o pareamento", "✦")
                    SettingRow("Dispositivos", "Gerenciar computadores vinculados", "▣")
                    SettingRow("Privacidade e segurança", "Credenciais e permissões", "⌑")
                    Text("A configuração da chave da API e a execução automática de reparos só serão habilitadas com a integração segura do serviço.", color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Text("MEU OVEROS  •  CENTRAL DE CONTROLE", Modifier.fillMaxWidth(), color = Color(0xFF64738D), fontSize = 9.sp, letterSpacing = 1.2.sp)
        }
        Row(
            Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.forEach { item ->
                TextButton(onClick = { tab = item }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            when (item) { "Início" -> "⌂"; "Recursos" -> "▦"; "Atividade" -> "◷"; else -> "⚙" },
                            color = if (tab == item) Accent else TextMuted, fontSize = 19.sp
                        )
                        Text(item, color = if (tab == item) TextMain else TextMuted, fontSize = 9.sp, fontWeight = if (tab == item) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(22.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("◷", color = Accent, fontSize = 36.sp)
        Text(title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, symbol: String) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(SurfaceRaised, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Text(symbol, color = Accent, fontSize = 21.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Text("›", color = TextMuted, fontSize = 23.sp)
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
