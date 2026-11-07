# OverOS Lite V2 — implementação do desktop

Este documento define o caminho de implementação do desktop da V2. Não representa recursos já entregues.

## Direção visual escolhida

A referência visual escolhida pelo usuário é **Epic/Epik Shell**, da família de desktops personalizados associados ao ecossistema Astal. O nome exato do showcase ainda precisa ser confirmado; portanto, esta decisão registra a direção visual, não uma identificação definitiva do repositório ou uma autorização para copiar arquivos de terceiros.

Objetivo: trazer para a OverOS a aparência que motivou a escolha — shell moderno, elegante, com painéis e widgets bem integrados — sem trocar por um desktop genérico, nem reduzir a qualidade visual a um modo simplificado. A implementação deve ser própria, leve e compatível com o hardware real do Intelbras i30.

## Requisitos do produto

- Desktop próprio da OverOS, seguindo a direção visual Epic/Epik Shell escolhida pelo usuário.
- Painéis elegantes e configuráveis, launcher pesquisável, janelas e área de notificações.
- Widgets integrados (relógio, status de rede, áudio e sistema), com atualização eficiente e sem polling agressivo.
- Central de configurações funcional para tema, papel de parede, ícones, escala, fontes, painel, atalhos e efeitos.
- Transparência, sombras e animações com implementação econômica; blur deve ser opcional e desligável automaticamente se a GPU não suportar.
- Preservar a mesma identidade visual ao reduzir efeitos: economia de RAM não deve significar trocar para uma interface feia ou genérica.
- Componentes e serviços leves, sem exigir aceleração 3D como requisito absoluto.
- Aplicativos reais; nenhum botão deve ser apenas decorativo.
- Preservar a V1 e manter toda a implementação na branch overos-lite-v2.

## Estratégia técnica

1. **Inventário do i30:** identificar CPU, RAM, GPU, firmware BIOS/UEFI, placa de rede e resolução. Não presumir suporte gráfico sem testar.
2. **Base própria:** kernel Linux + userland construído para OverOS. Ferramentas do runner de CI não são a base do sistema final.
3. **Sessão gráfica:** validar primeiro uma sessão X11 leve com gerenciador de janelas; comparar com Wayland apenas depois de confirmar suporte DRM/KMS. Não assumir que compositor com efeitos funciona em GPU antiga.
4. **Shell OverOS:** implementar painel, launcher, alternador de janelas, widgets, notificações e central de configurações como componentes reais. Astal/AGS pode ser avaliado como tecnologia de referência, mas não deve ser incluído automaticamente sem verificar dependências, consumo e compatibilidade.
5. **Personalização persistente:** salvar preferências por usuário em arquivos de configuração e aplicá-las após reiniciar.
6. **Aplicativos:** adicionar gerenciador de arquivos, terminal, configurações e navegador apenas após resolver dependências, certificados, fontes e integração de rede.
7. **Fastfetch:** usar o binário oficial do projeto fastfetch-cli/fastfetch, não um script que imite a saída. Personalizar a configuração e o logotipo da distribuição separadamente. Se o binário não estiver disponível no rootfs, o build deve falhar ou marcar explicitamente o recurso como pendente — nunca instalar uma imitação com o mesmo nome.
8. **Imagem:** incluir os arquivos da sessão gráfica e os aplicativos no rootfs da imagem raw; testar inicialização BIOS em QEMU e, depois, no i30 físico.

## Critérios de aceite

- A imagem inicia pelo USB em BIOS Legacy.
- A sessão gráfica inicia sem GPU 3D obrigatória.
- A direção visual Epic/Epik Shell é reconhecível sem depender de serviços pesados.
- Tema, papel de parede, escala e configuração dos painéis podem ser alterados pela interface e continuam após reiniciar.
- Efeitos opcionais podem ser reduzidos sem substituir o layout e a identidade visual.
- Fastfetch é identificado como o programa oficial e informa dados detectados, não valores simulados.
- A imagem e o log de testes são publicados no workflow da V2.
- Não declarar a imagem pronta para uso diário antes de testar no hardware físico.

## Estado atual

A imagem experimental anterior inicializa um kernel com initramfs mínimo e BusyBox. Ela ainda não contém o desktop completo, navegador ou painel administrativo funcional. O teste QEMU anterior não prova compatibilidade com o Intelbras i30 nem valida sozinho o caminho de boot pelo GRUB na imagem raw.
