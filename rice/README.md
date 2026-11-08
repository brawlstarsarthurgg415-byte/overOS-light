# OverOS Arch Rice — primeira base

Este diretório inicia a nova experiência visual do OverOS, separada das versões antigas.

## Direção visual

- Tema escuro azul-noite com acento ciano e violeta discreto.
- Painel simples, menu de aplicativos, relógio e indicadores de sistema.
- Animações desligadas por padrão; sem papel de parede em vídeo ou blur pesado.
- Interface utilizável em resoluções antigas e telas pequenas.
- Sem Xorg, conforme o objetivo do projeto.

## Restrição de hardware

O Intelbras i30 parece usar gráficos integrados SiS Mirage 3+ / chipset SiS 672MX, mas isso ainda não foi confirmado no próprio equipamento. Por isso, **nenhum compositor Wayland foi declarado compatível ainda**.

Astal/Quickshell é uma camada de shell/widgets, não substitui o compositor nem o driver de vídeo. Quickshell usa QtQuick e depende de bibliotecas gráficas e Wayland; não é a primeira escolha para um equipamento com cerca de 730 MB de RAM. A implementação final deve esperar a verificação de DRM/KMS e do driver da GPU.

## Estratégia em etapas

1. Definir a identidade visual e os componentes.
2. Verificar a GPU e os nós DRM disponíveis no i30.
3. Testar uma sessão Wayland mínima sem efeitos.
4. Só depois adicionar painel, menu e widgets.
5. Manter um modo de recuperação em terminal se a interface falhar.

## Estado atual

Este é o início da especificação visual. Ainda não é um sistema inicializável nem uma configuração testada no i30.
