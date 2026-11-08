# OverOS Custom Core — Over.ink
Esta branch inicia a linha **OverOS Custom Core**. O objetivo é um sistema próprio construído sobre o kernel Linux, sem Debian, Arch, Ubuntu ou outra distribuição como sistema-base.

## Regra do projeto: o pendrive É o computador
- Não existe instalador.
- A imagem final é gravada diretamente no pendrive (raw/DD).
- O computador inicia e roda a partir desse mesmo pendrive.
- A raiz do sistema e os dados do usuário ficam no pendrive; não exigir HD/SSD interno.
- O sistema deve preservar dados entre reinicializações e oferecer modo de recuperação.
- A imagem deve ser construída na nuvem/PC de desenvolvimento; não pedir um segundo pendrive ao usuário.

## Arquitetura pretendida
1. **Kernel:** kernel Linux upstream, compilado para x86_64, com módulos de armazenamento USB, USB HID, PCI, SATA/AHCI, rede, Wi-Fi, áudio, vídeo e filesystems necessários.
2. **Userland próprio:** toolchain e bibliotecas construídas para o OverOS; BusyBox/utilitários essenciais; init próprio e serviços pequenos. Não copiar uma rootfs de uma distribuição.
3. **Rede/firmware:** NetworkManager ou gerenciador leve equivalente, wpa_supplicant, DHCP, ferramentas IP, certificados e firmware selecionado conforme PCI/USB ID. Firmware proprietário opcional deve ser baixado de fonte autorizada durante a construção, nunca presumido para todo hardware.
4. **Pacotes:** suporte planejado aos comandos APT e Pacman. Eles não fazem parte do kernel e não são executáveis isolados: APT precisa do ecossistema dpkg e bibliotecas compatíveis; Pacman usa libalpm e seu próprio formato/repositórios. O OverOS terá uma camada de compatibilidade e raízes de pacotes isoladas para evitar que os dois sobrescrevam arquivos um do outro. Não declarar ambos operacionais até haver testes de instalação, atualização, remoção e rollback.
5. **Boot USB:** imagem raw híbrida BIOS/UEFI quando possível, com kernel, initramfs e raiz persistente no próprio pendrive. Sem fluxo de instalação em disco interno.

## Pacotes APT e Pacman: importante
Não é correto dizer que basta baixar só o APT e o Pacman. Ambos dependem de bibliotecas e de formatos de pacote. O plano é compilar suas dependências para a userland OverOS e manter seus bancos/raízes isolados. APT deverá consumir um repositório .deb compatível; Pacman deverá consumir um repositório .pkg.tar.zst compatível. Pacotes dessas famílias não são intercambiáveis, e não serão instalados no mesmo diretório sem uma camada de compatibilidade.

A primeira versão deve priorizar um repositório nativo OverOS e só depois habilitar os dois frontends de compatibilidade. Isso evita um sistema que parece ter gerenciadores, mas não consegue instalar programas de verdade.

## Estado atual
Esta branch contém a especificação inicial e um fragmento de configuração do kernel. **Ainda não é uma imagem bootável**, nem os gerenciadores de pacotes estão integrados. A construção do kernel, userland, firmware, imagem persistente e testes de boot ainda precisam ser implementados e executados.