# Boot V2

Alvo inicial: BIOS Legacy/CSM e boot USB direto, porque esse é o cenário histórico do i30. O layout definitivo será fechado depois de inventariar o firmware e os controladores do notebook.

A imagem será raw `.img`, com tabela de partição própria, partição de boot e rootfs OverOS. Não haverá assistente de instalação: gravar a imagem no USB e iniciar.

O kernel deve ter suporte embutido (não apenas módulo) ao controlador USB de armazenamento e ao sistema de arquivos da raiz, para não depender de um initramfs grande. O initramfs poderá ser adicionado para detecção mais sofisticada e recuperação.

`grub.cfg.example` é apenas um esboço de configuração, ainda não usado por um build.