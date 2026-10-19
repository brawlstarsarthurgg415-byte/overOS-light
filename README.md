# OverOS Light Version

Um Linux bem leve, feito para o notebook **i30** (2008, ~730 MB de RAM, sem HD).
Você grava no pendrive e **ele já é o sistema**: não tem instalador.

> Base: Debian (64 bits). Interface: Openbox. Sistema rodando direto do pendrive (não é carregado na RAM).

## Sistemas suportados

| Máquina | Situação |
|---|---|
| Notebook i30 (BIOS antiga, 64 bits) | Alvo principal desta versão. **Ainda não testado.** |
| PC de mesa com BIOS/Legacy ou "CSM" ligado | Deve funcionar, não testado |
| PC/tablet só UEFI com Secure Boot (ex.: o tablet com Windows 10) | **Não suportado** nesta versão |

## O que vem dentro

- Navegador leve: NetSurf. Firefox ESR também vem, mas é pesado e pode travar com 730 MB.
  **Google Chrome não cabe nessa RAM.**
- Vídeo: mpv (e yt-dlp para YouTube em 360p)
- Terminal com `fastfetch`, `htop`, `btop`, `cmatrix`
- Rede: NetworkManager (`nmtui` para conectar), firmware Wi-Fi Broadcom
- Menu de testes: USB, webcam, microfone, caixinha, bateria, internet, RAM, temperatura, velocidade do pendrive

## Usuário e senha

- usuário: `overos`
- senha: `overos` (troque com o comando `passwd`)

## Passo 1: gerar a imagem

Tem dois caminhos.

### Caminho A: só com o celular (Termux + GitHub)

O Termux **não** consegue gerar a imagem (o Android bloqueia `mount` e `losetup`). Em vez disso,
o GitHub gera para você, na nuvem (de graça em repositório público).

No Termux, fora do proot (digite `exit` se estiver dentro do Ubuntu):

```bash
pkg update
pkg install unzip git gh xz-utils
termux-setup-storage
cp ~/storage/downloads/overos-light.zip ~
unzip overos-light.zip
cd overos-light
gh auth login
git init -b main
git add -A
git commit -m "OverOS Light"
gh repo create overos-light --public --source=. --push
gh workflow run build.yml
gh run watch
```

Quando terminar (leva de 15 a 40 minutos), baixe a imagem:

```bash
gh release download --pattern 'overos-light.img.xz'
xz -d overos-light.img.xz
```

O arquivo `overos-light.img` fica na pasta. Grave com o EtchDroid (modo imagem de disco).
Se o `git commit` pedir nome e e-mail, rode antes:
`git config --global user.name "seu nome"` e `git config --global user.email "seu@email"`.

O GitHub também tenta ligar a imagem num emulador (QEMU) e guarda o log do boot
(aba Actions > o último "Gerar OverOS Light" > log-boot). Isso é só informativo.

### Caminho B: PC com Linux

Precisa de **um PC com Linux** (Debian, Ubuntu ou Mint; o Mint ao vivo no PC de mesa serve) e internet.
Não funciona no Windows.

```bash
sudo apt install debootstrap parted rsync e2fsprogs grub-pc-bin grub-common udev debian-archive-keyring
sudo ./build-overos-light.sh
```

Demora bastante (baixa uns 1 a 2 GB). No final aparece `overos-light.img`.
Se algum pacote opcional não existir, o script só avisa e continua (veja `work/avisos.txt`).

## Passo 2: gravar no pendrive

- Pelo celular: EtchDroid, escolhendo a opção de **imagem de disco (raw/dd)**.
- Pelo PC: Etcher, ou o script protegido:
  ```bash
  sudo ./gravar-pendrive.sh overos-light.img /dev/sdX
  ```
  (veja o nome do pendrive com `lsblk`; **tudo nele será apagado**)

A imagem tem 4 GB. No primeiro boot o sistema estica sozinho até ocupar todo o pendrive.
Use um pendrive de **8 GB ou mais**.

## Passo 3: ligar no i30

1. Deixe a **fonte LG de 19 V / 4,74 A** ligada direto na tomada, e a **ventoinha no máximo**.
2. Espete o pendrive, ligue o notebook e aperte **F12** (ou **Esc**) para escolher o boot pelo pendrive.
3. No menu do GRUB:
   - **Normal**: abre a interface (clique com o botão direito na tela para ver o menu).
   - **Modo de teste**: abre direto o menu de testes.
   - **Modo seguro**: só terminal, sem vídeo acelerado. Use se a tela não aparecer.
   - **Teste de memória RAM**: memtest86+ (deixe uma passada inteira).

## Problemas comuns

- **Sem imagem / tela preta:** use o *Modo seguro*.
- **Sem Wi-Fi:** use cabo de rede ou o celular com tethering USB. Depois, `nmtui`.
- **Travando:** feche o Firefox e use o NetSurf; confira a RAM no *Modo de teste*.
- **Notebook desliga sozinho:** quase sempre a fonte (precisa de 19 V e pelo menos 3,5 A).

## O que ainda não foi testado

Este kit foi escrito sem poder gerar nem ligar a imagem. Os scripts tiveram a sintaxe conferida,
mas **a imagem em si nunca foi construída nem iniciada**. Veja `CHECKLIST-NOTEBOOK.md`
para conferir o que funcionou de verdade no i30.

## Limitações conhecidas

- Atualizar o kernel dentro do sistema funciona (o GRUB usa `/vmlinuz` e `/initrd.img`), mas não há instalador nem atualização do GRUB.
- A entrada de teste de RAM do GRUB (memtest86+) pode precisar de ajuste; o teste `memtester` do menu funciona independentemente.
- Wi-Fi Broadcom: o firmware `b43` é baixado na hora de gerar a imagem. Se falhar, aparece em `work/avisos.txt`.
