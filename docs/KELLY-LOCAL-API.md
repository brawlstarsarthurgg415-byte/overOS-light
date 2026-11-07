# API local da Kelly

A interface web/APK pode consumir esta API local. O serviço usa apenas a biblioteca
padrão do Python e fica limitado a `127.0.0.1` por segurança.

## Estado desta integração

- API HTTP e fluxo SSE implementados.
- Tarefas persistidas pelo `TaskCoordinator`.
- Autenticação Bearer obrigatória para tarefas, estado e eventos.
- Cancelamento cooperativo por `DELETE`.
- Diretórios limitados a `OVEROS_PROJECT_ROOT`.
- **O motor Overcode ainda precisa fornecer um adaptador executor.** Não há
  execução de comandos por padrão; sem adaptador, o servidor não inicia.
- A API não contorna as confirmações de segurança do motor existente.

## Iniciar

Na raiz do repositório:

```bash
export KELLY_API_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
export OVEROS_PROJECT_ROOT="$PWD"
export KELLY_EXECUTOR="meu_adaptador:executar"
python3 -m kelly_dual.api
```

O módulo `meu_adaptador` deve estar no `PYTHONPATH` e exportar uma função
com a assinatura:

```python
def executar(task, emit, cancelled):
    emit("progress", "Lendo os arquivos", {"step": 1})
    # Delegue ao motor Overcode existente e às suas regras de aprovação.
    # Para comandos, emita tool_started/tool_output/tool_completed com os
    # campos command, stdout, stderr e exit_code.
    return "Resumo final"
```

Não implemente o adaptador com `shell=True` ou execução irrestrita. Ele deve
reutilizar o mecanismo de aprovação do Overcode e emitir eventos de aprovação
antes de ações que precisem de autorização.

## Rotas

- `GET /api/health` — estado básico, sem segredo.
- `POST /api/tasks` — cria tarefa; JSON: `{"prompt":"...","project_dir":"."}`.
- `GET /api/tasks/{id}` — estado e resultado.
- `GET /api/tasks/{id}/events` — Server-Sent Events; aceita `Last-Event-ID`.
- `DELETE /api/tasks/{id}` — solicita cancelamento cooperativo.

Todas as rotas, exceto health, exigem `Authorization: Bearer TOKEN`.
A API não habilita CORS e não deve ser exposta à rede nesta versão.
