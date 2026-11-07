# Primeiro início do OverOS

O assistente local solicita nome de usuário, avatar predefinido e modo de sessão (automático, terminal ou interface gráfica). Não solicita nem transmite credenciais. É executado como root pelo init, portanto não pede `sudo` dentro do próprio assistente.

**Limitação atual:** a imagem experimental ainda usa initramfs sem rootfs persistente e sem gestor completo de contas. O assistente salva as escolhas no ambiente atual, mas a persistência entre reinicializações e a criação segura da conta só ficam completas quando a partição de dados e o sistema de autenticação forem integrados. Não armazenar senha em texto simples.
