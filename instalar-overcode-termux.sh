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

mkdir -p "$CONFIG_DIR" "$PROJECT_DIR"
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

cd "$APP_DIR"
echo
echo "[OverCode] Instalação pronta."
echo "A API ficará em http://127.0.0.1:8765"
echo "Na configuração do aplicativo, use esse endereço e o token salvo em:"
echo "$ENV_FILE"
echo
echo "Para iniciar novamente depois: bash ~/overcode-2.7/start-termux.sh"
echo "Para parar: Ctrl+C"
echo

cat > "$APP_DIR/start-termux.sh" <<'START'
#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
source "$HOME/.config/overcode/kelly.env"
cd "$HOME/overcode-2.7"
exec python -m kelly_dual.api
START
chmod +x "$APP_DIR/start-termux.sh"
exec "$APP_DIR/start-termux.sh"
