# Plano técnico — Overcode / Kelly 2.6

## Objetivo

Evoluir o motor existente `overia-code` sem remover nem quebrar o modo terminal. Python será o cérebro e a camada de integração, não a linguagem usada para desenhar toda a interface. A interface gráfica, o painel web e o cliente Android serão clientes adicionais do mesmo motor.

## Estado real encontrado no repositório

- O instalador atual é `instalar-overia-code-v1_6.sh`.
- O motor é Python e usa a biblioteca padrão.
- Há seleção entre modelos GGUF via `llama-server` e Gemini pela API compatível com OpenAI.
- O agente executa comandos Bash em etapas, lê as saídas e pede confirmação para instalações e comandos perigosos.
- As funções de conversa, anexos, projetos, atualização, coração e diário já existem.
- O OverOS é direcionado a um i30 antigo com aproximadamente 730 MB de RAM; por isso, o painel não pode iniciar automaticamente nem consumir memória em segundo plano sem necessidade.

## Regras para a migração

1. **Compatibilidade:** manter `overia-code`, os comandos existentes e o fluxo de terminal.
2. **Sem reescrita destrutiva:** desenvolver em módulos novos e integrar por etapas.
3. **Leveza:** painel e servidor web opcionais; nada de Electron ou navegador embutido pesado no i30.
4. **Segurança:** API local vinculada a localhost por padrão; autenticação para acesso pela rede; confirmação humana para ações destrutivas; nunca expor a chave Gemini no navegador.
5. **Voz opcional:** entrada por microfone e fala por TTS devem ser capacidades dos clientes, não dependências obrigatórias do motor.
6. **Transparência:** cada recurso deve ter teste automatizado e ser marcado como não testado no hardware quando aplicável.

## Arquitetura planejada

```text
                    Kelly / Overcode
                           |
             Motor Python existente (v1.6+)
                           |
             API local opcional e autenticada
                 /          |           \
          Terminal       Painel web     Android
                           |               |
                    Web Speech / TTS   SpeechRecognizer /
                                       Android TextToSpeech
```

O motor continua responsável por modelos, contexto, memória, planejamento, execução de ferramentas e permissões. Os clientes só enviam mensagens, recebem eventos de estado e apresentam controles.

## Etapas de implementação

### Etapa 1 — estabilizar o motor
- Criar testes de sintaxe e testes unitários para extração de comandos, confirmação de risco, histórico e persistência.
- Separar configuração e estado de sessão sem alterar os comandos atuais.
- Documentar os comandos e o protocolo interno.

### Etapa 2 — API local
- Criar serviço opcional que conversa com o motor por uma interface interna.
- Eventos: mensagem recebida, pensando, pesquisando, executando, aguardando confirmação, resultado, erro e concluído.
- Bind em `127.0.0.1` por padrão.
- Acesso remoto somente com autenticação explícita e transporte protegido.

### Etapa 3 — painel web leve e bonito
- Criar a interface com HTML5, CSS3 e TypeScript; Svelte compilado pode organizar os componentes.
- Criar identidade visual própria da Kelly: tema escuro elegante, acentos luminosos, tipografia legível, ícones SVG e microanimações suaves.
- Criar conversa em layout moderno, lista de conversas, seletor de modelos, configurações, estados visuais (pensando, pesquisando, executando, aguardando confirmação, concluído e erro).
- Adaptar o layout para celular e desktop.
- Terminal e SSH ficam em uma seção avançada; não são removidos.
- Evitar bibliotecas grandes e efeitos pesados; oferecer modo econômico para o i30.

### Etapa 4 — voz
- Web: listar vozes disponíveis no navegador, selecionar idioma/voz e velocidade; reconhecer fala apenas quando o navegador oferecer suporte e pedir permissão.
- Android: usar APIs nativas `SpeechRecognizer` e `TextToSpeech`; listar vozes instaladas no dispositivo.
- Voz desligada por padrão e configurável por cliente.
- Não prometer interceptar o botão físico de energia: isso depende das permissões e das opções de assistente do Android/Samsung.

### Etapa 5 — cliente Android
- Aplicativo nativo em Kotlin, com visual coerente com o painel web e controles compactos.
- Cliente compacto que usa a API autenticada.
- Integração com o papel de assistente do Android quando disponível; não tentar capturar arbitrariamente o botão Power.
- Compilar e testar o APK antes de declarar pronto.

## Critérios de aceite

- O comando antigo `overia-code` continua iniciando o modo terminal.
- SSH e terminal continuam disponíveis.
- Desligar o painel não encerra o motor nem altera o sistema base.
- A chave Gemini nunca aparece no HTML, JavaScript ou logs do cliente.
- Ações perigosas continuam exigindo confirmação.
- Voz pode ser ligada/desligada e as opções apresentadas correspondem às vozes realmente instaladas.
- Testes automatizados passam; testes no i30 e no Samsung são identificados separadamente.

## Limitações conhecidas

Este documento registra a arquitetura e a sequência de trabalho. Ele não significa que API, painel, APK, voz ou integração com o botão físico já estejam implementados ou testados.

## Direção visual inicial

- Tema escuro grafite, superfícies em camadas e acento ciano/violeta discreto.
- Painel de conversa como foco principal; barra lateral recolhível para conversas e ferramentas.
- Kelly com indicador animado de atividade em SVG/CSS, sem animações grandes em loop quando ociosa.
- Botões claros para microfone, enviar, interromper e configurações.
- SSH, terminal, plugins e conectores em área avançada, acessíveis sem ocupar a tela principal.
- Respeitar preferência de movimento reduzido e modo econômico de efeitos.
