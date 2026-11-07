# Pipeline de build V2

O build final deverá ser reproduzível em GitHub Actions, porque o Termux/Android não oferece de forma confiável `losetup`, montagem de loop devices e instalação do GRUB na imagem.

Etapas planejadas:

1. Fixar versões e hashes de kernel, musl e BusyBox.
2. Compilar toolchain/userland em ambiente de build limpo; nada de copiar `/usr` de uma distro para dentro do rootfs.
3. Compilar `overos-init` estaticamente contra musl.
4. Montar rootfs mínimo em diretório staging com BusyBox, init, configuração, certificados e serviços OverOS.
5. Integrar kernel e drivers após inventário do i30.
6. Criar imagem raw particionada, instalar bootloader e copiar rootfs.
7. Verificar partições, integridade, conteúdo e boot em QEMU.
8. Publicar `.img.xz` como artefato experimental; liberar como estável só após teste físico no i30.

O workflow não deve executar `debootstrap`, `apt install` no rootfs, nem copiar um root filesystem Debian/Ubuntu. Pacotes de compilação instalados no runner são ferramentas de build, não a base do sistema final.