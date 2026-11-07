# OverOS Lite V2 — implementação do desktop

Este documento define o caminho de implementação do desktop da V2. Não representa recursos já entregues.

## Requisitos do produto

- Desktop próprio da OverOS, com identidade visual original.
- Painel inferior configurável, launcher pesquisável, janelas e área de notificações.
- Central de configurações funcional para tema, papel de parede, ícones, escala, fontes, painel, atalhos e efeitos.
- Perfis visuais: Aero (transparência/efeitos opcionais) e Econômico (sem blur, animações mínimas).
- Componentes e serviços leves, sem exigir aceleração 3D.
- Aplicativos reais; nenhum botão deve ser apenas decorativo.
- Preservar a V1 e manter toda a implementação na branch overos-lite-v2.

## Estratégia técnica

1. **Inventário do i30:** identificar CPU, RAM, GPU, firmware BIOS/UEFI, placa de rede e resolução. Não presumir suporte gráfico sem testar.
2. **Base própria:** kernel Linux + userland construído para OverOS. Ferramentas do runner de CI não são a base do sistema final.
3. **Sessão gráfica:** validar primeiro uma sessão X11 leve com gerenciador de janelas; comparar com Wayland apenas depois de confirmar suporte DRM/KMS. Não assumir que compositor com efeitos funciona em GPU antiga.
4. **Shell OverOS:** painel, launcher, alternador de janelas, notificações e central de configurações integrados como componentes reais.
5. **Personalização persistente:** salvar preferências por usuário em arquivos de configuração e aplicá-las após reiniciar.
6. **Aplicativos:** adicionar gerenciador de arquivos, terminal, configurações e navegador apenas após resolver dependências, certificados, fontes e integração de rede.
7. **Fastfetch:** usar o binário oficial do projeto fastfetch-cli/fastfetch, não um script que imite a saída. Personalizar a configuração e o logotipo da distribuição separadamente. Se o binário não estiver disponível no rootfs, o build deve falhar ou marcar explicitamente o recurso como pendente — nunca instalar uma imitação com o mesmo nome.
8. **Imagem:** incluir os arquivos da sessão gráfica e os aplicativos no rootfs da imagem raw; testar inicialização BIOS em QEMU e, depois, no i30 físico.

## Critérios de aceite

- A imagem inicia pelo USB em BIOS Legacy.
- A sessão gráfica inicia sem GPU 3D obrigatória.
- Tema, papel de parede, escala e configuração do painel podem ser alterados pela interface e continuam após reiniciar.
- O modo Econômico reduz efeitos e uso de memória.
- Fastfetch é identificado como o programa oficial e informa dados detectados, não valores simulados.
- A imagem e o log de testes são publicados no workflow da V2.
- Não declarar a imagem pronta para uso diário antes de testar no hardware físico.

## Estado atual

A imagem experimental anterior inicializa um kernel com initramfs mínimo e BusyBox. Ela ainda não contém o desktop completo, navegador ou painel administrativo funcional. O teste QEMU anterior não prova compatibilidade com o Intelbras i30 nem valida sozinho o caminho de boot pelo GRUB na imagem raw.
