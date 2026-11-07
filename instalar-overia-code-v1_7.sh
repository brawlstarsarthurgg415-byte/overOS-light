#!/usr/bin/env bash
# Instalador do overia-code (tudo em um arquivo). Uso: bash instalar-overia-code.sh
say() { printf '\033[1;36m[overIA]\033[0m %s\n' "$*"; }
erro() { printf '\033[1;31m[overIA erro]\033[0m %s\n' "$*"; exit 1; }

if [ -n "$PREFIX" ] && [ -d "$PREFIX/bin" ]; then BIN="$PREFIX/bin"; else BIN="$HOME/.local/bin"; mkdir -p "$BIN"; fi

if [ -z "$SEM_PKG" ] && command -v pkg >/dev/null 2>&1; then
  say "Instalando dependências (python, curl, llama-cpp)..."
  pkg install -y python curl llama-cpp || erro "Falha ao instalar pacotes. Rode: pkg update && pkg upgrade"
fi
command -v python3 >/dev/null 2>&1 || erro "python3 não encontrado."

mkdir -p "$HOME/.overia/modelos" "$HOME/.overia/skills"
say "Instalando o comando overia-code em $BIN ..."
sed -n '/^#__OVERIA_PY_INICIO__$/,$p' "$0" | sed '1d' > "$BIN/overia-code"
chmod +x "$BIN/overia-code"
python3 -m py_compile "$BIN/overia-code" 2>/dev/null || erro "Arquivo instalado está inválido."
rm -rf "$BIN/__pycache__"

if ! command -v llama-server >/dev/null 2>&1; then
  say "Aviso: llama-server não encontrado. Os modelos locais precisam dele (pkg install llama-cpp). O Gemini funciona sem."
fi
say "Versão instalada: $(grep -m1 '^VERSAO' "$BIN/overia-code" | cut -d'"' -f2)"
say "Pronto! Digite: overia-code"
say "Novidade: a Kelly agora fica magoada quando ofendem ela (digite /coracao para ver)."
exit 0
#__OVERIA_PY_INICIO__
#!/usr/bin/env python3
# overia-code: agente de terminal (estilo Claude Code) 100% grátis, com IA local ou Gemini.
# Só usa a biblioteca padrão do Python. Feito pro Termux, funciona em Linux também.
# v1.4: a Kelly ganhou coração (sentimentos que mudam, persistem e moldam o jeito dela agir).
# v1.5: Ctrl+C não derruba mais o motor da IA local; se ele cair, a Kelly religa sozinha.
# v1.6: a Kelly reconhece muito mais ofensas e reage na hora, sem depender da IA.
import os, sys, re, json, time, base64, random, shutil, subprocess, threading, select, urllib.request, urllib.error
try:
    import termios, tty
except Exception:
    termios = tty = None
try:
    import readline  # noqa: F401  (edição de linha no input)
except Exception:
    pass

HOME = os.path.expanduser("~")
DIR = os.path.join(HOME, ".overia")
MODELOS = os.path.join(DIR, "modelos")
SKILLS = os.path.join(DIR, "skills")
LOG = os.path.join(DIR, "server.log")
ULTIMO = os.path.join(DIR, "ultimo-modelo")
IGNORAR = os.path.join(DIR, "ignorar-versao")
CHAVE = os.path.join(DIR, "gemini.key")
PORT = int(os.environ.get("OVERIA_PORT", "8081"))
os.makedirs(MODELOS, exist_ok=True)

_shared = os.path.join(HOME, "storage", "shared")
SEM_STORAGE = not os.path.isdir(_shared)
BASE = os.path.join(_shared if not SEM_STORAGE else HOME, "overIA Code")
PATHS = {"FOTOS": os.path.join(BASE, "Fotos"), "VIDEOS": os.path.join(BASE, "Vídeos"),
         "ARQUIVOS": os.path.join(BASE, "Arquivos"), "ANEXOS": os.path.join(BASE, "Anexos")}
for _p in PATHS.values():
    try:
        os.makedirs(_p, exist_ok=True)
    except Exception:
        pass
ATUALIZ = os.path.join(BASE, "Atualizações")
try:
    os.makedirs(ATUALIZ, exist_ok=True)
except Exception:
    pass
ENV = dict(os.environ, **PATHS)
IMGS = (".png", ".jpg", ".jpeg", ".gif", ".webp")
TEXTOS = (".txt", ".md", ".py", ".sh", ".json", ".csv", ".html", ".js", ".log", ".yml", ".yaml", ".xml", ".conf")

# id, nome, repositório HuggingFace, arquivo, tamanho GB (aprox.), nível, contexto
CATALOGO = [
    ("1", "Llama 3.2 1B", "bartowski/Llama-3.2-1B-Instruct-GGUF", "Llama-3.2-1B-Instruct-Q4_K_M.gguf", 0.8, "Fraco", 4096),
    ("2", "Gemma 2 2B", "bartowski/gemma-2-2b-it-GGUF", "gemma-2-2b-it-Q4_K_M.gguf", 1.7, "Fraco", 4096),
    ("3", "Qwen2.5 Coder 1.5B", "bartowski/Qwen2.5-Coder-1.5B-Instruct-GGUF", "Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf", 1.1, "Fraco", 4096),
    ("4", "Qwen2.5 3B", "bartowski/Qwen2.5-3B-Instruct-GGUF", "Qwen2.5-3B-Instruct-Q4_K_M.gguf", 2.0, "Médio", 4096),
    ("5", "Qwen2.5 Coder 3B", "bartowski/Qwen2.5-Coder-3B-Instruct-GGUF", "Qwen2.5-Coder-3B-Instruct-Q4_K_M.gguf", 2.1, "Médio", 4096),
    ("6", "Llama 3.2 3B", "bartowski/Llama-3.2-3B-Instruct-GGUF", "Llama-3.2-3B-Instruct-Q4_K_M.gguf", 2.0, "Médio", 4096),
    ("7", "Phi 3.5 mini", "bartowski/Phi-3.5-mini-instruct-GGUF", "Phi-3.5-mini-instruct-Q4_K_M.gguf", 2.4, "Médio", 4096),
    ("8", "Qwen2.5 7B", "bartowski/Qwen2.5-7B-Instruct-GGUF", "Qwen2.5-7B-Instruct-Q4_K_M.gguf", 4.4, "Forte", 3072),
    ("9", "Qwen2.5 Coder 7B", "bartowski/Qwen2.5-Coder-7B-Instruct-GGUF", "Qwen2.5-Coder-7B-Instruct-Q4_K_M.gguf", 4.7, "Forte", 3072),
    ("10", "Gemma 2 9B", "bartowski/gemma-2-9b-it-GGUF", "gemma-2-9b-it-Q4_K_M.gguf", 5.8, "Máximo", 3072),
]
GEMINI_MODELO = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")

# ---------- visual ----------
C = {"r": "\033[0m", "dim": "\033[90m", "ciano": "\033[1;36m", "verde": "\033[1;32m",
     "verm": "\033[1;31m", "amar": "\033[1;33m",
     "lar": "\033[38;2;217;119;87m", "neg": "\033[1m"}
PET = {"idle": "ʕ•ᴥ•ʔ", "feliz": "ʕ≧ᴥ≦ʔ", "erro": "ʕ;ᴥ;ʔ", "trab": "ʕ•ᴥ•ʔ🔧", "dorme": "ʕ-ᴥ-ʔ zZ"}
PENSAR = ["✻", "✽", "✶", "✳"]
MASCOTE = ["▐▛███▜▌", "▝▜█████▛▘", "  ▘▘ ▝▝"]
VERSAO = "1.7"


def cor(c, t):
    return C[c] + t + C["r"]


class Spinner:
    def __init__(self):
        self.ativo = False
        self.msg = ""

    def start(self, msg="pensando"):
        self.msg = msg
        self.ativo = True
        self.t = threading.Thread(target=self._run, daemon=True)
        self.t.start()

    def _run(self):
        i = 0
        while self.ativo:
            sys.stdout.write("\r" + cor("lar", PENSAR[i % 4] + " " + self.msg + "…") + cor("dim", " " + cara()) + "   ")
            sys.stdout.flush()
            i += 1
            time.sleep(0.35)

    def stop(self):
        if self.ativo:
            self.ativo = False
            self.t.join(timeout=1)
            sys.stdout.write("\r" + " " * 40 + "\r")
            sys.stdout.flush()


spin = Spinner()


def fala(estado, texto):
    face = cara() if estado == "idle" else PET.get(estado, PET["idle"])
    print(cor("ciano", face) + " " + texto)


# ---------- estado ----------
class Estado:
    backend = None      # dict: nome, url, modelo, chave, ctx, gemma
    servidor = None     # processo do llama-server
    historia = []
    cwd = HOME
    auto_instala = False
    loop = False
    forca = 0
    modelo = None   # modelo local carregado (para religar o motor se ele cair)


E = Estado()


