#!/bin/sh
# Assistente inicial OverOS. Executado localmente como root; não envia dados à rede.
set -u
BASE=/etc/overos
mkdir -p "$BASE"
CFG="$BASE/setup.conf"
[ -f "$CFG" ] && exit 0
clear 2>/dev/null || true
echo
echo "  OVEROS LITE V2"
echo "  Configuracao inicial"
echo "  --------------------"
printf "Nome de usuario [over]: "
read USERNAME
USERNAME=$(printf '%s' "$USERNAME" | tr -cd 'a-z0-9_-')
[ -n "$USERNAME" ] || USERNAME=over
echo
echo "Escolha seu avatar:"
echo "  1) Azul   2) Roxo   3) Verde   4) Laranja"
printf "Opcao [1]: "
read AVATAR
case "$AVATAR" in 2) AVATAR=purple;; 3) AVATAR=green;; 4) AVATAR=orange;; *) AVATAR=blue;; esac
echo
echo "Como deseja iniciar o OverOS?"
echo "  1) Automatico (recomendado)"
echo "  2) Somente terminal"
echo "  3) Interface grafica"
printf "Opcao [1]: "
read MODE
case "$MODE" in 2) MODE=terminal;; 3) MODE=gui;; *) MODE=auto;; esac
printf 'username=%s\navatar=%s\nsession=%s\nsetup_complete=1\n' \
  "$USERNAME" "$AVATAR" "$MODE" > "$CFG"
chmod 600 "$CFG"
# A conta só é criada quando o utilitário existe no sistema.
if command -v adduser >/dev/null 2>&1; then
  adduser -D "$USERNAME" 2>/dev/null || true
fi
echo
echo "Configuracao salva em $CFG"
echo "Usuario selecionado: $USERNAME"
echo "Avatar: $AVATAR"
echo "Inicializacao: $MODE"
echo
echo "Nota: a imagem experimental ainda nao possui rootfs persistente."
echo "A conta e as preferencias so serao permanentes quando o disco"
echo "de dados do OverOS estiver integrado."
echo
printf "Pressione Enter para continuar..."
read _
