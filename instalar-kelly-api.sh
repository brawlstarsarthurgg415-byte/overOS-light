#!/usr/bin/env bash
set -euo pipefail

REPO="https://github.com/brawlstarsarthurgg415-byte/overOS-light.git"
APP_DIR="/opt/overcode"
ENV_DIR="/etc/overcode"
ENV_FILE="$ENV_DIR/kelly.env"
SERVICE="/etc/systemd/system/kelly-api.service"
RUN_USER="${SUDO_USER:-overos}"

if [ "$(id -u)" -ne 0 ]; then
  echo "Execute com sudo: sudo bash instalar-kelly-api.sh"
  exit 1
fi
id "$RUN_USER" >/dev/null 2>&1 || { echo "Usuário $RUN_USER não existe."; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "Python 3 não encontrado."; exit 1; }
command -v git >/dev/null 2>&1 || { echo "Git não encontrado. Instale com: sudo apt install git"; exit 1; }

echo "[OverCode] Baixando a API..."
if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" pull --ff-only
else
  if [ -e "$APP_DIR" ]; then
    echo "$APP_DIR já existe e não é um repositório Git. Parei para não sobrescrever."
    exit 1
  fi
  git clone --depth 1 "$REPO" "$APP_DIR"
fi

install -d -m 0750 -o root -g "$RUN_USER" "$APP_DIR"
install -d -m 0700 "$ENV_DIR"
install -d -m 0750 -o "$RUN_USER" -g "$RUN_USER" "/home/$RUN_USER/OverCodeProjects"

if [ ! -f "$ENV_FILE" ]; then
  TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
  umask 077
  cat > "$ENV_FILE" <<EOF
KELLY_API_TOKEN=$TOKEN
KELLY_API_HOST=127.0.0.1
KELLY_API_PORT=8765
OVEROS_PROJECT_ROOT=/home/$RUN_USER/OverCodeProjects
EOF
  chmod 600 "$ENV_FILE"
else
  echo "[OverCode] Mantive o token que já existia."
fi

cat > "$SERVICE" <<EOF
[Unit]
Description=OverCode Kelly API
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$RUN_USER
Group=$RUN_USER
WorkingDirectory=$APP_DIR
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/python3 -m kelly_dual.api
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ReadWritePaths=/home/$RUN_USER/.overos /home/$RUN_USER/OverCodeProjects

[Install]
WantedBy=multi-user.target
EOF

chmod 644 "$SERVICE"
systemctl daemon-reload
systemctl enable --now kelly-api
sleep 2
if systemctl is-active --quiet kelly-api; then
  echo
  echo "[OK] Kelly API instalada e ativa em http://127.0.0.1:8765"
  echo "A interface web é servida pelo mesmo endereço."
  echo "Token guardado em $ENV_FILE (permissão 600)."
  echo "Para ver o token: sudo cat $ENV_FILE"
  echo "Para logs: sudo journalctl -u kelly-api -f"
else
  echo "[ERRO] A API não iniciou. Veja: sudo journalctl -u kelly-api -n 60"
  exit 1
fi
