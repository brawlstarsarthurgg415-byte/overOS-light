#!/usr/bin/env bash
# =============================================================================
#  OverOS Light Version - gerador da imagem de disco (.img)
#
#  Rode num PC com Debian, Ubuntu ou Linux Mint (pode ser o Mint ao vivo),
#  como root, com internet:
#
#      sudo ./build-overos-light.sh
#
#  Resultado: overos-light.img, pronta para gravar no pendrive.
#  Opções (variáveis de ambiente), por exemplo:  sudo IMG_SIZE=6G ./build-overos-light.sh
#      SUITE=trixie            versão do Debian
#      IMG_SIZE=4G             tamanho da imagem (no 1º boot ela cresce até encher o pendrive)
#      INCLUIR_FIREFOX=sim     "nao" para deixar o Firefox de fora
#      SENHA=overos            senha do usuário "overos"
# =============================================================================
set -euo pipefail

SUITE="${SUITE:-trixie}"
MIRROR="${MIRROR:-http://deb.debian.org/debian}"
IMG_SIZE="${IMG_SIZE:-4G}"
INCLUIR_FIREFOX="${INCLUIR_FIREFOX:-sim}"
USUARIO="overos"
SENHA="${SENHA:-overos}"
ROTULO="OVERLIGHT"

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${OUT:-$AQUI/overos-light.img}"
WORK="${WORK:-$AQUI/work}"
MNT="$WORK/mnt"
LOOP=""
AVISOS="$WORK/avisos.txt"

