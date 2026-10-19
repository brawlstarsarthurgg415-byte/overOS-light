#!/usr/bin/env bash
# =============================================================================
#  OverOS Light Version - grava a imagem num pendrive com proteção
#
#  Uso:  sudo ./gravar-pendrive.sh overos-light.img /dev/sdX
#
#  ATENÇÃO: tudo que estiver no pendrive será APAGADO.
#  Este script nunca escolhe o disco sozinho e pede confirmação.
# =============================================================================
set -euo pipefail

IMG="${1:-}"
DEV="${2:-}"

uso() {
  echo "Uso:  sudo ./gravar-pendrive.sh overos-light.img /dev/sdX"
  echo
  echo "Discos que o sistema está vendo (RM=1 costuma ser pendrive):"
  lsblk -o NAME,SIZE,TYPE,RM,TRAN,MODEL,MOUNTPOINT
  exit 1
}

[ "$(id -u)" -eq 0 ] || { echo "Rode com sudo."; exit 1; }
[ -n "$IMG" ] && [ -f "$IMG" ] || uso
[ -n "$DEV" ] && [ -b "$DEV" ] || uso

# 1) nunca gravar no disco do próprio sistema
if lsblk -nro MOUNTPOINT "$DEV" | grep -qxE '/|/boot|/boot/efi|/home'; then
  echo "RECUSADO: $DEV contém o sistema deste computador (/, /boot ou /home)."
  exit 1
fi

# 2) a imagem tem que caber
TAM_IMG="$(stat -c %s "$IMG")"
TAM_DEV="$(blockdev --getsize64 "$DEV")"
if [ "$TAM_IMG" -gt "$TAM_DEV" ]; then
  echo "RECUSADO: a imagem ($((TAM_IMG / 1024 / 1024)) MB) não cabe em $DEV ($((TAM_DEV / 1024 / 1024)) MB)."
  exit 1
fi

# 3) mostrar o que vai ser apagado
echo
echo "Você escolheu: $DEV"
lsblk -o NAME,SIZE,TYPE,RM,TRAN,MODEL,LABEL,MOUNTPOINT "$DEV"
echo
if [ "$(lsblk -dno RM "$DEV")" != "1" ]; then
  echo "AVISO: este dispositivo NÃO está marcado como removível (pode ser um HD)."
  read -r -p "Se tem certeza, digite SIM em maiúsculas: " R
  [ "$R" = "SIM" ] || { echo "Cancelado."; exit 1; }
fi

NOME="$(basename "$DEV")"
read -r -p "TUDO em $DEV será apagado. Digite o nome do dispositivo ($NOME) para confirmar: " R
[ "$R" = "$NOME" ] || { echo "Nome diferente. Cancelado."; exit 1; }

# 4) gravar
umount "${DEV}"?* 2>/dev/null || true
echo "Gravando... (pode demorar vários minutos, não tire o pendrive)"
dd if="$IMG" of="$DEV" bs=4M status=progress conv=fsync
sync
echo
echo "Pronto! Pode remover o pendrive. No notebook, aperte F12 ou Esc ao ligar e escolha o pendrive."
