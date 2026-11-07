# Over Browser

Navegador original da OverOS Lite V2. O produto terá interface, identidade, abas, barra de endereço, favoritos, histórico, downloads e controles de privacidade desenvolvidos para OverOS.

## Decisão técnica transparente

A OverOS não vai fingir que construiu um motor web completo do zero nesta etapa. Um motor moderno precisa implementar HTML, CSS, JavaScript, TLS, codecs, sandbox e atualizações de segurança — trabalho enorme e contínuo. O Over Browser será um aplicativo original, com UI e integrações próprias, usando um motor web mantido e isolado como componente de renderização quando a base gráfica estiver pronta. Isso não significa copiar a interface de outro navegador.

## Requisitos

- Abas com criação, fechamento, restauração e navegação por teclado.
- Barra de endereço com pesquisa configurável.
- Voltar, avançar, recarregar, página inicial e indicador de carregamento.
- Favoritos e histórico locais, com opção de limpar.
- Downloads com destino escolhido e aviso de arquivo.
- Modo privado que não persiste histórico/cookies locais do perfil.
- Permissões por site (câmera, microfone, localização, notificações), inicialmente bloqueadas.
- Sem telemetria por padrão, sem conta obrigatória, sem processos de background quando fechado.
- Bloqueio de pop-ups intrusivos e controles para conteúdo misto.
- Perfil leve e limites de cache configuráveis.

## Desempenho e segurança

- Uma janela de processo principal; renderização isolada por site conforme suporte do motor.
- Suspender abas inativas e limitar abas em hardware com pouca RAM.
- Não pré-carregar páginas nem executar sincronização em background por padrão.
- Atualizações de segurança do motor são obrigatórias para distribuição.
- Não armazenar senhas em texto puro; integração futura com armazenamento seguro do sistema.
- Testar em hardware i686/x86_64 conforme arquitetura real do i30 antes de definir o pacote.

## Estado

Esta entrega cria a especificação do aplicativo. A interface nativa e o motor web ainda não estão integrados à imagem.