msg()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
erro() { printf '\033[1;31mERRO: %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- verificações
[ "$(id -u)" -eq 0 ] || erro "Rode como root:  sudo ./build-overos-light.sh"

for c in debootstrap parted losetup mkfs.ext4 rsync grub-install udevadm e2fsck truncate; do
  command -v "$c" >/dev/null 2>&1 || erro "Falta o programa '$c'.
Instale com:  sudo apt install debootstrap parted rsync e2fsprogs grub-pc-bin grub-common udev debian-archive-keyring"
done
[ -d /usr/lib/grub/i386-pc ] || erro "Falta o grub-pc-bin.  Instale:  sudo apt install grub-pc-bin"
[ -f /usr/share/keyrings/debian-archive-keyring.gpg ] || erro "Falta a chave do Debian.  Instale:  sudo apt install debian-archive-keyring"
[ -d "$AQUI/overlay" ] || erro "Pasta 'overlay' não encontrada ao lado deste script."
[ ! -e "$OUT" ] || erro "$OUT já existe. Apague ou use OUT=outro-nome.img"

# ------------------------------------------------------------------- limpeza
desmontar_chroot() {
  local d
  for d in dev/pts dev proc sys; do
    if mountpoint -q "$MNT/$d"; then umount -l "$MNT/$d" || true; fi
  done
}
limpar() {
  set +e
  desmontar_chroot
  if mountpoint -q "$MNT"; then umount "$MNT"; fi
  if [ -n "$LOOP" ]; then losetup -d "$LOOP"; fi
}
trap limpar EXIT

no_chroot() {
  chroot "$MNT" env DEBIAN_FRONTEND=noninteractive LANG=C.UTF-8 \
    PATH=/usr/sbin:/usr/bin:/sbin:/bin "$@"
}

instalar_opcional() {
  # Instala um pacote por vez; se algum nome não existir, só avisa.
  local p
  for p in "$@"; do
    if ! no_chroot apt-get install -y --no-install-recommends "$p"; then
      echo "AVISO: pacote '$p' não foi instalado" | tee -a "$AVISOS"
    fi
  done
}

mkdir -p "$MNT"
: > "$AVISOS"

# ------------------------------------------------------- 1. disco virtual
msg "1/9 Criando a imagem de $IMG_SIZE"
truncate -s "$IMG_SIZE" "$OUT"
LOOP="$(losetup --find --show --partscan "$OUT")"
parted -s "$LOOP" mklabel msdos mkpart primary ext4 1MiB 100% set 1 boot on
partprobe "$LOOP" 2>/dev/null || true
udevadm settle
PART="${LOOP}p1"
for _ in 1 2 3 4 5 6 7 8 9 10; do [ -b "$PART" ] && break; sleep 1; done
[ -b "$PART" ] || erro "A partição $PART não apareceu."

msg "2/9 Formatando (ext4, rótulo $ROTULO) e montando"
mkfs.ext4 -F -L "$ROTULO" -m 1 "$PART" >/dev/null
mount "$PART" "$MNT"

# ------------------------------------------------------- 2. sistema base
msg "3/9 Baixando o Debian $SUITE (demora; precisa de internet)"
debootstrap --arch=amd64 --variant=minbase \
  --components=main,contrib,non-free,non-free-firmware \
  --include=ca-certificates,gnupg \
  "$SUITE" "$MNT" "$MIRROR"

msg "4/9 Configurando o sistema"
rm -f "$MNT"/etc/apt/sources.list.d/debian.sources
cat > "$MNT/etc/apt/sources.list" <<EOF
deb $MIRROR $SUITE main contrib non-free non-free-firmware
deb $MIRROR $SUITE-updates main contrib non-free non-free-firmware
deb http://security.debian.org/debian-security $SUITE-security main contrib non-free non-free-firmware
EOF
cat > "$MNT/etc/apt/apt.conf.d/99overos" <<'EOF'
APT::Install-Recommends "false";
APT::Install-Suggests "false";
EOF

echo "overos-light" > "$MNT/etc/hostname"
cat > "$MNT/etc/hosts" <<'EOF'
127.0.0.1 localhost
127.0.1.1 overos-light
EOF
cat > "$MNT/etc/fstab" <<EOF
# OverOS Light: sistema no pendrive, com poucas escritas
LABEL=$ROTULO  /     ext4   defaults,noatime,commit=60,errors=remount-ro  0 1
tmpfs          /tmp  tmpfs  defaults,noatime,nosuid,nodev,size=128m       0 0
EOF
ln -sf /usr/share/zoneinfo/America/Sao_Paulo "$MNT/etc/localtime"
echo "America/Sao_Paulo" > "$MNT/etc/timezone"
cat > "$MNT/etc/default/keyboard" <<'EOF'
XKBMODEL="abnt2"
XKBLAYOUT="br"
XKBVARIANT=""
XKBOPTIONS=""
BACKSPACE="guess"
EOF

# impede serviços de subirem dentro do chroot durante a instalação
printf '#!/bin/sh\nexit 101\n' > "$MNT/usr/sbin/policy-rc.d"
chmod +x "$MNT/usr/sbin/policy-rc.d"

mount --bind /dev     "$MNT/dev"
mount --bind /dev/pts "$MNT/dev/pts"
mount -t proc proc    "$MNT/proc"
mount -t sysfs sys    "$MNT/sys"

# ------------------------------------------------------- 3. pacotes
msg "5/9 Instalando kernel e programas (a parte mais demorada)"
no_chroot apt-get update
no_chroot apt-get install -y --no-install-recommends initramfs-tools

# initramfs com todos os drivers de USB/disco, para o boot do pendrive funcionar
sed -i -e 's/^MODULES=.*/MODULES=most/' -e 's/^COMPRESS=.*/COMPRESS=gzip/' \
  "$MNT/etc/initramfs-tools/initramfs.conf"
grep -q '^MODULES=most' "$MNT/etc/initramfs-tools/initramfs.conf" || erro "Não consegui configurar o initramfs."

no_chroot apt-get install -y --no-install-recommends linux-image-amd64

NUCLEO=(
  systemd-sysv udev dbus sudo locales console-setup keyboard-configuration kbd
  e2fsprogs cloud-guest-utils util-linux less nano procps
  network-manager wpasupplicant iw rfkill iputils-ping
  xserver-xorg-core xserver-xorg-input-libinput xserver-xorg-video-all
  xinit x11-xserver-utils openbox tint2 lxterminal fonts-dejavu-core
  netsurf-gtk mpv alsa-utils
  usbutils pciutils v4l-utils acpi upower lm-sensors memtester hdparm whiptail
  zram-tools htop
)
no_chroot apt-get install -y --no-install-recommends "${NUCLEO[@]}"

OPCIONAIS=(
  fastfetch btop cmatrix yt-dlp pulseaudio pcmanfm libgl1-mesa-dri
  b43-fwcutter firmware-b43-installer firmware-brcm80211 firmware-misc-nonfree
  firmware-linux-free memtest86+ xterm
)
instalar_opcional "${OPCIONAIS[@]}"
if [ "$INCLUIR_FIREFOX" = "sim" ]; then
  instalar_opcional firefox-esr
fi

# ------------------------------------------------------- 4. arquivos do OverOS
msg "6/9 Copiando os arquivos do OverOS (menu de testes, interface, ajustes)"
rsync -a --chown=root:root "$AQUI/overlay/" "$MNT/"
chmod 0755 "$MNT/usr/local/bin/overos-testes" "$MNT/usr/local/sbin/overos-growfs"
chmod 0440 "$MNT"/etc/sudoers.d/overos
cat > "$MNT/etc/overos-release" <<EOF
NAME="OverOS Light Version"
BASE="Debian $SUITE (amd64)"
BUILD="$(date +%Y-%m-%d)"
EOF
printf 'OverOS Light Version (base Debian %s)\n' "$SUITE" > "$MNT/etc/issue"

# idioma
sed -i 's/^# *pt_BR.UTF-8/pt_BR.UTF-8/' "$MNT/etc/locale.gen"
no_chroot locale-gen
no_chroot update-locale LANG=pt_BR.UTF-8

# usuário (depois do rsync, para receber os arquivos de /etc/skel)
GRUPOS=""
for g in sudo audio video plugdev netdev cdrom dialout adm; do
  if grep -q "^$g:" "$MNT/etc/group"; then   # só grupos que existem DENTRO da imagem
    GRUPOS="${GRUPOS:+$GRUPOS,}$g"
  fi
done
no_chroot useradd -m -s /bin/bash -G "$GRUPOS" "$USUARIO"
echo "$USUARIO:$SENHA" | no_chroot chpasswd
no_chroot passwd -l root >/dev/null

no_chroot systemctl enable overos-growfs.service
no_chroot systemctl enable NetworkManager.service
no_chroot systemctl enable zramswap.service || echo "AVISO: zramswap não habilitou" | tee -a "$AVISOS"

# ------------------------------------------------------- 5. inicialização
msg "7/9 Instalando o GRUB (boot BIOS antigo) no pendrive"
no_chroot update-initramfs -u -k all

if [ -e "$MNT/vmlinuz" ] && [ -e "$MNT/initrd.img" ]; then
  K="/vmlinuz"; I="/initrd.img"
else
  KV="$(find "$MNT/boot" -maxdepth 1 -name 'vmlinuz-*' | sort -V | tail -n1)"
  [ -n "$KV" ] || erro "Kernel não encontrado em /boot."
  K="/boot/$(basename "$KV")"
  I="/boot/initrd.img-${K#/boot/vmlinuz-}"
fi
[ -e "$MNT$I" ] || erro "initrd não encontrado: $I"

MEMTEST="$(find "$MNT/boot" -maxdepth 1 -name 'memtest86+*.bin' | sort | head -n1)"

grub-install --target=i386-pc --boot-directory="$MNT/boot" \
  --modules="part_msdos ext2" --no-floppy "$LOOP"

BASE="root=LABEL=$ROTULO rw rootwait rootdelay=5"
mkdir -p "$MNT/boot/grub"
cat > "$MNT/boot/grub/grub.cfg" <<EOF
set default=0
set timeout=8
insmod part_msdos
insmod ext2
search --no-floppy --label --set=root $ROTULO

menuentry "OverOS Light - Normal" {
    linux $K $BASE quiet loglevel=3
    initrd $I
}
menuentry "OverOS Light - Modo de teste (abre o menu de testes)" {
    linux $K $BASE quiet loglevel=3 overos.modo=teste
    initrd $I
}
menuentry "OverOS Light - Modo seguro (so terminal, sem video acelerado)" {
    linux $K $BASE nomodeset systemd.unit=multi-user.target overos.modo=seguro
    initrd $I
}
EOF
if [ -n "$MEMTEST" ]; then
  cat >> "$MNT/boot/grub/grub.cfg" <<EOF
menuentry "Teste de memoria RAM (memtest86+)" {
    linux16 /boot/$(basename "$MEMTEST")
}
EOF
else
  echo "AVISO: memtest86+ não foi encontrado; entrada de teste de RAM no GRUB não criada (use o menu de testes)." | tee -a "$AVISOS"
fi

# ------------------------------------------------------- 6. limpeza
msg "8/9 Limpando"
no_chroot apt-get clean
rm -rf "$MNT"/var/lib/apt/lists/* "$MNT"/usr/sbin/policy-rc.d
: > "$MNT/etc/machine-id"
rm -f "$MNT/var/lib/dbus/machine-id"
desmontar_chroot

msg "9/9 Finalizando"
umount "$MNT"
e2fsck -f -p "$PART" || true
losetup -d "$LOOP"
LOOP=""

msg "PRONTO!  Imagem: $OUT"
if [ -s "$AVISOS" ]; then
  echo "Avisos durante a construção (veja $AVISOS):"
  cat "$AVISOS"
fi
echo "Próximo passo: grave no pendrive (EtchDroid/Etcher) ou use:  sudo ./gravar-pendrive.sh $OUT /dev/sdX"