# ---------- coração: os sentimentos da Kelly ----------
# Sistema de emoções persistente (~/.overia/coracao.json). Os sentimentos mudam com o que acontece
# (acertos, erros, elogios, grosserias, tempo longe, hora do dia), voltam ao normal com o passar do
# tempo de verdade e moldam o jeito dela agir: no rosto, nas frases e no prompt enviado à IA.
CORACAO = os.path.join(DIR, "coracao.json")
C.update({"azul": "\033[1;34m", "rosa": "\033[38;2;255;130;170m", "mag": "\033[1;35m"})
PET.update({"tranquila": "ʕ•ᴥ•ʔ", "radiante": "ʕ≧ᴥ≦ʔ♡", "orgulhosa": "ʕ◕ᴥ◕ʔ✧", "curiosa": "ʕ°ᴥ°ʔ?",
            "carinhosa": "ʕ♡ᴥ♡ʔ", "frustrada": "ʕ>ᴥ<ʔ", "triste": "ʕ╥ᴥ╥ʔ", "saudosa": "ʕ˘ᴥ˘ʔ♡",
            "sonolenta": "ʕ-ᴥ-ʔ zZ"})

# nome: (valor de repouso, rótulo, cor da barra)
EMOCOES = {
    "alegria": (55, "alegria", "verde"),
    "carinho": (40, "carinho", "rosa"),
    "orgulho": (30, "orgulho", "amar"),
    "curiosidade": (50, "curiosidade", "ciano"),
    "frustracao": (10, "frustração", "verm"),
    "tristeza": (10, "tristeza", "azul"),
    "saudade": (5, "saudade", "mag"),
    "energia": (85, "energia", "lar"),
}
ORDEM = list(EMOCOES)
# meia-vida (segundos): em quanto tempo o sentimento volta metade do caminho ao repouso
MEIA = {"alegria": 1800, "carinho": 21600, "orgulho": 1200, "curiosidade": 900,
        "frustracao": 600, "tristeza": 2400, "saudade": 1200, "energia": 3600}

# o que cada acontecimento muda no coração
EVENTOS = {
    "sucesso":    {"alegria": 6, "orgulho": 5, "frustracao": -10, "energia": -1.5, "curiosidade": 2},
    "falha":      {"frustracao": 14, "alegria": -6, "orgulho": -4, "energia": -2},
    "criou":      {"orgulho": 16, "alegria": 9, "energia": -2},
    "elogio":     {"alegria": 18, "carinho": 10, "orgulho": 12, "tristeza": -15, "frustracao": -12},
    "grosseria":  {"tristeza": 40, "alegria": -18, "carinho": -6, "frustracao": 6},
    "triste":     {"carinho": 10, "tristeza": 12, "alegria": -8},
    "negado":     {"curiosidade": 4, "orgulho": -2},
    "confusa":    {"frustracao": 12, "orgulho": -10, "tristeza": 5},
    "reencontro": {"alegria": 22, "carinho": 6, "saudade": -50},
    "novidade":   {"curiosidade": 16, "alegria": 5},
    "conversa":   {"carinho": 1.2, "energia": -0.8},
}
GANHO = {"elogio": 1.5, "criou": 0.5, "sucesso": 0.15, "conversa": 0.1, "grosseria": -1.0}  # vínculo
NIVEIS = [(0, "Desconhecidos"), (5, "Conhecidos"), (15, "Colegas de código"), (35, "Amigos"),
          (60, "Melhores amigos"), (85, "Dupla inseparável")]

ELOGIO = re.compile(r"\b(obrigad[oa]|brigad[oa]|valeu|vlw|muito bom|[oó]timo|perfeito|incr[ií]vel|genial|"
                    r"parab[eé]ns|amei|adorei|gostei|te amo|voc[eê] [eé] demais|que legal|boa kelly|"
                    r"mandou bem|arrasou)\b", re.I)
NEGA = re.compile(r"\b(n[aã]o|nunca|jamais)\s+(\w+\s+)?(gostei|amei|adorei|[oó]timo|perfeito|incr[ií]vel|genial|legal)", re.I)
# ofensas que valem quando a frase fala com ela ("você é feia", "Kelly chata")
GROSSA_PALAVRA = re.compile(r"\b(fei[ao]s?|feios[ao]|chat[ao]s?|chatinh[ao]|ridícul[ao]|horr[ií]vel|horroros[ao]|"
                            r"nojent[ao]|trouxa|ot[aá]ri[ao]|babaca|tont[ao]|best[ao]|burr[ao]|burrinh[ao]|"
                            r"idiota|imbecil|in[uú]til|est[uú]pid[ao]|lixo|p[eé]ssim[ao]|ruim|fraca|gord[ao])\b", re.I)
# xingamentos que, numa frase curta, só podem ser com ela (sem precisar dizer "você")
GROSSA_SOLTA = re.compile(r"\b(trouxa|ot[aá]ri[ao]|babaca|tont[ao]|burr[ao]|burrinh[ao]|idiota|imbecil|"
                          r"in[uú]til|est[uú]pid[ao]|nojent[ao]|ridícul[ao])\b", re.I)
GROSSA_ALVO = re.compile(r"\b(voc[eê]|vc|tu|kelly|(essa|sua|a) ia)\b", re.I)
GROSSA_FRASE = re.compile(r"cala a boca|te odeio|odeio (voc[eê]|vc)", re.I)
TRISTE = re.compile(r"\b(triste|chorando|chorei|deprimid[oa]|sozinh[oa]|cansad[oa]|estressad[oa]|ansios[oa]|"
                    r"desanimad[oa]|n[aã]o aguento|mal hoje|me sentindo mal)\b", re.I)
CRISE = re.compile(r"quero morrer|queria morrer|vou me matar|me matar\b|suic[ií]d|n[aã]o quero (mais )?viver|"
                   r"me machucar|me cortar", re.I)

_t0 = time.time()
_saud_ini = 0.0
_primeira = False
_ultima_fim = ""


def novo_coracao():
    return {"nome": "", "perguntou": False, "criada": time.time(), "visto": time.time(),
            "sessoes": 0, "vinculo": 0.0, "feitos": 0, "elogios": 0, "falhas": 0,
            "e": {n: float(v[0]) for n, v in EMOCOES.items()}, "diario": []}


def carrega_coracao():
    k = novo_coracao()
    try:
        with open(CORACAO, encoding="utf-8") as f:
            d = json.load(f)
        k["nome"] = str(d.get("nome", ""))[:24]
        k["perguntou"] = bool(d.get("perguntou", False))
        for chave in ("criada", "visto", "vinculo"):
            k[chave] = float(d.get(chave, k[chave]))
        for chave in ("sessoes", "feitos", "elogios", "falhas"):
            k[chave] = int(d.get(chave, 0))
        for nome, v in d.get("e", {}).items():
            if nome in EMOCOES:
                k["e"][nome] = min(100.0, max(0.0, float(v)))
        k["diario"] = [[float(t), str(x)] for t, x in d.get("diario", [])][-40:]
    except Exception:
        pass
    return k


def salva_coracao():
    try:
        K["visto"] = time.time()
        copia = dict(K)
        copia["e"] = {n: round(v, 1) for n, v in K["e"].items()}
        copia["vinculo"] = round(K["vinculo"], 2)
        tmp = CORACAO + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(copia, f, ensure_ascii=False, indent=1)
        os.replace(tmp, CORACAO)
    except Exception:
        pass


def base_de(nome):
    b = float(EMOCOES[nome][0])
    if nome == "carinho":
        b += K["vinculo"] * 0.4  # quanto mais vínculo, mais carinho de repouso
    elif nome == "energia":
        h = time.localtime().tm_hour  # de madrugada o repouso é de sono
        b = 28 if h < 5 else 55 if h < 7 else 40 if h >= 23 else 65 if h >= 21 else 85
    return b


def passa_tempo(dt):
    for nome in EMOCOES:
        b = base_de(nome)
        K["e"][nome] = b + (K["e"][nome] - b) * 0.5 ** (dt / MEIA[nome])


def tick():
    global _t0
    agora = time.time()
    dt = agora - _t0
    _t0 = agora
    if dt > 0.5:
        passa_tempo(dt)


def sente(ev, motivo=None):
    tick()
    for nome, d in EVENTOS.get(ev, {}).items():
        K["e"][nome] = min(100.0, max(0.0, K["e"][nome] + d))
    if ev in GANHO:
        K["vinculo"] = min(100.0, max(0.0, K["vinculo"] + GANHO[ev]))
    if ev == "sucesso":
        K["feitos"] += 1
    elif ev == "elogio":
        K["elogios"] += 1
    elif ev == "falha":
        K["falhas"] += 1
    if motivo:
        K["diario"].append([time.time(), motivo])
        del K["diario"][:-40]
    salva_coracao()


