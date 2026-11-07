# Aplicativos OverOS — base leve

Primeira camada de utilitários para BusyBox + shell POSIX, sem runtimes pesados ou serviços residentes.

## Disponíveis nesta etapa

- **Monitor do sistema** — lê /proc e mostra memória, uptime, kernel e armazenamento.
- **Arquivos** — lista conteúdo e orienta operações básicas no terminal. Ainda não é um gerenciador gráfico.
- **Configurações** — preferências persistentes; permite alterar tema, cor de destaque, animações, transparência e posição do painel.
- **Terminal** — abre o shell disponível no sistema.

## Uso

- overosctl
- overosctl status
- overosctl files /caminho
- overosctl settings
- overosctl settings set theme dark
- overosctl settings set accent violet

Configurações ficam em ~/.config/overos/settings.conf (no protótipo inicial, usuário root).

## Próxima camada gráfica

A experiência visual escolhida é Epic/Epik Shell. Os aplicativos gráficos serão implementados como processos sob demanda, com componentes compartilhados para tema, pop-ups e navegação. GTK/AGS/Astal e compositor só serão escolhidos após medir compatibilidade e consumo no i30.

**Estado honesto:** estes utilitários são funcionais em terminal. Ainda não são aplicativos gráficos nem o desktop Epic-like; a imagem atual não deve ser apresentada como se já os contivesse.
