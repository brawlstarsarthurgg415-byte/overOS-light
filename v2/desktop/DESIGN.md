# OverOS Shell — direção visual Epic/Epik Shell

A referência escolhida pelo usuário é a Epic/Epik Shell vista na pesquisa de desktops Astal/Arch. A OverOS deve desenvolver uma experiência própria nessa direção: moderna, elegante, com painéis, pop-ups e widgets integrados. Não substituir por um desktop genérico nem presumir que instalar o Plasma completo ou todo o stack Astal seja adequado ao hardware.

## Experiência visual

- Painel principal elegante e configurável, com lançador, aplicativos favoritos, janelas, bandeja e relógio.
- Launcher com pesquisa rápida, categorias, favoritos e navegação por teclado.
- Pop-ups contextuais para Wi-Fi, Bluetooth, volume, brilho, bateria, notificações e sessão.
- Widgets discretos e integrados: relógio/calendário, rede, áudio, bateria e monitoramento.
- Janelas com cantos suaves, sombras e foco evidente; transparência e blur opcionais.
- Central de configurações com aparência, tela, teclado, rede, energia, aplicativos e sistema.
- Personalização persistente de tema, cores, papel de parede, ícones, fontes, escala, posição/tamanho do painel, widgets, atalhos e efeitos.
- Animações curtas e suaves, com preferência por transições acionadas por interação em vez de animações contínuas.

## Princípios de implementação

1. A identidade visual e o layout devem permanecer consistentes mesmo quando efeitos forem reduzidos.
2. Construir os componentes da shell OverOS como processos/serviços sob demanda; evitar daemon separado para cada widget.
3. Carregar launcher e listas de aplicativos sob demanda; sem indexação global de arquivos por padrão.
4. Pop-ups devem ser acessíveis por teclado, fechar com Escape e não bloquear o restante da sessão.
5. Configurações devem ser salvas por usuário e reaplicadas após reiniciar.
6. Não copiar código, ícones ou assets de terceiros sem conferir licença e atribuição.
7. Avaliar GTK/AGS/Astal como tecnologia possível, mas medir dependências, RAM e compatibilidade antes de adotá-la como base.
8. Validar a sessão gráfica no Intelbras i30 antes de ativar blur, animações ou aceleração por padrão.

## Regras de desempenho

- Nenhuma animação contínua em idle.
- Widgets atualizam por eventos ou intervalos moderados, não por polling agressivo.
- Sem compositor 3D obrigatório para iniciar a sessão.
- Blur, transparência e sombras complexas são recursos opcionais.
- Se a GPU não suportar composição, manter o mesmo layout com superfícies opacas e sombras simples.
- Reduzir efeitos não deve remover widgets, pop-ups ou personalização.

## Estado

A direção visual foi escolhida. A imagem experimental atual ainda é um initramfs mínimo com console e utilitários de terminal; não contém a shell gráfica, os pop-ups ou os aplicativos gráficos. A seleção final do toolkit depende do inventário de hardware e dos testes no i30.
