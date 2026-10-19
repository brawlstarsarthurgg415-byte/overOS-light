# OverOS Light: no terminal 1, abre a interface sozinho (menos no modo seguro)
if [ -z "${DISPLAY:-}" ] && [ "$(tty)" = "/dev/tty1" ] \
   && ! grep -q 'overos.modo=seguro' /proc/cmdline; then
  startx -- -nolisten tcp vt1
fi
