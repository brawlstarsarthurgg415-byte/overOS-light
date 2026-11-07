# Decisões iniciais

| Tema | Decisão provisória | Condição para fechar |
|---|---|---|
| Base | Kernel Linux + userland próprio | Validar cadeia de boot e toolchain |
| libc | musl | Medir compatibilidade dos apps escolhidos |
| Utilitários | BusyBox como toolkit, não como distro | Selecionar applets necessários |
| PID 1 | `overos-init` próprio | Testar sinais, reaping e falha da sessão |
| Desktop | Shell OverOS com visual Plasma/Aero | Inventário de GPU e teste de composição |
| Navegador | Ainda não definido; NetSurf não é padrão | Benchmark real no i30 |
| Painel web | Serviço local autenticado + API allowlist | Revisão de segurança e teste de rede |
| Imagem | `.img` raw, sem instalador | Teste de boot em USB físico |