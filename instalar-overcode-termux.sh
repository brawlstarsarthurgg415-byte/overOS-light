#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="https://github.com/brawlstarsarthurgg415-byte/overOS-light.git"
BRANCH="overcode-2.7-agent-core"
APP_DIR="$HOME/overcode-2.7"
CONFIG_DIR="$HOME/.config/overcode"
ENV_FILE="$CONFIG_DIR/kelly.env"
PROJECT_DIR="$HOME/OverCodeProjects"

echo "[OverCode] Preparando o Termux..."
pkg update -y
pkg install -y git python

mkdir -p "$CONFIG_DIR" "$PROJECT_DIR" "$PREFIX/bin"
if [ -d "$APP_DIR/.git" ]; then
  echo "[OverCode] Atualizando código..."
  git -C "$APP_DIR" fetch origin "$BRANCH"
  git -C "$APP_DIR" checkout "$BRANCH"
  git -C "$APP_DIR" pull --ff-only origin "$BRANCH"
elif [ -e "$APP_DIR" ]; then
  echo "A pasta $APP_DIR já existe e não é um repositório Git. Não vou sobrescrever."
  exit 1
else
  git clone --depth 1 --branch "$BRANCH" "$REPO" "$APP_DIR"
fi

if [ ! -f "$ENV_FILE" ]; then
  TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
  umask 077
  printf 'export KELLY_API_TOKEN=%q\n' "$TOKEN" > "$ENV_FILE"
  printf 'export KELLY_API_HOST=127.0.0.1\n' >> "$ENV_FILE"
  printf 'export KELLY_API_PORT=8765\n' >> "$ENV_FILE"
  printf 'export OVEROS_PROJECT_ROOT=%q\n' "$PROJECT_DIR" >> "$ENV_FILE"
  printf 'export KELLY_EXECUTOR=kelly_dual.executor:execute_task\n' >> "$ENV_FILE"
  chmod 600 "$ENV_FILE"
fi

cat > "$APP_DIR/overcode_cli.py" <<'PYCLI'
#!/usr/bin/env python3
"""CLI Termux do OverCode: inicia a API e oferece chat no terminal."""
import json, os, signal, subprocess, sys, time
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

HOME = Path.home()
APP = HOME / "overcode-2.7"
ENV = HOME / ".config/overcode/kelly.env"
STATE = HOME / ".local/state/overcode"
LOG = STATE / "api.log"
PID = STATE / "api.pid"

def load_env():
    if not ENV.exists():
        raise SystemExit("Configuração não encontrada. Execute novamente o instalador.")
    for line in ENV.read_text().splitlines():
        line=line.strip()
        if line.startswith("export ") and "=" in line:
            key, value = line[7:].split("=",1)
            if value.startswith("'") and value.endswith("'"):
                value=value[1:-1]
            elif value.startswith('"') and value.endswith('"'):
                value=value[1:-1]
            os.environ[key]=value
    os.environ.setdefault("KELLY_API_HOST","127.0.0.1")
    os.environ.setdefault("KELLY_API_PORT","8765")
    os.environ.setdefault("OVEROS_PROJECT_ROOT",str(HOME/"OverCodeProjects"))
    os.environ.setdefault("KELLY_EXECUTOR","kelly_dual.executor:execute_task")

def base():
    return "http://%s:%s" % (os.environ["KELLY_API_HOST"],os.environ["KELLY_API_PORT"])

def request(path, payload=None, auth=True, timeout=3):
    data=None if payload is None else json.dumps(payload).encode()
    headers={"Content-Type":"application/json"}
    if auth: headers["Authorization"]="Bearer "+os.environ.get("KELLY_API_TOKEN","")
    req=Request(base()+path,data=data,headers=headers)
    with urlopen(req,timeout=timeout) as r:
        raw=r.read()
        return json.loads(raw.decode()) if raw else {}

def healthy():
    try:
        return request("/api/health",auth=False).get("ok") is True
    except Exception:
        return False

