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


## Interface de atividade estilo Claude Code

A pasta `ui/` contém uma prévia responsiva para site e futura incorporação em APK/WebView. Ela demonstra a composição da conversa com painel de atividade, linha do tempo, comandos e saídas. A demonstração é estática/simulada e não executa comandos reais.

O contrato de eventos está documentado em `docs/KELLY-ACTIVITY-STREAM.md`. A integração final deverá transmitir eventos persistentes do motor para site e APK via API autenticada com SSE ou WebSocket. O terminal continua textual e leve.

A preferência de "aceitar tudo" será limitada a aprovar em lote ações seguras já exibidas. Não haverá aprovação irrestrita para comandos destrutivos, privilegiados, instalações ou ações remotas.

## Família de modelos Kelly — estratégia de desenvolvimento

A Kelly será uma família de modelos e serviços especializados, não um único arquivo de pesos usado para tudo. O objetivo é criar identidade, comportamento, ferramentas, avaliação e personalização próprios. Não afirmar que um modelo foi treinado do zero quando a base vier de pesos abertos.

### Perfis planejados

- **Kelly Live:** modelo pequeno e quantizado para diálogo de baixa latência, roteamento de intenção, atualizações curtas de progresso e conversa por voz.
- **Kelly Medium:** prioridade máxima. Modelo principal para o Samsung Galaxy S23+, com capacidade geral, planejamento, raciocínio, uso de ferramentas e programação. Deve ser escolhido por benchmarks reais no aparelho, não apenas por tamanho ou marketing.
- **Kelly Lite:** perfil ainda menor para aparelhos limitados, tarefas simples e economia extrema.
- **Kelly Code:** especialista em engenharia de software, leitura de repositórios, edição, testes e depuração.
- **Kelly Vision:** componente multimodal para imagens e, quando houver suporte viável, documentos e câmera.
- **Kelly Pro:** perfil para computadores/servidores com mais memória e aceleração; não presumir que cabe no celular.

Os perfis podem compartilhar uma base compatível e receber adaptadores/ajustes especializados. Não é necessário manter todos carregados simultaneamente.

### Kelly Dual Intelligence — conversa rápida + execução profunda

A experiência padrão poderá combinar dois agentes:

1. **Kelly Live** mantém a conversa responsiva, recebe texto/voz, esclarece pedidos simples e informa estados reais.
2. **Orquestrador** classifica a solicitação, estima dificuldade/custo e decide se responde localmente ou cria uma tarefa para a Medium.
3. **Kelly Medium** recebe o pedido, o contexto relevante e as permissões; cria plano, usa ferramentas, executa etapas e testa o resultado.
4. **Canal de eventos** transmite progresso estruturado à Live: planejando, pesquisando, lendo arquivos, aguardando aprovação, executando, testando, concluído ou erro.
5. **Kelly Live** comunica o progresso e apresenta a resposta final validada pela Medium.

A Live não pode inventar progresso nem declarar uma operação concluída sem confirmação do orquestrador/ferramenta. Se a Medium estiver ocupada, descarregada ou indisponível, a Live deve dizer isso claramente, manter a tarefa em fila quando apropriado e permitir cancelar ou alterar o pedido.

### Contratos entre os agentes

- Contexto compartilhado por identificadores de conversa/tarefa e resumos versionados; não duplicar indiscriminadamente todo o histórico.
- Mensagens internas estruturadas com tipo, tarefa, estado, resultado e referências a arquivos; não depender de texto livre para controlar ferramentas.
- Uma única fonte de verdade para estado de execução, permissões e resultados.
- Cancelamento cooperativo, timeout, tentativas limitadas e recuperação após falha.
- Aprovação humana para ações destrutivas, acesso remoto, instalação de software e operações sensíveis.
- Separar o canal de conversa do canal de execução: fala e texto da Live não executam comandos por conta própria.
- Streaming de tokens/eventos para reduzir sensação de espera, sem sacrificar verificações.

### Manual operacional obrigatório da Kelly

Criar e manter um manual versionado que o agente deve consultar conforme a tarefa. O manual terá:

