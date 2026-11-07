# Arquitetura técnica — OverOS Lite V2

## Camadas

1. **Firmware e boot:** BIOS Legacy como primeiro alvo do i30; bootloader GRUB ou Syslinux, escolhido após confirmar o firmware. UEFI pode ser adicionado depois sem mudar o userland.
2. **Kernel:** Linux configurado para CPU, chipset, USB, armazenamento, ACPI, framebuffer/DRM, áudio e rede identificados no i30. Configuração enxuta, mas sem remover drivers necessários ao boot USB.
3. **Initramfs:** localizar a partição raiz por UUID/label, carregar módulos essenciais, montar `/proc`, `/sys`, `/dev` e transferir para o rootfs.
4. **Userland OverOS:** musl como libc; BusyBox como conjunto de utilitários básicos (não como distribuição); PID 1 e supervisor OverOS; scripts e serviços próprios; gestão de dispositivos por mecanismos do kernel e regras mínimas.
5. **Rede e administração:** serviço de diagnóstico local, painel web autenticado, API de ações allowlist, logs estruturados e trilha de auditoria. Sem endpoint de shell arbitrário e sem root exposto à LAN.
6. **Desktop:** shell visual OverOS próprio. Alvo visual: composição Aero e organização do Plasma. A implementação inicial deve usar componentes pequenos e substituíveis; não carregar o KDE Plasma completo no i30 presumidamente limitado.
7. **Aplicativos:** navegador moderno testado no hardware; gerenciador de arquivos, terminal, leitor PDF, mídia, configurações e ferramentas de diagnóstico.

## KDE/Plasma: decisão de engenharia

Não prometer Plasma completo antes de medir recursos. Plasma real traz Qt, serviços de sessão, DBus, polkit, componentes KDE e processos residentes. A V2 deve entregar a experiência visual e funcional desejada por meio de um shell OverOS, com painel, launcher, bandeja, widgets e configurações próprias. Componentes KDE individuais só entram se o footprint medido couber.

## Navegador

NetSurf não será o navegador principal por não oferecer compatibilidade suficiente com a web moderna. Avaliar Chromium e Firefox em modo enxuto e alternativas baseadas em QtWebEngine/WebKitGTK somente após conhecer CPU, RAM, GPU e arquitetura do i30. Se o hardware tiver cerca de 700 MB de RAM, nenhum navegador moderno completo terá desempenho confortável em sites pesados; isso precisa ser medido, não escondido.

## Painel web e acesso da Kelly

- Servidor escuta na LAN somente quando habilitado pelo usuário; primeira configuração local gera credencial forte.
- Sessões autenticadas, cookies `HttpOnly`/`SameSite`, proteção CSRF, limites de tentativas e expiração.
- API com ações explícitas: ler status/logs, reiniciar serviços conhecidos, alternar sessão gráfica, coletar diagnóstico e aplicar atualizações assinadas.
- Nunca aceitar comando shell enviado pelo navegador. Operações privilegiadas passam por um broker pequeno com allowlist, validação de parâmetros e logs.
- A Kelly só acessa remotamente se o dono habilitar uma conexão segura/autorizada; não haverá porta administrativa pública por padrão.

## Persistência e atualização

A imagem USB deve ter partição de boot e rootfs. Separar dados mutáveis (`/var`, configurações e dados do usuário) para reduzir corrupção. Usar atualizações atômicas ou A/B quando o tamanho do pendrive permitir; no primeiro protótipo, oferecer atualização transacional com backup e rollback.

## Critérios de aceitação

- Boot em BIOS Legacy a partir de USB sem instalador.
- Modo gráfico básico recuperável sem aceleração.
- Uso de RAM/CPU medido em idle e com navegador.
- Painel web não expõe root nem comandos arbitrários.
- Logs permitem diagnosticar falha sem acesso físico.
- Navegação em páginas modernas testada no i30 real.