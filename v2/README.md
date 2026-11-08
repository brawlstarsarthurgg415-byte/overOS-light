# OverOS Lite V2

**Projeto de sistema próprio baseado no kernel Linux — não é uma distro Debian/Ubuntu personalizada.**

A V2 fica isolada em `v2/`; os arquivos da V1 são preservados para consulta e recuperação. A imagem final será um disco `.img` gravável diretamente em USB e inicializável sem instalador.

## Direção do produto

- Kernel Linux configurado para o hardware real do Intelbras i30.
- Userland mínimo montado por nós: init do OverOS, serviços, configuração, ferramentas e políticas próprias. Componentes individuais de código aberto podem ser compilados e incorporados; nenhuma distribuição pronta será a base do produto.
- Interface OverOS própria com linguagem visual inspirada no KDE Plasma e no Windows Vista Aero: painel, menu, janelas, sombras, vidro/transparência com fallback sem composição, notificações, widgets e central de configurações.
- Painel web local para diagnóstico e administração autenticada, com logs, estado de serviços, recursos, rede, atualizações e ações permitidas.
- Aplicativos escolhidos por compatibilidade e consumo real. Navegador moderno é prioridade, mas será validado no i30 antes de ser definido como padrão.

## Princípio de compatibilidade

O i30 é o alvo de validação, não uma máquina presumida. Não declarar suporte a GPU, Wi-Fi, áudio, BIOS ou aceleração sem inventário e teste no aparelho. A V2 deve iniciar em modo gráfico básico mesmo quando composição/aceleração não estiverem disponíveis.


## Perfil do Intelbras i30 (manual do projeto)

O manual do aparelho informa cerca de **730 MB de RAM DDR2**, arquitetura de 64 bits e Wi-Fi Broadcom que depende do firmware **b43**. O interruptor físico de Wi-Fi também precisa estar ligado. Esses dados orientam a configuração, mas o reconhecimento real ainda precisa ser validado no notebook.

- Priorizar modo texto e shell framebuffer; não exigir compositor, navegador ou aceleração 3D para iniciar.
- Se o framebuffer falhar, permanecer em um menu de recuperação de terminal.
- Antes do QR de pareamento, oferecer configuração de rede quando não houver rota padrão.
- A imagem completa precisa incluir NetworkManager, network-manager-tui (comando nmtui), wpasupplicant, firmware b43 e openssh-server.
- Para o Wi-Fi Broadcom, o firmware precisa estar presente na imagem; só instalar nmtui não resolve uma placa sem firmware.
- Testar primeiro por cabo ou tethering USB se o Wi-Fi não subir; não declarar o driver funcional sem teste físico.

**Limite atual:** a V2 ainda não possui um gerador de imagem inicializável integrado. O workflow adicionado nesta etapa verifica a compilação do shell nativo, não gera sozinho uma nova imagem .img.

## Estado

Esta pasta é o início isolado da V2. Ainda não é uma imagem inicializável nem um instalador. O build só será chamado de pronto depois de gerar a `.img`, validar partições/boot e executar testes no i30 físico.

## Estrutura

- `boot/`: configuração de boot e initramfs.
- `init/`: PID 1 e sequência de inicialização OverOS.
- `desktop/`: shell, tema, compositor e sessão gráfica.
- `web-panel/`: painel local e API administrativa.
- `apps/`: catálogo e configuração dos aplicativos.
- `build/`: scripts reproduzíveis de compilação e empacotamento.
- `docs/`: arquitetura, segurança e matriz de compatibilidade.
- `tests/`: testes automatizados e de imagem.