def start():
    STATE.mkdir(parents=True,exist_ok=True)
    if healthy():
        print("[OverCode] API já está ativa em "+base())
        return
    # Um processo pode ocupar a porta sem ser a API do OverCode.
    try:
        with urlopen(base()+"/api/health",timeout=1):
            pass
    except HTTPError:
        pass
    except URLError as e:
        if "refused" not in str(e).lower() and "errno 111" not in str(e).lower():
            # A tentativa de conexão falhou por outra razão; o servidor ainda será testado ao iniciar.
            pass
    log=LOG.open("ab")
    proc=subprocess.Popen(
        [sys.executable,"-m","kelly_dual.api"],cwd=str(APP),
        env=os.environ.copy(),stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,
        start_new_session=True
    )
    log.close()
    PID.write_text(str(proc.pid))
    for _ in range(30):
        if healthy():
            print("[OverCode] API iniciada em "+base())
            return
        if proc.poll() is not None:
            break
        time.sleep(.3)
    print("[OverCode] Não consegui iniciar a API. Veja: overcode logs")
    raise SystemExit(1)

def find_api_pids():
    found=[]
    proc_root=Path("/proc")
    if not proc_root.exists(): return found
    for entry in proc_root.iterdir():
        if not entry.name.isdigit(): continue
        pid=int(entry.name)
        if pid==os.getpid(): continue
        try:
            cmd=(entry/"cmdline").read_bytes().replace(b"\\0",b" ")
            if b"kelly_dual.api" in cmd and b"-m" in cmd:
                found.append(pid)
        except (OSError,PermissionError):
            continue
    return found

def stop():
    pids=set(find_api_pids())
    if PID.exists():
        try: pids.add(int(PID.read_text().strip()))
        except ValueError: pass
    if not pids:
        print("[OverCode] Nenhuma API ativa encontrada.")
        PID.unlink(missing_ok=True)
        return
    for pid in pids:
        try: os.kill(pid,signal.SIGTERM)
        except ProcessLookupError: pass
    for _ in range(25):
        alive=[]
        for pid in pids:
            try: os.kill(pid,0); alive.append(pid)
            except ProcessLookupError: pass
        if not alive: break
        time.sleep(.2)
    PID.unlink(missing_ok=True)
    print("[OverCode] API parada.")

def chat():
    start()
    history=[]
    session_id=""
    print("Kelly pronta. Digite /sair para encerrar.")
    while True:
        try: message=input("\nVocê > ").strip()
        except (EOFError,KeyboardInterrupt):
            print(); break
        if not message: continue
        if message in {"/sair","/exit","/quit"}: break
        try:
            result=request("/api/chat",{"message":message,"history":history,"session_id":session_id},timeout=120)
            session_id=result.get("session_id",session_id)
            answer=result.get("answer", "Sem resposta.")
            print("\nKelly > "+answer)
            history.extend([{"role":"user","content":message},{"role":"assistant","content":answer}])
            history=history[-30:]
            if result.get("task_id"):
                print("[Kelly Code trabalhando em segundo plano: "+result["task_id"][:8]+"]")
        except HTTPError as e:
            print("[OverCode] Erro HTTP:",e.code,e.read().decode(errors="replace"))
        except Exception as e:
            print("[OverCode] Falha de conexão:",e)

def main():
    load_env()
    cmd=sys.argv[1].lower() if len(sys.argv)>1 else "chat"
    if cmd in ("chat",""): chat()
    elif cmd=="start": start()
    elif cmd=="stop": stop()
    elif cmd=="status":
        print("[OverCode] API online em "+base() if healthy() else "[OverCode] API offline.")
    elif cmd=="logs":
        if LOG.exists():
            lines=LOG.read_text(errors="replace").splitlines()
            print("\n".join(lines[-100:]))
        else: print("Ainda não há logs.")
    elif cmd=="update":
        stop()
        subprocess.run(["git","-C",str(APP),"pull","--ff-only","origin","overcode-2.7-agent-core"],check=True)
        print("[OverCode] Código atualizado. Reiniciando a API…")
        start()
    elif cmd in ("help","--help","-h"):
        print("Uso: overcode [start|stop|status|logs|update|help]\nSem argumento: abre o chat com Kelly.")
    else:
        print("Comando desconhecido. Use: overcode help")
        raise SystemExit(2)

if __name__=="__main__":
    main()
PYCLI
chmod +x "$APP_DIR/overcode_cli.py"

cat > "$PREFIX/bin/overcode" <<'WRAPPER'
#!/data/data/com.termux/files/usr/bin/bash
exec python "$HOME/overcode-2.7/overcode_cli.py" "$@"
WRAPPER
chmod +x "$PREFIX/bin/overcode"

echo
echo "[OverCode] Instalação concluída."
echo "Comando para conversar: overcode"
echo "Comandos: overcode start | stop | status | logs | update"
echo "API: http://127.0.0.1:8765"
echo "Token: $ENV_FILE"
echo
echo "A instalação não inicia uma segunda API automaticamente."