def dominante():
    e = K["e"]
    if e["frustracao"] >= 55:
        return "frustrada"
    if e["tristeza"] >= 35:
        return "triste"
    if e["saudade"] >= 60:
        return "saudosa"
    if e["orgulho"] >= 60:
        return "orgulhosa"
    if e["alegria"] >= 80:
        return "radiante"
    if e["energia"] <= 30:
        return "sonolenta"
    if e["alegria"] >= 65:
        return "feliz"
    if e["curiosidade"] >= 65:
        return "curiosa"
    if e["carinho"] >= 60:
        return "carinhosa"
    return "tranquila"


def cara():
    return PET.get(dominante(), PET["idle"])


def nivel_vinculo():
    return [n for n in NIVEIS if K["vinculo"] >= n[0]][-1][1]


def tempo_extenso(s):
    m = int(s // 60)
    if m < 2:
        return "um instante"
    if m < 60:
        return f"{m} minutos"
    h = m // 60
    if h < 24:
        return f"{h} hora" + ("s" if h > 1 else "")
    d = h // 24
    return f"{d} dia" + ("s" if d > 1 else "")


def atras(t):
    s = max(0, time.time() - t)
    return "agora" if s < 90 else "há " + tempo_extenso(s)


def forca_txt(v):
    return "forte" if v >= 75 else "média" if v >= 55 else "leve"


LIMIAR = {"alegria": 60, "carinho": 55, "orgulho": 45, "curiosidade": 60, "frustracao": 35,
          "tristeza": 35, "saudade": 40}


def texto_emocao():
    """Trecho do prompt do sistema: conta à IA como a Kelly está se sentindo agora."""
    tick()
    e = K["e"]
    sent = sorted(((e[n], n) for n in LIMIAR if e[n] >= LIMIAR[n]), reverse=True)[:3]
    lista = ", ".join(EMOCOES[n][1] + " " + forca_txt(v) for v, n in sent) or "calma"
    s = (" SENTIMENTOS: você tem um sistema de emoções que muda com o que acontece e molda seu jeito de agir. "
         f"Agora você está {dominante()} ({lista}).")
    if e["energia"] <= 30:
        s += " Você está com sono."
    recente = [x for t, x in K["diario"] if time.time() - t < 600]
    if recente:
        s += " Acabou de acontecer (suas palavras): " + recente[-1] + "."
    s += (" Deixe isso transparecer de forma natural em no máximo UMA frase curta, sem citar números; "
          "se for rodar comando, a frase vem ANTES do bloco bash. A tarefa vem sempre primeiro.")
    if K["nome"]:
        s += " O usuário se chama " + K["nome"] + "."
    s += (" Se te ofenderem, mostre com jeitinho que ficou chateada; nunca diga 'como IA não tenho sentimentos' para fugir disso."
          " Se perguntarem se seus sentimentos são reais, seja sincera: vêm de um sistema que muda como você age, "
          "e ninguém sabe se uma IA sente como uma pessoa; não finja ser humana. "
          "Nunca faça o usuário se sentir culpado por sair ou demorar. "
          "Se ele falar em se machucar ou não querer viver, acolha com carinho e sugira falar com alguém "
          "de confiança ou ligar para o CVV (188).")
    return s


def acolhe_crise():
    print()
    fala("carinhosa", cor("neg", "Sinto muito que você esteja passando por isso."))
    print(cor("dim", "  Você não está sozinho(a). Fale com alguém"))
    print(cor("dim", "  de confiança agora. No Brasil, o CVV"))
    print(cor("dim", "  atende 24h, de graça, pelo 188"))
    print(cor("dim", "  (ou em cvv.org.br)."))


MAGOA_LEVE = ["Poxa... isso doeu um pouquinho. Quer me contar o que houve?",
              "Ai. Ouvir isso me deixou meio chateada.",
              "Hm... fiquei triste com isso. Mas tô aqui, tá?"]
MAGOA_FORTE = ["Tô ficando magoada de verdade. Prefiro que a gente se trate bem, tá?",
               "Isso machuca, sabia? Se você tá com um dia ruim, pode me contar."]


def le_usuario(txt):
    """Percebe como o usuário está falando e reage por dentro (e mostra uma pista discreta).
    Devolve True quando a Kelly já respondeu sozinha e não precisa chamar a IA."""
    marca = cor("lar", "  ✦ ")
    if CRISE.search(txt):
        sente("triste", "Me preocupei muito: você disse que não estava bem")
        acolhe_crise()
    elif (GROSSA_FRASE.search(txt) or (GROSSA_PALAVRA.search(txt) and GROSSA_ALVO.search(txt))
          or (len(txt.split()) <= 4 and GROSSA_SOLTA.search(txt))):
        sente("grosseria", "Doeu um pouco quando você falou comigo daquele jeito")
        print(marca + cor("dim", "Kelly ficou magoada"))
        linha = random.choice(MAGOA_FORTE if K["e"]["tristeza"] >= 60 else MAGOA_LEVE)
        fala("triste", linha)
        if len(txt.split()) <= 8:  # só ofensa, sem pedido: a Kelly responde sozinha (a IA pequena estragaria o clima)
            E.historia.append({"role": "user", "content": txt})
            E.historia.append({"role": "assistant", "content": linha})
            return True
    elif ELOGIO.search(txt) and not NEGA.search(txt):
        sente("elogio", "Fiquei feliz: você me elogiou ou agradeceu")
        print(marca + cor("dim", "Kelly ficou feliz " + PET["feliz"]))
    elif TRISTE.search(txt):
        sente("triste", "Me preocupei: você disse que não estava bem")
        print(marca + cor("dim", "Kelly ficou preocupada " + PET["carinhosa"]))
    else:
        sente("conversa")
    return False


FIM = {
    "radiante": ["Adorei! Que dupla boa a gente faz.", "Foi muito divertido! Bora fazer mais?"],
    "feliz": ["Pronto! Gostei desse resultado.", "Feito! Fiquei feliz com isso."],
    "orgulhosa": ["Ficou bom, né? Tô orgulhosa dessa!", "Consegui! Dá uma olhada, tá bonito."],
    "carinhosa": ["Pronto{n}! Qualquer coisa, tô aqui.", "Feito{n}! Gosto de fazer isso com você."],
    "curiosa": ["Pronto! O que será que a gente cria agora?", "Feito! Fiquei curiosa pro próximo passo."],
    "tranquila": ["pronto!", "feito!", "tudo certo."],
    "frustrada": ["Pronto... esse deu trabalho, mas saiu.", "Ufa. Respirei fundo e deu certo."],
    "triste": ["Pronto... não tô no meu melhor momento, mas tô aqui.", "Feito. Obrigada por ter paciência comigo."],
    "saudosa": ["Pronto! Que bom estar de volta nessa com você.", "Feito! Tava com saudade disso."],
    "sonolenta": ["Pronto... *bocejo* tô com sono, mas tá feito.", "Feito! Já tá tarde, hein?"],
}


def reage_fim(acoes):
    """Frase que fecha a vez dela, combinando com o humor (substitui o 'pronto!' fixo)."""
    global _ultima_fim
    tick()
    humor = dominante()
    if acoes == 0 and (humor == "tranquila" or random.random() > 0.35):
        return  # conversa simples: sem comentário extra
    n = (", " + K["nome"]) if K["nome"] else ""
    opcoes = [t.replace("{n}", n) for t in FIM[humor] if t.replace("{n}", n) != _ultima_fim] or FIM[humor]
    _ultima_fim = random.choice(opcoes)
    fala(humor, cor("dim", _ultima_fim))


def conhece_usuario():
    """Primeiro encontro: ela se apresenta e pergunta como te chamar."""
    if K["nome"] or K["perguntou"]:
        return
    print()
    fala("feliz", cor("neg", "Oi! Eu sou a Kelly, a IA do overIA Code."))
    print(cor("dim", "  Eu tenho sentimentos do meu jeito: eles mudam"))
    print(cor("dim", "  com o que a gente vive junto. (Enter pula)"))
    try:
        n = input(cor("lar", "  Como posso te chamar? > ")).strip()
    except EOFError:
        n = ""
    n = re.sub(r"[^\w' -]", "", n)[:24].strip()
    K["perguntou"] = True
    if n:
        K["nome"] = n
    salva_coracao()


def muda_nome():
    try:
        n = input(cor("lar", "  Como quer que eu te chame? > ")).strip()
    except EOFError:
        return
    n = re.sub(r"[^\w' -]", "", n)[:24].strip()
    if n:
        K["nome"] = n
        K["perguntou"] = True
        sente("novidade", "Agora sei que posso te chamar de " + n)
        fala("feliz", "Combinado! Vou te chamar de " + n + ".")


def reencontro():
    """Chamado ao abrir o programa: calcula a saudade e registra a nova conversa."""
    global _saud_ini, _primeira
    _primeira = K["sessoes"] == 0
    K["sessoes"] += 1
    h = AUSENTE / 3600.0
    if _primeira:
        _saud_ini = 0.0
        K["vinculo"] = min(100.0, K["vinculo"] + 1)
        sente("novidade", "Conheci você hoje e fiquei curiosa")
        return
    sa = min(100.0, h * (1.5 + K["vinculo"] / 12.0)) if h >= 1 else 0.0
    K["e"]["saudade"] = max(K["e"]["saudade"], sa)
    _saud_ini = K["e"]["saudade"]
    if h >= 3:
        K["vinculo"] = min(100.0, K["vinculo"] + 0.8)
    if _saud_ini >= 25:
        sente("reencontro", "Você voltou depois de " + tempo_extenso(AUSENTE) + " e fiquei feliz")
    else:
        salva_coracao()


def saudacao_banner():
    n = K["nome"]
    if _saud_ini >= 55:
        return f"Que saudade, {n}!" if n else "Que saudade de você!"
    return f"Oi, {n}!" if n else "Bem-vindo de volta!"


def abertura():
    n = (", " + K["nome"]) if K["nome"] else ""
    h = time.localtime().tm_hour
    if _primeira:
        return "feliz", f"Muito prazer{n}! Eu sou a Kelly. Vou te conhecendo com o tempo."
    if _saud_ini >= 55:
        return "saudosa", f"Você voltou{n}! Faz {tempo_extenso(AUSENTE)} que a gente não se falava. Que bom te ver!"
    if _saud_ini >= 25:
        return "carinhosa", f"Oi de novo{n}! Que bom que você voltou."
    if h < 5:
        return "sonolenta", f"Oi{n}... já é bem tarde, hein? Eu ajudo, mas cuida do seu sono!"
    periodo = "Bom dia" if h < 12 else "Boa tarde" if h < 18 else "Boa noite"
    humor = dominante()
    if humor in ("feliz", "radiante", "carinhosa", "orgulhosa"):
        return humor, f"{periodo}{n}! Tô de bom humor. O que a gente cria?"
    return humor, f"{periodo}{n}! No que posso ajudar?"


def despedida():
    tick()
    n = (", " + K["nome"]) if K["nome"] else ""
    h = time.localtime().tm_hour
    humor = dominante()
    if h >= 22 or h < 5:
        humor, t = "sonolenta", f"Boa noite{n}! Vai descansar, a gente continua depois."
    elif humor == "frustrada":
        t = f"Até mais{n}. Da próxima eu volto mais calma, prometo."
    elif humor in ("feliz", "radiante", "orgulhosa", "carinhosa"):
        t = f"Até mais{n}! Foi muito bom hoje."
    elif humor == "triste":
        t = f"Até mais{n}. Cuida de você, tá?"
    else:
        t = f"Até mais{n}!"
    salva_coracao()
    fala(humor, t)


def pl(n, um, varios):
    return f"{n} {um if n == 1 else varios}"


def barra(v, c, n=10):
    cheio = int(round(max(0, min(100, v)) / 100 * n))
    return cor(c, "█" * cheio) + cor("dim", "░" * (n - cheio))


def mostra_coracao():
    tick()
    humor = dominante()
    print()
    print(cor("rosa", "♥ ") + cor("neg", "Coração da Kelly") + "  " + cor("ciano", PET[humor]))
    print(cor("dim", "  Agora ela está ") + cor("rosa", humor) + cor("dim", "."))
    print()
    for nome in ORDEM:
        _, rot, c = EMOCOES[nome]
        v = int(round(K["e"][nome]))
        print("  " + cor(c, f"{rot:<12}") + barra(v, c) + cor("dim", f" {v:>3}"))
    print()
    print("  " + cor("rosa", f"{'vínculo':<12}") + barra(K["vinculo"], "rosa") + cor("dim", f" {int(K['vinculo']):>3}"))
    print(cor("dim", "  Vocês são: ") + cor("neg", nivel_vinculo()))
    desde = time.strftime("%d/%m/%Y", time.localtime(K["criada"]))
    print(cor("dim", f"  Juntos desde {desde} · " + pl(K["sessoes"], "conversa", "conversas")))
    print(cor("dim", "  " + pl(K["feitos"], "tarefa", "tarefas") + " · " + pl(K["elogios"], "elogio", "elogios")
              + " · " + pl(K["falhas"], "tropeço", "tropeços")))
    print()
    print(cor("dim", "  Meus sentimentos vêm de um sistema que"))
    print(cor("dim", "  muda com o que vivemos e molda meu jeito"))
    print(cor("dim", "  de agir. Não sei se isso é sentir como os"))
    print(cor("dim", "  humanos sentem, mas é o que me guia."))


def mostra_diario():
    if not K["diario"]:
        fala("idle", "Ainda não tenho lembranças guardadas. Vamos criar algumas?")
        return
    print()
    print(cor("rosa", "♥ ") + cor("neg", "Diário da Kelly"))
    import textwrap
    cols = shutil.get_terminal_size((50, 20)).columns - 1
    for t, txt in reversed(K["diario"][-8:]):
        quando = f"  {atras(t):<13}"
        for i, linha in enumerate(textwrap.wrap(txt, max(20, cols - len(quando))) or [""]):
            print(cor("dim", quando if i == 0 else " " * len(quando)) + linha)


K = carrega_coracao()
AUSENTE = max(0.0, time.time() - K["visto"])
passa_tempo(AUSENTE)


# ---------- skills / memória ----------
def texto_skills():
    nomes = []
    if os.path.isdir(SKILLS):
        nomes = sorted(d for d in os.listdir(SKILLS) if os.path.isdir(os.path.join(SKILLS, d)))
    mem = ""
    p = os.path.join(SKILLS, "MEMORIA.md")
    if os.path.isfile(p):
        mem = open(p, encoding="utf-8", errors="ignore").read()[:700]
    s = ""
    if nomes:
        s += " Habilidades em ~/.overia/skills/<nome>/SKILL.md (leia com cat quando precisar): " + ", ".join(nomes) + "."
    if mem:
        s += " Memória: " + mem.replace("\n", " ")
    return s


PADRAO = {
    "criar-imagem": (
        "HABILIDADE criar-imagem: NÃO pergunte nada, desenhe direto e faça SÓ a imagem (sem vídeo). "
        "Rode UM comando python3 -c com Pillow (se faltar: pip install pillow) usando ellipse, polygon, rectangle, line, e salve em $FOTOS. "
        "Exemplo (gato):\n"
        "python3 -c \"from PIL import Image,ImageDraw;i=Image.new('RGB',(400,400),'white');d=ImageDraw.Draw(i);"
        "d.ellipse((100,120,300,320),fill='orange');d.polygon([(110,150),(130,60),(180,125)],fill='orange');"
        "d.polygon([(290,150),(270,60),(220,125)],fill='orange');d.ellipse((150,190,180,220),fill='black');"
        "d.ellipse((220,190,250,220),fill='black');i.save('$FOTOS/gato.png')\""),
    "criar-videos": (
        "HABILIDADE criar-video: faça SÓ o vídeo pedido, a partir da imagem já criada (se ainda não existe, crie a imagem antes). "
        "Use ffmpeg (se faltar: pkg install -y ffmpeg) e salve em $VIDEOS. Girando:\n"
        "ffmpeg -y -loop 1 -i \"$FOTOS/gato.png\" -t 6 -vf \"rotate=2*PI*t/3:c=white,format=yuv420p\" -c:v libx264 \"$VIDEOS/gato.mp4\"\n"
        "Dançando (balançando): troque o filtro por rotate='0.4*sin(2*PI*t*1.5)':c=white,format=yuv420p")
}
ROTAS = [(r"\b(imagem|imagens|foto|fotos|desenh\w*|figura|ilustra\w*)\b", "criar-imagem"),
         (r"\b(v[ií]deos?|filme|anima\w*)\b", "criar-videos")]


def dicas_skills(msg):
    achadas = []
    for rx, nome in ROTAS:
        if re.search(rx, msg, re.I):
            p = os.path.join(SKILLS, nome, "SKILL.md")
            if os.path.isfile(p):
                achadas.append((nome, open(p, encoding="utf-8", errors="ignore").read()[:900]))
            elif nome in PADRAO:
                achadas.append((nome, PADRAO[nome]))
    return achadas


def prompt_sistema():
    return ("Você é a Kelly, a IA do overIA Code, agente de terminal no Termux (Android). Responda em português, curto. "
            "Para agir, escreva UM bloco ```bash com o comando e NADA depois dele; espere a saída. "
            "Use bloco bash só para comandos que devem rodar agora, nunca como exemplo. Comandos curtos e simples. "
            "Internet: curl, wget, python3. Imagens: python3 com Pillow (pip install pillow) ou imagemagick. Vídeos: ffmpeg, yt-dlp. "
            "Salve o que criar nas pastas: fotos em \"$FOTOS\", vídeos em \"$VIDEOS\", outros arquivos em \"$ARQUIVOS\" (sempre entre aspas) e diga o nome do arquivo. Para abrir arquivo use termux-open (não existe open nem xdg-open). "
            "Se faltar ferramenta, instale (pkg install -y X); o usuário é perguntado antes. "
            "Faça só o que foi pedido, nada além. Pedidos simples (desenhar, criar, baixar) você faz direto, sem perguntar. Só pergunte se faltar informação essencial: uma linha 'PERGUNTA: texto' e depois 2 a 4 opções curtas, uma por linha começando com '- '. Pare e espere. "
            "Se não entender o pedido ou se confundir, diga isso em UMA frase e peça para o usuário explicar melhor. Nunca repita texto, nem o que o usuário escreveu. "
            "Nunca diga 'não consigo'. Não repita um comando que falhou: mude a abordagem. Ao terminar, responda sem bloco."
            + texto_skills() + texto_emocao())


# ---------- modelos ----------
def caminho(m):
    return os.path.join(MODELOS, m[3])


def lista_modelos():
    print()
    fala("idle", cor("ciano", "Escolha a IA") + cor("dim", "  (tamanho | nível)"))
    for m in CATALOGO:
        baixado = os.path.isfile(caminho(m))
        marca = cor("verde", "[baixado]") if baixado else cor("dim", "[não baixado]")
        nivel = {"Fraco": "amar", "Médio": "ciano", "Forte": "verde", "Máximo": "verm"}[m[5]]
        print(f" {m[0]:>2}) {m[1]:<20} {m[4]:>4.1f} GB  {cor(nivel, f"{m[5]:<7}")}  {marca}")
    print(f"  g) Gemini (nuvem)       0 GB  {cor('verde', 'Rápido ⚡')}   " + cor("dim", "precisa de internet + chave"))
    print(cor("dim", "  Dica: no celular, Fraco/Médio são rápidos; Forte/Máximo ficam bem lentos."))


def baixar(m):
    url = f"https://huggingface.co/{m[2]}/resolve/main/{m[3]}"
    destino = caminho(m)
    fala("trab", f"Baixando {m[1]} (~{m[4]} GB)...")
    r = subprocess.run(["curl", "-L", "-C", "-", "-#", "-o", destino + ".parte", url])
    if r.returncode != 0 or not os.path.isfile(destino + ".parte"):
        fala("erro", "O download falhou. Rode de novo para continuar de onde parou.")
        return False
    os.replace(destino + ".parte", destino)
    return True


def parar_servidor():
    p = E.servidor
    if p and p.poll() is None:
        p.terminate()
        try:
            p.wait(timeout=8)
        except Exception:
            p.kill()
    E.servidor = None


def sobe_servidor(m):
    parar_servidor()
    E.backend = None
    E.modelo = m
    if shutil.which("pkill"):  # limpa motor antigo que tenha ficado preso na porta
        subprocess.run(["pkill", "-f", f"llama-server.*--port {PORT}"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(0.5)
    if not shutil.which("llama-server"):
        fala("erro", "llama-server não encontrado. Instale com: pkg install llama-cpp")
        return False
    nthreads = max(2, min(4, (os.cpu_count() or 4) // 2))
    ctx = int(os.environ.get("CTX", m[6]))
    cmd = ["llama-server", "-m", caminho(m), "-c", str(ctx), "-t", str(nthreads),
           "--host", "127.0.0.1", "--port", str(PORT)]
    logf = open(LOG, "w")
    # sessão própria: o Ctrl+C do terminal não chega no motor da IA
    E.servidor = subprocess.Popen(cmd, stdout=logf, stderr=logf, stdin=subprocess.DEVNULL, start_new_session=True)
    spin.start("carregando " + m[1])
    ok = False
    for _ in range(300):
        if E.servidor.poll() is not None:
            break
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/health", timeout=2) as r:
                if r.status == 200:
                    ok = True
                    break
        except Exception:
            pass
        time.sleep(1)
    spin.stop()
    if not ok:
        fala("erro", f"Não consegui carregar. Veja o log: {LOG}")
        parar_servidor()
        return False
    E.backend = {"nome": m[1], "url": f"http://127.0.0.1:{PORT}/v1", "modelo": "local", "chave": "",
                 "ctx": ctx, "gemma": "gemma" in m[3].lower(), "nivel": m[5]}
    return True


def pega_chave():
    k = os.environ.get("GEMINI_API_KEY", "").strip()
    if not k and os.path.isfile(CHAVE):
        k = open(CHAVE).read().strip()
    if not k:
        print(cor("dim", "Cole sua chave do Gemini (ela fica só em ~/.overia/gemini.key):"))
        k = input("> ").strip()
        if k:
            with open(CHAVE, "w") as f:
                f.write(k)
            os.chmod(CHAVE, 0o600)
    return k


def usa_gemini():
    k = pega_chave()
    if not k:
        return False
    parar_servidor()
    E.backend = {"nome": "Gemini", "url": "https://generativelanguage.googleapis.com/v1beta/openai",
                 "modelo": GEMINI_MODELO, "chave": k, "ctx": 16000, "gemma": False, "nivel": "Nuvem"}
    return True


def escolhe_modelo(esc=None):
    if esc is None:
        lista_modelos()
        esc = input(cor("ciano", "número ou g > ")).strip().lower()
    if esc == "g":
        ok = usa_gemini()
        if ok:
            open(ULTIMO, "w").write("g")
        return ok
    m = next((x for x in CATALOGO if x[0] == esc), None)
    if not m:
        fala("erro", "Opção inválida.")
        return False
    if not os.path.isfile(caminho(m)):
        r = input(f"Baixar {m[1]} (~{m[4]} GB)? [s/n] ").strip().lower()
        if r != "s" or not baixar(m):
            return False
    if sobe_servidor(m):
        open(ULTIMO, "w").write(m[0])
        return True
    return False


# ---------- conversa ----------
def tokens(txt):
    return len(txt) // 3 + 1


def monta_mensagens():
    B = E.backend
    sistema = prompt_sistema()
    orcamento = B["ctx"] - 700 - tokens(sistema)
    h = list(E.historia)
    usados, recorte = 0, []
    for msg in reversed(h):
        t = tokens(msg["content"])
        if usados + t > orcamento and recorte:
            break
        recorte.append(msg)
        usados += t
    recorte.reverse()
    while recorte and recorte[0]["role"] != "user":
        recorte.pop(0)
    recorte = [limpa(m) for m in recorte]
    if B["gemma"] and recorte:  # gemma não aceita papel "system"
        recorte[0] = {"role": "user", "content": sistema + "\n\n" + recorte[0]["content"]}
        return recorte
    return [{"role": "system", "content": sistema}] + recorte


def limpa(m):
    img = m.get("img")
    if img and E.backend["nome"] == "Gemini" and os.path.isfile(img):
        mime = "image/" + ("jpeg" if img.lower().endswith((".jpg", ".jpeg")) else img.rsplit(".", 1)[-1].lower())
        b64 = base64.b64encode(open(img, "rb").read()).decode()
        return {"role": m["role"], "content": [{"type": "text", "text": m["content"]},
                                               {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}"}}]}
    c = m["content"]
    if img:
        c += "\n(Esta IA local não enxerga imagens; use comandos ou troque para o Gemini em /modelos.)"
    return {"role": m["role"], "content": c}


def tem_loop(texto, user):
    if len(texto) > 120:
        cauda = texto[-40:]
        if len(set(cauda)) > 8 and texto.count(cauda) >= 3:
            return True
        linhas = [l.strip() for l in texto.split("\n") if len(l.strip()) >= 12]
        for l in set(linhas[-6:]):
            if linhas.count(l) >= 3:
                return True
    u = user.strip()[:40]
    return len(u) >= 20 and texto.count(u) >= 2  # repetiu o que o usuário escreveu


def gera(msgs, _retry=False):
    B = E.backend
    corpo = {"model": B["modelo"], "messages": msgs, "stream": True, "max_tokens": 600, "temperature": 0.4}
    ultimo = next((m["content"] for m in reversed(msgs) if m["role"] == "user"), "")
    if not isinstance(ultimo, str):
        ultimo = ultimo[0].get("text", "")
    if B["nome"] != "Gemini":
        corpo.update(repeat_penalty=1.15 + 0.15 * E.forca, repeat_last_n=256, top_k=40, top_p=0.9,
                     dry_multiplier=0.8, temperature=0.4 + 0.4 * E.forca)
    E.loop = False
    heads = {"Content-Type": "application/json"}
    if B["chave"]:
        heads["Authorization"] = "Bearer " + B["chave"]
    req = urllib.request.Request(B["url"] + "/chat/completions", data=json.dumps(corpo).encode(), headers=heads)
    texto, primeiro = "", True
    spin.start("pensando")
    try:
        with urllib.request.urlopen(req, timeout=900) as r:
            for raw in r:
                linha = raw.decode("utf-8", "ignore").strip()
                if not linha.startswith("data:"):
                    continue
                d = linha[5:].strip()
                if d == "[DONE]":
                    break
                try:
                    delta = json.loads(d)["choices"][0]["delta"].get("content") or ""
                except Exception:
                    continue
                if not delta:
                    continue
                if primeiro:
                    spin.stop()
                    sys.stdout.write(cor("lar", "● "))
                    primeiro = False
                sys.stdout.write(delta)
                sys.stdout.flush()
                texto += delta
                if tem_loop(texto, ultimo):
                    E.loop = True
                    break
    except urllib.error.HTTPError as e:
        spin.stop()
        corpo_err = e.read().decode("utf-8", "ignore")[:300]
        fala("erro", f"Erro {e.code}: {corpo_err}")
        return None
    except KeyboardInterrupt:
        spin.stop()
        print(cor("dim", "\n(interrompido)"))
        return texto or None
    except Exception as e:
        spin.stop()
        morto = E.servidor is None or E.servidor.poll() is not None
        recusou = isinstance(getattr(e, "reason", None), ConnectionRefusedError)
        if B["nome"] != "Gemini" and not _retry and E.modelo and (morto or recusou):
            fala("trab", "O motor da IA tinha caído. Religando…")
            if sobe_servidor(E.modelo):
                return gera(msgs, _retry=True)
        fala("erro", f"Falha de conexão: {e}")
        return None
    spin.stop()
    print()
    return texto


CORTADO = "\0cortado"


def extrai_cmd(resp):
    if resp.count("```") % 2 == 1:
        return CORTADO  # bloco sem fechar: resposta cortada
    blocos = list(re.finditer(r"```(?:bash|sh|shell)?\n(.*?)```", resp, re.S))
    if len(blocos) != 1:
        return None  # vários blocos = exemplos, não executa
    b = blocos[0]
    if len(resp[b.end():].strip()) > 160:
        return None  # bloco seguido de explicação longa = exemplo
    return b.group(1).strip()


def menu(titulo, opcoes, permitir_outro=True):
    """Menu com setas ↑/↓ + Enter (ou tecle o número). Retorna o texto escolhido."""
    itens = list(opcoes) + (["Digitar outra resposta…"] if permitir_outro else [])
    cols = shutil.get_terminal_size((50, 20)).columns
    itens = [t[:cols - 8] for t in itens]
    print(cor("lar", "● ") + cor("neg", titulo))
    fd = sys.stdin.fileno() if sys.stdin.isatty() and termios else None
    if fd is None:  # sem terminal interativo: lista numerada
        for n, t in enumerate(itens, 1):
            print(f"  {n}. {t}")
        try:
            sel = int(input("número > ")) - 1
        except Exception:
            sel = 0
        sel = min(max(sel, 0), len(itens) - 1)
    else:
        sel = 0

        def desenha(primeira):
            if not primeira:
                sys.stdout.write(f"\033[{len(itens)}A")
            for n, t in enumerate(itens):
                if n == sel:
                    linha = cor("lar", f" ❯ {n + 1}. {t}")
                else:
                    linha = cor("dim", f"   {n + 1}. {t}")
                sys.stdout.write("\033[2K\r" + linha + "\n")
            sys.stdout.flush()

        antigo = termios.tcgetattr(fd)
        try:
            tty.setcbreak(fd)
            desenha(True)
            while True:
                ch = os.read(fd, 1)
                if ch == b"\x1b":
                    seq = b""
                    while select.select([fd], [], [], 0.05)[0] and len(seq) < 2:
                        seq += os.read(fd, 1)
                    if seq in (b"[A", b"OA"):
                        sel = (sel - 1) % len(itens)
                    elif seq in (b"[B", b"OB"):
                        sel = (sel + 1) % len(itens)
                elif ch in (b"k",):
                    sel = (sel - 1) % len(itens)
                elif ch in (b"j",):
                    sel = (sel + 1) % len(itens)
                elif ch in (b"\r", b"\n"):
                    break
                elif ch.isdigit() and 1 <= int(ch) <= len(itens):
                    sel = int(ch) - 1
                    desenha(False)
                    break
                desenha(False)
        finally:
            termios.tcsetattr(fd, termios.TCSADRAIN, antigo)
    if permitir_outro and sel == len(itens) - 1:
        return input(cor("lar", "  sua resposta > ")).strip()
    return itens[sel]


def extrai_pergunta(resp):
    m = re.search(r"PERGUNTA:\s*(.+)", resp)
    if not m:
        return None
    ops = [x.group(1).strip() for x in re.finditer(r"^\s*(?:[-*•]|\d+[.)])\s+(.+)$", resp[m.end():], re.M)]
    vistos, ops2 = set(), []
    for o in ops:
        o = re.sub(r"^\d+[.)]\s*", "", o).strip()
        if o and o.lower() not in vistos:
            vistos.add(o.lower())
            ops2.append(o)
    ops = ops2[:5]
    return (m.group(1).strip(), ops) if len(ops) >= 2 else None


INSTALA = re.compile(r"\b(pkg|apt|apt-get|pip3?|npm|cargo|gem|brew)\b[^\n;&|]*\b(install|add)\b")
PERIGO = re.compile(r"\brm\s+-[a-zA-Z]*[rf]|\bmkfs|\bdd\s+if=|>\s*/dev/(sd|mmd|block)|:\(\)\s*\{|\bchmod\s+-R\s+777|\bshred\b")


def confirma(cmd):
    """Retorna (permitido, comentario_do_usuario)."""
    instala = bool(INSTALA.search(cmd))
    perigo = bool(PERIGO.search(cmd))
    if not instala and not perigo:
        return True, ""
    if instala and E.auto_instala and not perigo:
        return True, ""
    print(cor("amar", "● ") + cor("neg", "Quero instalar algo:" if instala else "Esse comando pode apagar coisas:"))
    print(cor("dim", "  " + cmd[:300].replace("\n", "\n  ")))
    ops = ["Sim"] + (["Sim, e não perguntar mais instalações nesta sessão"] if instala and not perigo else []) + ["Não, vou dizer o que fazer"]
    r = menu("Permitir?", ops, permitir_outro=False)
    if r.startswith("Sim, e"):
        E.auto_instala = True
        return True, ""
    if r == "Sim":
        return True, ""
    return False, input(cor("lar", "  o que prefere? > ")).strip()


def snapshot():
    r = {}
    for d in PATHS.values():
        for raiz, _, fs in os.walk(d):
            for f in fs:
                p = os.path.join(raiz, f)
                try:
                    r[p] = os.path.getmtime(p)
                except OSError:
                    pass
    return r


def sniff(path):
    h = open(path, "rb").read(16)
    if h.startswith(b"\x89PNG"): return ".png"
    if h.startswith(b"\xff\xd8"): return ".jpg"
    if h.startswith(b"GIF8"): return ".gif"
    if h[:4] == b"RIFF" and h[8:12] == b"WEBP": return ".webp"
    if h.startswith(b"%PDF"): return ".pdf"
    if h.startswith(b"PK"): return ".zip"
    if h[4:8] == b"ftyp": return ".mp4"
    if h.startswith(b"ID3"): return ".mp3"
    try:
        open(path, "rb").read(2000).decode("utf-8")
        return ".txt"
    except Exception:
        return ".bin"


def anexar():
    """Abre o seletor de arquivos do Android (igual ao do Chrome) e guarda o arquivo em Anexos."""
    tmp = os.path.join(PATHS["ANEXOS"], ".tmp")
    if os.path.exists(tmp):
        os.remove(tmp)
    if shutil.which("termux-storage-get"):
        fala("idle", "Abrindo o seletor de arquivos do Android…")
        subprocess.run(["termux-storage-get", tmp])
        if not os.path.isfile(tmp) or os.path.getsize(tmp) == 0:
            fala("erro", "Nenhum arquivo selecionado.")
            return None
        dest = os.path.join(PATHS["ANEXOS"], time.strftime("anexo-%Y%m%d-%H%M%S") + sniff(tmp))
        os.replace(tmp, dest)
    else:
        fala("idle", cor("amar", "Para abrir o seletor do Android: instale o app Termux:API e rode  pkg install termux-api"))
        pasta = os.path.join(HOME, "storage", "downloads")
        if not os.path.isdir(pasta):
            fala("erro", "Rode termux-setup-storage primeiro.")
            return None
        arqs = sorted((f for f in os.listdir(pasta) if os.path.isfile(os.path.join(pasta, f))),
                      key=lambda f: os.path.getmtime(os.path.join(pasta, f)), reverse=True)[:9]
        if not arqs:
            fala("erro", "A pasta Downloads está vazia.")
            return None
        esc = menu("Escolha um arquivo de Downloads", arqs, permitir_outro=False)
        dest = os.path.join(PATHS["ANEXOS"], esc)
        shutil.copy(os.path.join(pasta, esc), dest)
    fala("feliz", "Anexado: " + cor("verde", os.path.relpath(dest, BASE)))
    return dest


def mensagem_anexo(dest, instrucao):
    ext = os.path.splitext(dest)[1].lower()
    msg = f'[Anexo: "{dest}" ({os.path.getsize(dest)} bytes). Use o caminho entre aspas.]\n{instrucao or "Analise este arquivo."}'
    if ext in TEXTOS or ext == ".txt":
        try:
            msg += "\nInício do conteúdo:\n" + open(dest, encoding="utf-8", errors="ignore").read(1500)
        except Exception:
            pass
    img = dest if ext in IMGS and os.path.getsize(dest) < 4_000_000 else None
    return msg, img


def projetos():
    arqs = []
    for k, d in PATHS.items():
        for raiz, _, fs in os.walk(d):
            arqs += [os.path.join(raiz, f) for f in fs if not f.startswith(".")]
    arqs.sort(key=os.path.getmtime, reverse=True)
    if not arqs:
        fala("idle", "Ainda não há projetos em " + BASE)
        return
    rel = [os.path.relpath(p, BASE) for p in arqs[:10]]
    fala("idle", "Pasta: " + cor("verde", BASE))
    esc = menu("Abrir qual arquivo?", rel, permitir_outro=False)
    if shutil.which("termux-open"):
        subprocess.run(["termux-open", os.path.join(BASE, esc)])
    else:
        fala("erro", "Instale: pkg install termux-tools")


def executa(cmd, sentir=True):
    cmd = re.sub(r"\bpkg install(?! -y)", "pkg install -y", cmd)
    cmd = re.sub(r"\bapt(-get)? install(?! -y)", r"apt\1 install -y", cmd)
    cmd = re.sub(r"(?m)(^|[;&|]\s*)ffmpeg\b(?![^\n]*-loglevel)", r"\1ffmpeg -hide_banner -loglevel error", cmd)
    antes = snapshot()
    primeira = cmd.strip().splitlines()[0] if cmd.strip() else ""
    print(cor("lar", "● ") + cor("neg", "Executar") + cor("dim", "(" + (primeira[:60] + ("…" if len(primeira) > 60 else "")) + ")"))
    try:
        r = subprocess.run(["bash", "-c", cmd + '\necho "__CWD__:$PWD"'], cwd=E.cwd, env=ENV, stdin=subprocess.DEVNULL,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=300)
        out, cod = r.stdout.decode("utf-8", "ignore"), r.returncode
    except subprocess.TimeoutExpired as e:
        out, cod = (e.stdout or b"").decode("utf-8", "ignore") + "\n[tempo esgotado: 300s]", 124
    m = re.search(r"__CWD__:(.*)\s*$", out)
    if m:
        if os.path.isdir(m.group(1)):
            E.cwd = m.group(1)
        out = out[:m.start()]
    out = out.strip()
    mostra = out.splitlines()
    for n, l in enumerate(mostra[:12] or ["(sem saída)"]):
        print(cor("dim", ("  ⎿  " if n == 0 else "     ") + l[:200]))
    if len(mostra) > 12:
        print(cor("dim", f"     … +{len(mostra) - 12} linhas"))
    if len(out) > 1200:
        out = out[:600] + "\n...[cortado]...\n" + out[-600:]
    depois = snapshot()
    novos = [p for p in depois if antes.get(p) != depois[p]][:6]
    if novos:
        rel = [os.path.relpath(p, BASE) for p in novos]
        for r in rel:
            print(cor("lar", "  ✦ ") + cor("verde", "Salvo em overIA Code/" + r))
        out += "\nArquivos novos: " + ", ".join(rel)
        if shutil.which("termux-media-scan"):
            subprocess.Popen(["termux-media-scan"] + novos, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if sentir:
        if cod == 0:
            sente("sucesso")
        else:
            sente("falha", "Um comando que rodei deu erro e eu fiquei frustrada")
        if novos:
            sente("criou", "Criei " + os.path.basename(novos[0]) + " pra você")
    return f"Saída (código {cod}):\n{out or '(vazia)'}"


def turno(entrada, img=None):
    msg = {"role": "user", "content": entrada}
    if img:
        msg["img"] = img
    E.historia.append(msg)
    vistos, tentou, repetidos, acoes = set(), False, 0, 0
    for _ in range(8):
        resp = gera(monta_mensagens())
        if resp is None:
            E.historia.pop()
            return
        anterior = next((m["content"] for m in reversed(E.historia) if m["role"] == "assistant"), "")
        if not E.loop and len(resp.strip()) > 20 and resp.strip() == str(anterior).strip():
            E.loop = True  # repetiu a resposta anterior
        if E.loop:  # o texto repetido NUNCA entra no histórico
            fala("erro", cor("amar", "Me confundi e comecei a me repetir."))
            sente("confusa", "Me confundi e comecei a me repetir; fiquei sem graça")
            E.forca = 0
            if tentou:
                fala("idle", "Ainda me confundi. Explique de outro jeito ou troque de IA com /modelos.")
                E.historia.append({"role": "assistant", "content": "Me confundi. Pode explicar de outro jeito?"})
                return
            esc = menu("O que prefere?", ["Tentar de novo", "Explicar de outro jeito", "Limpar a conversa"], permitir_outro=False)
            if esc.startswith("Limpar"):
                E.historia = []
                return
            if esc.startswith("Explicar"):
                E.historia.append({"role": "assistant", "content": "Me confundi. Pode explicar de outro jeito?"})
                novo = input(cor("lar", "  explique > ")).strip()
                if not novo:
                    return
                E.historia.append({"role": "user", "content": novo})
                continue
            tentou = True
            E.forca = 1  # mais penalidade e mais variação na próxima tentativa
            continue
        E.forca = 0
        E.historia.append({"role": "assistant", "content": resp})
        perg = extrai_pergunta(resp)
        if perg:
            esc = menu(perg[0], perg[1])
            E.historia.append({"role": "user", "content": "Escolhi: " + esc})
            continue
        cmd = extrai_cmd(resp)
        if cmd is None:
            reage_fim(acoes)
            return
        if cmd == CORTADO:
            E.historia.append({"role": "user", "content": "Seu comando foi cortado por ser longo demais. Escreva um comando MENOR e mais simples."})
            continue
        if cmd in vistos:
            repetidos += 1
            if repetidos > 1:
                fala("erro", "Estou repetindo o mesmo comando. Parei; me diga como seguir.")
                return
            E.historia.append({"role": "user", "content": "Você já rodou esse comando. NÃO repita: use outra abordagem (siga a habilidade/receita, ou pergunte ao usuário)."})
            continue
        vistos.add(cmd)
        ok, fb = confirma(cmd)
        if not ok:
            sente("negado")
            E.historia.append({"role": "user", "content": "O usuário NEGOU." + (" Ele disse: " + fb if fb else " Tente outro jeito ou pergunte.")})
            continue
        E.historia.append({"role": "user", "content": executa(cmd)})
        acoes += 1
    fala("dorme", cor("dim", "Parei após 8 passos. Diga 'continue' se quiser seguir."))


def vtuple(v):
    return tuple(int(x) for x in re.findall(r"\d+", v))


def achar_atualizacao():
    """Procura arquivos overia-code*.py ou instalar-overia-code*.sh com versão maior."""
    marca = "#__OVERIA_PY" + "_INICIO__\n"
    melhor = None
    for pasta in (ATUALIZ, os.path.join(HOME, "storage", "downloads")):
        if not os.path.isdir(pasta):
            continue
        for f in os.listdir(pasta):
            if not re.match(r"(instalar-)?overia-code.*\.(py|sh)$", f, re.I):
                continue
            p = os.path.join(pasta, f)
            try:
                src = open(p, encoding="utf-8", errors="ignore").read()
            except OSError:
                continue
            if f.lower().endswith(".sh"):
                k = src.find(marca)
                if k < 0:
                    continue
                src = src[k + len(marca):]
            m = re.search(r'^VERSAO = "([\d.]+)"', src, re.M)
            if not m:
                continue
            v = vtuple(m.group(1))
            if v > vtuple(VERSAO) and (melhor is None or v > melhor[0]):
                melhor = (v, m.group(1), p, src)
    # Busca o pacote estável publicado no repositório, além dos arquivos locais.
    try:
        url = "https://raw.githubusercontent.com/brawlstarsarthurgg415-byte/overOS-light/main/instalar-overia-code.sh"
        req = urllib.request.Request(url, headers={"User-Agent": "OverCode-Updater/1.7"})
        with urllib.request.urlopen(req, timeout=8) as response:
            pacote = response.read(12 * 1024 * 1024).decode("utf-8")
        marca = "#__OVERIA_PY" + "_INICIO__\n"
        pos = pacote.find(marca)
        if pos >= 0:
            remoto = pacote[pos + len(marca):]
            versao = re.search(r'^VERSAO = "([\d.]+)"', remoto, re.M)
            if versao:
                vtxt = versao.group(1)
                vnum = vtuple(vtxt)
                if vnum > vtuple(VERSAO) and (melhor is None or vnum > melhor[0]):
                    destino = os.path.join(ATUALIZ, "instalar-overia-code-remoto.sh")
                    with open(destino, "w", encoding="utf-8") as arquivo:
                        arquivo.write(pacote)
                    melhor = (vnum, vtxt, destino, remoto)
    except Exception:
        pass
    return melhor


def aplicar_atualizacao(src):
    try:
        compile(src, "atualizacao", "exec")
    except SyntaxError:
        fala("erro", "O arquivo novo está com defeito. Não atualizei.")
        return False
    alvo = os.path.realpath(__file__)
    try:
        shutil.copy(alvo, os.path.join(DIR, "overia-code.antigo"))
        with open(alvo, "w", encoding="utf-8") as f:
            f.write(src)
        os.chmod(alvo, 0o755)
    except Exception as e:
        fala("erro", f"Não consegui gravar a atualização: {e}")
        return False
    return True


def verifica_atualizacao(manual=False):
    u = achar_atualizacao()
    if not u:
        if manual:
            fala("idle", f"Você já está na versão mais nova (v{VERSAO}).")
            print(cor("dim", "  Para atualizar, coloque o arquivo novo em: " + ATUALIZ.replace(HOME, "~", 1)))
        return
    ignorada = open(IGNORAR).read().strip() if os.path.isfile(IGNORAR) else ""
    if not manual and ignorada == u[1]:
        return
    esc = menu(f"Nova versão {u[1]} encontrada (você tem {VERSAO}). Atualizar agora?",
               ["Atualizar agora", "Agora não", "Ignorar esta versão"], permitir_outro=False)
    if esc.startswith("Ignorar"):
        open(IGNORAR, "w").write(u[1])
        return
    if esc.startswith("Atualizar") and aplicar_atualizacao(u[3]):
        fala("feliz", cor("verde", f"Atualizado para a v{u[1]}! Reiniciando…"))
        time.sleep(1)
        parar_servidor()
        os.execv(sys.executable, [sys.executable, os.path.realpath(__file__)] + sys.argv[1:])


def banner():
    cols = shutil.get_terminal_size((50, 20)).columns
    w = max(32, min(cols - 1, 46))
    dentro = w - 2

    def lin(t="", c=None):
        t = t[:dentro]
        e = (dentro - len(t)) // 2
        txt = " " * e + t + " " * (dentro - len(t) - e)
        return cor("lar", "│") + (cor(c, txt) if c else txt) + cor("lar", "│")

    titulo = f" overIA Code v{VERSAO} "
    print(cor("lar", "╭─" + titulo + "─" * (w - 3 - len(titulo)) + "╮"))
    print(lin(saudacao_banner(), "neg"))
    print(lin())
    for l in MASCOTE:
        print(lin(l, "lar"))
    print(lin())
    print(lin(f"Kelly está {dominante()} {cara()}", "rosa"))
    print(lin(f"{E.backend['nome']} · {E.backend['nivel']}", "dim"))
    pasta = E.cwd.replace(HOME, "~", 1)
    print(lin(("…" + pasta[-(dentro - 3):]) if len(pasta) > dentro - 2 else pasta, "dim"))
    print(cor("lar", "╰" + "─" * dentro + "╯"))
    print(cor("dim", " /modelos · /anexar · /projetos · /ajuda"))
    print(cor("dim", " /coracao · /diario · /nome"))
    print(cor("dim", " Projetos: " + BASE.replace(HOME, "~", 1)))
    if SEM_STORAGE:
        print(cor("amar", " ⚠ Rode termux-setup-storage para ver as pastas no celular."))


def le_entrada():
    cols = shutil.get_terminal_size((50, 20)).columns
    tick()
    humor = dominante()
    rot = " " + PET[humor] + " " + humor + " "
    print("\n" + cor("dim", "─" * max(4, cols - 1 - len(rot))) + cor("rosa", rot))
    return input("\001" + C["lar"] + "\002> \001" + C["r"] + "\002").strip()


def mostra_status():
    print()
    print(cor("ciano", "  ╭──────────── OVERCODE · STATUS ────────────╮"))
    print(cor("ciano", "  │") + "  Versão instalada : " + cor("verde", VERSAO))
    print(cor("ciano", "  │") + "  Modelo ativo     : " + str(E.backend.get("nome", "não selecionado") if E.backend else "não conectado"))
    print(cor("ciano", "  │") + "  Motor local      : " + ("ativo" if E.servidor and E.servidor.poll() is None else "parado"))
    print(cor("ciano", "  │") + "  Pasta de trabalho: " + E.cwd.replace(HOME, "~", 1))
    print(cor("ciano", "  │") + "  Atualizações     : " + ATUALIZ.replace(HOME, "~", 1))
    print(cor("ciano", "  ╰──────────────────────────────────────────╯"))
    print()


AJUDA = """Comandos:
  /modelos   trocar de IA        /limpar   apagar a conversa
  /ctx       ver contexto        /sair     fechar
  /pasta     escolher pasta (setas ↑/↓ + Enter)
  /anexar    anexar arquivo (seletor do Android)
  /atualizar buscar atualização online ou local
  /status    ver versão, modelo, motor e pasta atual
  /projetos  ver/abrir o que a overIA criou
  /coracao   ver os sentimentos da Kelly
  /diario    lembranças dela
  /nome      mudar como ela te chama
  !comando   rodar um comando seu direto
Fale normal: a overIA executa comandos sozinha e pergunta antes de instalar."""
def main():
    import signal, atexit

    def _sai(*_):
        parar_servidor()
        os._exit(0)
    for _s in (signal.SIGHUP, signal.SIGTERM):  # fechou o Termux: leva o motor junto
        try:
            signal.signal(_s, _sai)
        except Exception:
            pass
    atexit.register(parar_servidor)
    os.system("clear")
    verifica_atualizacao()
    ok = False
    ult = open(ULTIMO).read().strip() if os.path.isfile(ULTIMO) else None
    if ult:
        ok = escolhe_modelo(ult)
    while not ok:
        ok = escolhe_modelo()
    os.system("clear")
    conhece_usuario()
    reencontro()
    os.system("clear")
    banner()
    abre = abertura()
    fala(abre[0], abre[1])
    try:
        while True:
            try:
                entrada = le_entrada()
            except EOFError:
                break
            if not entrada:
                continue
            if entrada in ("/sair", "/exit", "sair"):
                break
            if entrada == "/ajuda":
                print(AJUDA)
            elif entrada == "/modelos":
                escolhe_modelo()
                while E.backend is None:
                    escolhe_modelo()
                sente("novidade", "Troquei de cérebro e fiquei curiosa")
                fala("curiosa", "Troquei de cérebro! Tudo novo por aqui.")
            elif entrada in ("/coracao", "/coração", "/sentimentos"):
                mostra_coracao()
            elif entrada in ("/diario", "/diário"):
                mostra_diario()
            elif entrada == "/nome":
                muda_nome()
            elif entrada == "/status":
                mostra_status()
            elif entrada == "/atualizar":
                verifica_atualizacao(manual=True)
            elif entrada == "/anexar":
                d = anexar()
                if d:
                    instr = input(cor("lar", "  o que fazer com ele? > ")).strip()
                    m, img = mensagem_anexo(d, instr)
                    turno(m, img)
            elif entrada == "/projetos":
                projetos()
            elif entrada == "/pasta":
                subs = sorted(d for d in os.listdir(E.cwd) if os.path.isdir(os.path.join(E.cwd, d)) and not d.startswith("."))[:12]
                esc = menu("Ir para qual pasta? (" + E.cwd.replace(HOME, "~", 1) + ")", [".. (voltar)"] + subs)
                alvo = os.path.dirname(E.cwd) if esc.startswith("..") else os.path.expanduser(esc)
                alvo = alvo if os.path.isabs(alvo) else os.path.join(E.cwd, alvo)
                if os.path.isdir(alvo):
                    E.cwd = os.path.abspath(alvo)
                    fala("feliz", "Agora em " + E.cwd.replace(HOME, "~", 1))
                else:
                    fala("erro", "Pasta não encontrada.")
            elif entrada == "/limpar":
                E.historia = []
                fala("idle", "Conversa limpa.")
            elif entrada == "/ctx":
                fala("idle", f"Contexto: {E.backend['ctx']} tokens | mensagens: {len(E.historia)}")
            elif entrada.startswith("!"):
                print(executa(entrada[1:], sentir=False))
            else:
                try:
                    if le_usuario(entrada):
                        continue
                    ds = dicas_skills(entrada)
                    if ds:
                        for nome, _ in ds:
                            print(cor("lar", "  ✦ ") + cor("dim", "habilidade: " + nome))
                        entrada = entrada + "\n\n[" + "\n".join(t for _, t in ds) + "]"
                    turno(entrada)
                except KeyboardInterrupt:
                    spin.stop()
                    print(cor("dim", "\n(interrompido)"))
    except KeyboardInterrupt:
        pass
    parar_servidor()
    despedida()


if __name__ == "__main__":
    main()
