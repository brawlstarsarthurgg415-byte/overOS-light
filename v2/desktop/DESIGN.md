# OverOS Shell — direção visual

Objetivo: combinar a clareza/organização do KDE Plasma com o acabamento Aero do Windows Vista, sem carregar o desktop Plasma completo.

## Elementos da experiência

- Barra inferior translúcida com menu OverOS, apps fixados, janelas abertas, bandeja e relógio.
- Launcher com busca rápida, categorias e favoritos.
- Janelas com cantos suaves, bordas luminosas discretas, sombras e foco bem visível.
- Painéis/widgets: relógio, rede, áudio, bateria e monitoramento básico.
- Central de configurações com páginas de aparência, tela, teclado, rede, energia, aplicativos e sistema.
- Tema claro/escuro, escala de interface e opção "efeitos reduzidos".
- Fallback automático sem blur/transparência quando GPU ou memória não suportarem composição.

## Regras de desempenho

1. Não manter animações contínuas nem widgets que consultem sensores em loop agressivo.
2. Blur e transparência são opcionais; modo econômico usa cores sólidas e sombras simples.
3. Launcher e menu carregam dados sob demanda.
4. Sem indexação global de arquivos por padrão.
5. Sessão gráfica deve iniciar mesmo sem aceleração 3D.

## Decisão pendente

Escolher compositor/servidor gráfico após identificar a GPU e testar DRM/KMS no i30. Wayland com compositor leve é preferível se os drivers funcionarem; Xorg com composição opcional será fallback de compatibilidade.