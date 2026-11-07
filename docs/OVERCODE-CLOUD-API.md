# OverCode Cloud API

A Kelly API serve a interface web/app e conecta a conversa a um provedor de IA configurado no servidor.

## Recursos desta versão

- `GET /api/health`: estado do serviço e indicação de executor de código.
- `GET /api/providers`: lê apenas nome do provedor/modelo e se existe chave; nunca devolve a chave.
- `POST /api/providers`: salva provedor, modelo, URL compatível e API key no servidor.
- `POST /api/providers/test`: testa a credencial com uma chamada curta.
- `POST /api/chat`: conversa com Gemini ou API compatível com OpenAI.
- `POST /api/tasks` e SSE `/api/tasks/{id}/events`: fila de tarefas do motor Kelly Code.

## Provedores

- Gemini: selecione Google Gemini e informe um modelo, por exemplo `gemini-2.5-flash`.
- OpenAI e compatíveis: selecione OpenAI / compatível, informe o modelo e a URL base HTTPS. Exemplos de compatibilidade incluem serviços que implementam `/chat/completions`.

A API key é armazenada em `~/.overos/kelly/providers.json` com permissão `0600`. Ela nunca é retornada ao navegador nem deve ser colocada no APK, no JavaScript ou no Git.

## Inicialização local

Gere um token de acesso forte:

```bash
python3 -c 'import secrets; print(secrets.token_urlsafe(48))'
```

Configure as variáveis no serviço (não publique o valor do token):

- `KELLY_API_TOKEN`: token aleatório com pelo menos 32 caracteres.
- `KELLY_API_HOST=127.0.0.1`
- `KELLY_API_PORT=8765`
- `OVEROS_PROJECT_ROOT=/caminho/para/projetos`
- `KELLY_EXECUTOR=modulo:funcao` quando o adaptador real da Kelly Code estiver instalado.

Execute com `python3 -m kelly_dual.api` a partir da raiz do repositório.

A conversa pode iniciar sem o executor de código; nesse caso, `/api/health` informa que ele não está configurado e as tarefas de código não devem ser consideradas disponíveis.

## Publicação na internet

A API embutida usa HTTP simples e deve permanecer em localhost. Publique o site e faça proxy de `/api/` por Nginx/Caddy com HTTPS e certificado válido. Não exponha diretamente a porta 8765 na internet. Use firewall, limite de requisições no proxy, autenticação forte e monitore o uso/custos do provedor.

Para um APK, configure o endereço HTTPS do mesmo serviço como origem da interface. A chave do provedor continua exclusivamente no servidor. Não empacote uma API key dentro do APK.

## Limites conhecidos

Este commit entrega a camada de provedores, chat HTTP, configuração e interface. A execução real de tarefas de programação ainda depende de um adaptador `KELLY_EXECUTOR` conectado ao motor OverCode e das regras de aprovação. A API não inventa execução quando esse adaptador está ausente.
