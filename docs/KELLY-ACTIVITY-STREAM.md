# Protocolo de atividade visual da Kelly

## Objetivo

Site e APK devem oferecer uma experiência de agente transparente: a conversa permanece disponível enquanto Kelly Code trabalha, e cada ação aparece numa linha do tempo com estado, comando, saída, arquivos e aprovações. O terminal continua sendo uma interface textual enxuta.

## Eventos

O motor deve emitir eventos JSON versionados. O painel e o APK consomem o mesmo contrato.

Campos comuns:
- `schema_version`: versão inteira do contrato.
- `task_id`: identificador persistente da tarefa.
- `event_id`: identificador único/idempotente.
- `sequence`: número crescente por tarefa.
- `timestamp`: data/hora ISO 8601 UTC.
- `type`: tipo do evento.
- `status`: estado atual da tarefa.
- `message`: explicação curta em português.

Tipos recomendados:
- `task.queued`, `task.started`, `task.progress`
- `plan.created`, `plan.updated`
- `tool.started`, `tool.output`, `tool.completed`, `tool.failed`
- `approval.required`, `approval.resolved`
- `file.changed`
- `test.started`, `test.completed`
- `task.completed`, `task.failed`, `task.cancelled`

Exemplo de comando:
```json
{
  "schema_version": 1,
  "task_id": "task-id",
  "event_id": "event-id",
  "sequence": 12,
  "timestamp": "2026-11-07T12:00:00Z",
  "type": "tool.started",
  "status": "working",
  "message": "Verificando os arquivos do projeto",
  "tool": {
    "name": "shell",
    "label": "Terminal",
    "command": "find . -maxdepth 2 -type f",
    "cwd": "/workspace/projeto"
  }
}
```

A saída deve chegar em eventos separados `tool.output`, com `stream` (`stdout` ou `stderr`), trecho de texto e sequência. Não truncar silenciosamente: limitar o tamanho exibido no cliente, mas permitir expandir/consultar logs completos com paginação.

## Interface web e APK

Ambos exibem:
- Mensagens da Kelly Light.
- Cartão de tarefa com agente responsável e estado.
- Plano/checklist atualizado.
- Linha do tempo de cada ferramenta, com comando literal, diretório de trabalho, duração e saída.
- Saída stdout e stderr visualmente distintas.
- Arquivos criados/alterados com diff consultável.
- Testes executados e seus resultados.
- Botões de aprovar, rejeitar e cancelar quando aplicável.
- Conversa continua utilizável enquanto o painel de atividade atualiza.

O layout deve se adaptar: no desktop, conversa e atividade lado a lado; no celular, atividade em painel/aba expansível sem esconder a conversa.

## Aprovações e "Aceitar tudo"

Oferecer uma preferência chamada **Aceitar automaticamente ações seguras**. Não implementar uma autorização irrestrita para qualquer comando.

- Ações de baixo risco, somente leitura e previamente allowlisted podem ser aprovadas automaticamente.
- Instalação, elevação de privilégio, exclusão, sobrescrita relevante, alteração de serviço/firewall, acesso remoto e comandos fora do workspace sempre exigem aprovação explícita.
- A aprovação deve ser limitada à sessão ou tarefa e mostrar exatamente o escopo.
- Um botão "Aceitar tudo" pode aprovar em lote somente as ações seguras pendentes. Nunca pode ignorar bloqueios de segurança ou aprovar comandos futuros sem escopo.
- O backend é a autoridade final; esconder um botão no cliente não é controle de segurança.

## Transporte e segurança

- Preferir API local autenticada com streaming por Server-Sent Events (SSE) ou WebSocket.
- Em localhost, usar token de sessão protegido; para acesso pela rede, autenticação forte e TLS.
- Não expor chaves de modelos no JavaScript nem no APK.
- Conteúdo de saída é texto não confiável: renderizar como texto, não como HTML executável.
- Reconectar usando cursor/sequence e buscar eventos perdidos após queda de rede.
- Persistir eventos no servidor para reabrir uma tarefa e reconstruir a linha do tempo.
- Não transmitir segredos em stdout; mascarar tokens e credenciais nos logs.

## Terminal

O terminal atual permanece suportado. A visão detalhada de eventos é uma vantagem do site e do APK; o terminal recebe apenas mensagens textuais concisas, comandos/saídas quando útil e prompts de confirmação compatíveis com o fluxo atual.

## Estado de implementação

A pasta `ui/` contém uma prévia responsiva estática para validar a disposição visual. A prévia simula eventos e comandos e **não executa comandos reais**. O protocolo acima é o contrato planejado; API SSE/WebSocket, autenticação, APK e integração com o motor ainda precisam ser implementados e testados.
