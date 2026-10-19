# Lista de verificação no notebook i30

Marque conforme for testando e me diga o que falhou (tire foto da tela).

## Antes de ligar
- [ ] Fonte LG 19 V / 4,74 A direto na tomada (sem extensão)
- [ ] Ventoinha no máximo (USB direto no carregador de 5 V)
- [ ] Só o pendrive do OverOS espetado

## Boot
- [ ] O notebook oferece boot pelo pendrive (F12 ou Esc)
- [ ] Aparece o menu do GRUB com 4 entradas
- [ ] Normal: chega na interface (tela azul-escura + barra embaixo)
- [ ] Modo seguro: chega no terminal
- [ ] Teste de RAM (memtest86+) abre e roda

## Primeiro boot
- [ ] `df -h /` mostra o tamanho do pendrive inteiro (o sistema esticou)
- [ ] `free -h` mostra ~700 MB e swap zram
- [ ] Parado, o uso de RAM fica abaixo de ~250 MB

## Menu de testes (`overos-testes`)
- [ ] USB
- [ ] Webcam
- [ ] Microfone
- [ ] Caixinha de som
- [ ] Bateria
- [ ] Internet (cabo)
- [ ] Wi-Fi (placa Broadcom)
- [ ] Memória RAM: sem nenhum "FAILURE"
- [ ] Temperatura
- [ ] Velocidade do pendrive

## Programas
- [ ] NetSurf abre uma página
- [ ] mpv toca um vídeo
- [ ] `fastfetch` e `btop` abrem
- [ ] Firefox abre (se travar, é esperado com essa RAM)

## Desligar
- [ ] Menu > Desligar apaga o notebook sem erro
