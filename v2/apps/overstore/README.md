# OverStore

Interface original da loja de aplicativos da OverOS Lite V2. Implementada em HTML, CSS e JavaScript puros, sem framework, fontes remotas, analytics ou processos residentes.

## Estado desta entrega

- Interface responsiva com identidade visual OverOS.
- Pesquisa local e filtros por categoria.
- Cartões e detalhes dos aplicativos.
- Biblioteca local de aplicativos instalados (estado demonstrativo).
- Atalho Ctrl+K e suporte a teclado.
- Respeita prefers-reduced-motion.

**Limitação explícita:** esta versão é a camada visual e de navegação do catálogo. Ainda não instala programas, não consulta repositório remoto e não atualiza pacotes. O botão de detalhes informa que o pacote está planejado; não simula uma instalação concluída.

## Próxima integração

Conectar a uma API local do gerenciador de pacotes OverOS. A API deverá validar assinatura e hash, verificar arquitetura e dependências, pedir confirmação para operações privilegiadas, instalar em caminhos controlados e manter rollback. A interface não deve executar comandos arbitrários nem usar shell com texto fornecido pelo usuário.

## Executar para prévia

Abra `index.html` em um navegador moderno. Para integrar ao desktop, servir os arquivos localmente a partir da sessão gráfica; não requer conexão à internet para abrir a interface.