- Identidade, objetivos, limites e regras de segurança.
- Como inspecionar um repositório antes de editar.
- Como planejar, dividir tarefas e manter o Plan Mode.
- Como escolher ferramentas e linguagens; como pedir aprovação.
- Regras de edição segura: ler antes, mudanças pequenas, preservar compatibilidade e evitar sobrescrever trabalho do usuário.
- Como executar testes, interpretar falhas, corrigir e repetir com limite.
- Como lidar com rede indisponível, falta de memória, falta de espaço, dependências e modelos descarregados.
- Como registrar progresso, decisões, arquivos alterados e rollback.
- Como distinguir fatos verificados, hipóteses e ações ainda não realizadas.
- Exemplos de fluxos aprovados e testes de conformidade.

O manual orienta o agente, mas não substitui permissões impostas pelo código. Segurança crítica deve ser aplicada pelo orquestrador e pelas ferramentas, não apenas por instruções em prompt.

### Como desenvolver os modelos próprios

1. Medir hardware-alvo e definir limites de RAM, armazenamento, temperatura, bateria e latência.
2. Escolher uma base de pesos abertos cuja licença permita o uso pretendido e verificar idioma, contexto e suporte ao formato de inferência.
3. Criar conjuntos de dados próprios e licenciados: português brasileiro, instruções de ferramentas, programação, planejamento, segurança e recuperação de erros.
4. Fazer ajuste fino especializado (por exemplo, LoRA/QLoRA quando compatível) e quantização; manter registro da base, licença, dados, receita e versão.
5. Avaliar em tarefas fixas e reproduzíveis: precisão, alucinações, obediência a permissões, qualidade de código, taxa de conclusão, latência e consumo.
6. Testar no S23+ real, em condições de bateria/temperatura variadas. Só então escolher tamanho, quantização e runtime.
7. Manter possibilidade de voltar à versão anterior e comparar cada versão com o conjunto de regressão.

Treinar um modelo fundamental do zero não é o primeiro passo: exige escala de dados e computação muito maior. A estratégia inicial é construir modelos Kelly personalizados sobre bases abertas, com ajustes, ferramentas, avaliação e identidade próprios.

### Critérios específicos para o S23+

- Priorizar Kelly Medium, mas medir a memória disponível durante uso real do Android; não presumir que toda a RAM física esteja livre.
- Carregar a Medium sob demanda e descarregá-la quando necessário; evitar duas cópias grandes na memória.
- A Live pode usar um modelo menor, ou uma implementação compacta de roteamento/diálogo, e serviços nativos de fala.
- Medir latência do primeiro token, tokens por segundo, tempo de transcrição, tempo de síntese, aquecimento e consumo de bateria.
- Permitir modo de uma única IA quando o aparelho não conseguir manter Live + Medium sem pressão de memória.
- Voz em tempo real depende também de ASR/TTS, áudio e streaming; um modelo de texto pequeno sozinho não garante conversa por voz instantânea.

### Estado

A família de modelos, a arquitetura Dual Intelligence e o manual são especificações de projeto. Existe agora um primeiro pacote isolado `kelly_dual` com fila SQLite persistente, eventos, cancelamento cooperativo e recuperação de tarefas, coberto por testes automatizados. Ele ainda não está ligado ao motor Overcode v1.6, a endpoints de inferência, à interface, nem a clientes Android. Nenhum peso Kelly foi treinado ou baixado; ajuste fino, runtime móvel, seleção por benchmark no S23+ e pipeline de voz continuam pendentes.

## Direção visual inicial

- Tema escuro grafite, superfícies em camadas e acento ciano/violeta discreto.
- Painel de conversa como foco principal; barra lateral recolhível para conversas e ferramentas.
- Kelly com indicador animado de atividade em SVG/CSS, sem animações grandes em loop quando ociosa.
- Botões claros para microfone, enviar, interromper e configurações.
- SSH, terminal, plugins e conectores em área avançada, acessíveis sem ocupar a tela principal.
- Respeitar preferência de movimento reduzido e modo econômico de efeitos.
