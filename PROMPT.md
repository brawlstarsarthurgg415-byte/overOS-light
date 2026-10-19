# PROMPT — OverOS Light Version (roda direto do pendrive, sem instalador)

## 1. Objetivo
Criar um kit que gera uma imagem de disco (.img) de um Linux bem leve. A imagem é gravada no pendrive (ou HD) com EtchDroid, Etcher ou dd. Depois de gravada, ela JÁ É o sistema: liga e usa, sem instalador.

## 2. Hardware alvo (notebook i30)
- Notebook de ~2008, BIOS antiga (boot legacy/MBR, não UEFI)
- CPU 64 bits com 2 núcleos
- RAM: ~730 MB (DDR2). A meta é usar menos de 250 MB parado
- Sem HD. O sistema roda do pendrive USB 2.0 (lento)
- Wi-Fi Broadcom (precisa de firmware b43) + porta de rede cabeada
- Tem webcam, microfone, caixinha de som e bateria para testar

## 3. Regras de projeto
1. Sem instalador: a imagem é gravada e pronto.
2. Não carregar o sistema inteiro na RAM. O sistema fica em partição ext4 no próprio pendrive.
3. Poupar o pendrive: montar com noatime, logs em tmpfs pequeno, swap compactado na RAM (zram).
4. Base: Debian estável, amd64, via debootstrap. Isso é mais realista do que "só kernel", porque navegador, áudio e Wi-Fi precisam de bibliotecas e drivers.
5. Interface leve: Xorg + Openbox + tint2. Nada de KDE (pesado demais para 730 MB).
6. Todo script em português simples, com comentários.
7. Segurança: o script de gravação nunca escolhe disco sozinho e pede confirmação digitando o nome do dispositivo.

## 4. O que vem dentro
- Navegador leve (NetSurf) + opção Firefox ESR com aviso de que é pesado
- Vídeo: mpv e yt-dlp (YouTube em 360p)
- Terminal com fastfetch, htop, btop, cmatrix
- Rede: NetworkManager, Wi-Fi com firmware b43, celular por USB (tethering)
- Áudio: ALSA + PulseAudio leve

## 5. Modos de boot (menu do GRUB)
- Normal: abre a interface
- Modo de teste: abre direto o menu "overos-testes"
- Modo seguro: nomodeset, só terminal
- Teste de RAM: memtest86+

## 6. Menu "overos-testes"
USB (listar dispositivos), webcam (mostrar imagem), microfone (gravar e tocar), caixinha de som (speaker-test), bateria (carga e estado), internet (ping, Wi-Fi), memória (memtester), temperatura (sensors), pendrive (velocidade de leitura/escrita).

## 7. Entregáveis
- build-overos-light.sh (gera a imagem; roda num PC Debian/Ubuntu, como root)
- gravar-pendrive.sh (grava com proteção)
- Arquivos de configuração e scripts do menu de testes
- README.md passo a passo, em português, para iniciante
- Lista de verificação para conferir no notebook de verdade

## 8. Verificação e honestidade
- Conferir a sintaxe de todos os scripts
- Revisar o kit contra este prompt, ponto a ponto
- Dizer claramente o que NÃO foi testado (a imagem não pode ser gerada nem testada neste ambiente, e o notebook real só o usuário testa)
- Limites a explicar: Google Chrome não cabe em 730 MB, e o tablet com Secure Boot fica fora desta versão
