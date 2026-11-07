# Catálogo de aplicativos — critérios V2

Nenhum navegador será escolhido só pelo nome ou por ser "leve" no anúncio. Cada candidato precisa passar por benchmark no i30 (RAM em idle, RAM com 3 abas, CPU, vídeo, TLS, JavaScript, CSS moderno e estabilidade).

| Categoria | Direção | Critério |
|---|---|---|
| Navegador principal | Comparar Firefox e Chromium em perfil enxuto; avaliar QtWebEngine/WebKitGTK se a memória permitir | Abrir sites atuais, login, vídeo e páginas pesadas sem travar o sistema |
| Navegador de emergência | NetSurf pode ser mantido como alternativa simples | Não será apresentado como compatível com toda a web moderna |
| Arquivos | Gerenciador GTK/Qt pequeno ou shell próprio | Navegação local, USB, copiar/mover, sem indexador residente |
| Terminal | Terminal leve com shell BusyBox/musl | Inicialização rápida e fonte legível |
| Mídia | mpv com aceleração quando disponível | Testar codecs e consumo de CPU |
| PDF | Leitor leve | Abrir documentos grandes sem alto uso de RAM |
| Diagnóstico | Ferramentas próprias + utilitários pequenos | Tudo acessível também pelo painel web |

## Restrição conhecida

Se a medição confirmar aproximadamente 700–800 MB de RAM, navegadores modernos e páginas atuais serão o maior gargalo. A V2 deve usar zram, limitar serviços residentes e oferecer modo de baixa memória, mas não prometer desempenho impossível.