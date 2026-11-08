/*
 * OverOS Shell — interface framebuffer nativa, sem X11/Wayland.
 * Protótipo gráfico real: desenha diretamente em /dev/fb0 e recebe teclas
 * no terminal virtual. Não depende de navegador nem de compositor 3D.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/fb.h>
#include <linux/kd.h>
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <termios.h>
#include <unistd.h>

static int fb=-1, tty=-1, W=0,H=0,bpp=0;
static long mapsz=0;
static uint8_t *mem, *backbuf;
static struct fb_var_screeninfo vi;
static struct fb_fix_screeninfo fi;
static struct termios oldt;
static int old_graphics=0, page=0, selected=0;

static uint32_t rgb(uint8_t r,uint8_t g,uint8_t b) {
  uint32_t v=0;
  v |= ((uint32_t)r >> (8-vi.red.length)) << vi.red.offset;
  v |= ((uint32_t)g >> (8-vi.green.length)) << vi.green.offset;
  v |= ((uint32_t)b >> (8-vi.blue.length)) << vi.blue.offset;
  if(vi.transp.length) v |= ((1u<<vi.transp.length)-1u)<<vi.transp.offset;
  return v;
}
static void pixel(int x,int y,uint32_t c) {
  if(x<0||y<0||x>=W||y>=H) return;
  long o=(long)y*fi.line_length+(long)x*(bpp/8);
  if(o<0||o+(bpp/8)>mapsz||!backbuf) return;
  if(bpp==32) *(uint32_t*)(backbuf+o)=c;
  else if(bpp==24) { backbuf[o]=c&255; backbuf[o+1]=(c>>8)&255; backbuf[o+2]=(c>>16)&255; }
  else if(bpp==16) *(uint16_t*)(backbuf+o)=(uint16_t)c;
}
static void rect(int x,int y,int w,int h,uint32_t c) {
  if(w<1||h<1)return;
  for(int yy=y;yy<y+h;yy++) for(int xx=x;xx<x+w;xx++) pixel(xx,yy,c);
}
static void roundrect(int x,int y,int w,int h,int r,uint32_t c) {
  for(int yy=0;yy<h;yy++) for(int xx=0;xx<w;xx++) {
    int dx=xx<r?r-xx-1:(xx>=w-r?xx-(w-r):0);
    int dy=yy<r?r-yy-1:(yy>=h-r?yy-(h-r):0);
    if((xx>=r&&xx<w-r)||(yy>=r&&yy<h-r)||dx*dx+dy*dy<=r*r)
      pixel(x+xx,y+yy,c);
  }
}
static const uint8_t *glyph(char c) {
  static const uint8_t blank[7]={0};
  static const uint8_t f[36][7]={
  {14,17,17,31,17,17,17},{30,17,17,30,17,17,30},{14,17,16,16,16,17,14},
  {30,17,17,17,17,17,30},{31,16,16,30,16,16,31},{31,16,16,30,16,16,16},
  {14,17,16,23,17,17,15},{17,17,17,31,17,17,17},{14,4,4,4,4,4,14},
  {7,2,2,2,18,18,12},{17,18,20,24,20,18,17},{16,16,16,16,16,16,31},
  {17,27,21,21,17,17,17},{17,25,21,19,17,17,17},{14,17,17,17,17,17,14},
  {30,17,17,30,16,16,16},{14,17,17,17,21,18,13},{30,17,17,30,20,18,17},
  {15,16,16,14,1,1,30},{31,4,4,4,4,4,4},{17,17,17,17,17,17,14},
  {17,17,17,17,17,10,4},{17,17,17,21,21,21,10},{17,17,10,4,10,17,17},
  {17,17,10,4,4,4,4},{31,1,2,4,8,16,31},
  {14,17,19,21,25,17,14},{4,12,4,4,4,4,14},{14,17,1,2,4,8,31},
  {30,1,1,14,1,1,30},{2,6,10,18,31,2,2},{31,16,16,30,1,1,30},
  {14,16,16,30,17,17,14},{31,1,2,4,8,8,8},{14,17,17,14,17,17,14},
  {14,17,17,15,1,1,14}
  };
  if(c>='a'&&c<='z') c-=32;
  if(c>='A'&&c<='Z')return f[c-'A'];
  if(c>='0'&&c<='9')return f[26+c-'0'];
  return blank;
}
static void text(int x,int y,const char *s,int scale,uint32_t color) {
  for(;*s;s++,x+=6*scale) {
    const uint8_t *g=glyph(*s);
    for(int yy=0;yy<7;yy++) for(int xx=0;xx<5;xx++)
      if(g[yy]&(1<<(4-xx))) rect(x+xx*scale,y+yy*scale,scale,scale,color);
  }
}
static void present(void) { if(mem&&backbuf&&mapsz>0) memcpy(mem,backbuf,(size_t)mapsz); }
static void cleanup(void) {
  if(tty>=0) { tcsetattr(tty,TCSAFLUSH,&oldt); if(old_graphics) ioctl(tty,KDSETMODE,KD_TEXT); }
  free(backbuf); backbuf=NULL;
  if(mem&&mem!=MAP_FAILED) munmap(mem,mapsz);
  if(fb>=0)close(fb); if(tty>=0)close(tty);
}
static void draw(void) {
  uint32_t bg=rgb(8,12,24), panel=rgb(17,25,43), card=rgb(25,37,60);
  uint32_t white=rgb(235,243,255), muted=rgb(135,157,187), blue=rgb(48,139,255);
  rect(0,0,W,H,bg);
  for(int y=0;y<H;y+=1) { int v=18+(y*14/(H?H:1)); rect(0,y,W,1,rgb(7,v,25+v)); }
  roundrect(18,16,W-36,48,14,panel);
  roundrect(32,28,20,20,10,blue); text(60,31,"OVEROS",2,white);
  text(W-210,34,"LITE V2",1,muted);
  int side= W<700?150:190;
  roundrect(18,78,side,H-96,16,panel);
  text(38,100,"WORKSPACE",1,muted);
  const char *nav[]={"HOME","APPS","FILES","SETTINGS","SYSTEM"};
  for(int i=0;i<5;i++) {
    int yy=130+i*48;
    if(page==i) roundrect(28,yy-9,side-20,36,9,rgb(34,75,126));
    roundrect(40,yy,8,8,4,i==page?blue:muted);
    text(60,yy,nav[i],1,i==page?white:muted);
  }
  int x=side+42, cw=W-x-38;
  text(x,98,page==0?"WELCOME TO OVEROS":nav[page],2,white);
  text(x,126,"YOUR COMPUTER, YOUR WAY",1,muted);
  int gap=14, cardw=(cw-gap)/2; if(cardw<120)cardw=cw;
  int y=164, ch=112;
  const char *titles[]={"OVER STORE","OVER BROWSER","OVER FILES","OVER SETTINGS"};
  const char *subs[]={"Discover your apps","Explore the web","Your local documents","Make it yours"};
  for(int i=0;i<4;i++) {
    int xx=x+(i%2)*(cardw+gap), yy=y+(i/2)*(ch+gap);
    roundrect(xx,yy,cardw,ch,14,card);
    roundrect(xx+14,yy+14,34,34,10,i==selected?blue:rgb(45,74,111));
    text(xx+62,yy+20,titles[i],1,white);
    text(xx+62,yy+42,subs[i],1,muted);
    text(xx+16,yy+78,i==selected?"SELECTED":"OPEN WITH ENTER",1,i==selected?blue:muted);
  }
  if(page==3) text(x,H-88,"P  CONNECT MEU OVEROS",1,rgb(75,213,226));
  int dockw=260; if(dockw>W-40)dockw=W-40;
  roundrect((W-dockw)/2,H-58,dockw,42,14,panel);
  text((W-dockw)/2+18,H-43,"1 HOME   2 APPS   3 FILES   4 SETTINGS",1,white);
  text(22,H-20,"ARROWS: MOVE    ENTER: OPEN    Q: EXIT",1,muted);
  present();
}
static void draw_setup(int stage,const char *username,int mode,int pair) {
  uint32_t bg=rgb(7,12,25), panel=rgb(17,27,47), card=rgb(25,39,64);
  uint32_t white=rgb(238,245,255), muted=rgb(145,165,194), blue=rgb(57,145,255), cyan=rgb(75,213,226);
  rect(0,0,W,H,bg);
  for(int y=0;y<H;y++) rect(0,y,W,1,rgb(7,12+y*10/(H?H:1),25+y*18/(H?H:1)));
  int cw=W-80; if(cw>760)cw=760; if(cw<280)cw=W-24;
  int x=(W-cw)/2, y=(H-330)/2; if(y<12)y=12;
  roundrect(x,y,cw,330,22,panel);
  roundrect(x+24,y+22,42,42,21,blue);
  roundrect(x+43,y+17,16,16,8,cyan);
  text(x+82,y+34,"MEU OVEROS",2,white);
  text(x+26,y+91,"CONFIGURACAO INICIAL",1,cyan);
  if(stage==0) {
    text(x+26,y+132,"COMO PODEMOS TE CHAMAR?",2,white);
    roundrect(x+24,y+165,cw-48,52,10,card);
    text(x+42,y+185,username,2,white);
    text(x+26,y+245,"DIGITE SEU NOME E PRESSIONE ENTER",1,muted);
  } else if(stage==1) {
    text(x+26,y+132,"COMO DESEJA INICIAR?",2,white);
    roundrect(x+24,y+164,cw-48,38,9,mode==0?rgb(35,75,125):card);
    text(x+40,y+178,"1 AUTOMATICO",1,white);
    roundrect(x+24,y+207,cw-48,38,9,mode==1?rgb(35,75,125):card);
    text(x+40,y+221,"2 SOMENTE TERMINAL",1,white);
    roundrect(x+24,y+250,cw-48,38,9,mode==2?rgb(35,75,125):card);
    text(x+40,y+264,"3 INTERFACE GRAFICA",1,white);
  } else {
    text(x+26,y+132,"CONFIGURAR O APP?",2,white);
    text(x+26,y+169,"MEU OVEROS NO CELULAR",1,muted);
    roundrect(x+24,y+205,(cw-60)/2,54,10,pair?rgb(35,75,125):card);
    text(x+42,y+225,"Y SIM",2,white);
    roundrect(x+36+(cw-60)/2,y+205,(cw-60)/2,54,10,!pair?rgb(35,75,125):card);
    text(x+54+(cw-60)/2,y+225,"N NAO",2,white);
    text(x+26,y+280,"QR E CODIGO APARECEM SOMENTE SE ESCOLHER SIM",1,muted);
  }
  text(x+26,y+310,"OVEROS LITE  |  SETAS / TECLAS / ENTER",1,muted);
  present();
}

static int save_setup(const char *username,int mode,int pair) {
  if(mkdir("/etc/overos",0755)<0 && errno!=EEXIST)return -1;
  FILE *fp=fopen("/etc/overos/setup.conf","w");
  if(!fp)return -1;
  const char *session=mode==1?"terminal":mode==2?"gui":"auto";
  fprintf(fp,"username=%s\navatar=blue\nsession=%s\npair=%s\nsetup_complete=1\n",
          username,session,pair?"yes":"no");
  fclose(fp);
  chmod("/etc/overos/setup.conf",0600);
  return 0;
}


/* Antes do QR, tente configurar a rede no proprio terminal. */
static void run_network_setup_if_needed(void) {
  int has_route = (system("ip route show default 2>/dev/null | grep -q .") == 0);
  if (has_route) return;

  if (tty >= 0) ioctl(tty, KDSETMODE, KD_TEXT);
  tcsetattr(STDIN_FILENO, TCSAFLUSH, &oldt);
  printf("\n=== OVEROS: CONFIGURAR INTERNET ===\n");
  printf("Nao foi encontrada uma rota de rede. Configure a conexao antes do QR.\n\n");

  if (system("command -v nmtui >/dev/null 2>&1") == 0) {
    printf("Abrindo o configurador de rede. Use as setas e Enter.\n");
    (void)system("nmtui");
  } else if (system("command -v nmcli >/dev/null 2>&1") == 0) {
    printf("nmtui nao esta instalado; estado dos dispositivos:\n");
    (void)system("nmcli device status");
    printf("\nO QR sera adiado para que o sistema continue iniciando.\n");
    printf("Pressione Enter para continuar...");
    (void)getchar();
  } else {
    printf("O configurador de rede nao esta instalado nesta imagem.\n");
    printf("O pareamento por QR sera ignorado nesta inicializacao.\n");
    printf("Pressione Enter para continuar...");
    (void)getchar();
  }

  struct termios raw = oldt;
  raw.c_lflag &= ~(ICANON | ECHO);
  raw.c_cc[VMIN] = 0;
  raw.c_cc[VTIME] = 1;
  tcsetattr(STDIN_FILENO, TCSAFLUSH, &raw);
  if (tty >= 0) ioctl(tty, KDSETMODE, KD_GRAPHICS);
}

static void run_pair_screen(int *pair) {
  run_network_setup_if_needed();
  const char *state_file="/mnt/overos-data/overos-data/pair/paired-devices.json";
  if(access("/usr/bin/over-pair",X_OK)!=0) {
    if(tty>=0)ioctl(tty,KDSETMODE,KD_TEXT);
    tcsetattr(STDIN_FILENO,TCSAFLUSH,&oldt);
    printf("\nPareamento do celular indisponivel nesta inicializacao.\n");
    printf("Pressione Enter para continuar...");
    (void)getchar();
  } else {
    if(tty>=0)ioctl(tty,KDSETMODE,KD_TEXT);
    tcsetattr(STDIN_FILENO,TCSAFLUSH,&oldt);
    (void)system("/usr/bin/over-pair serve --state-dir /mnt/overos-data/overos-data/pair >/run/over-pair.log 2>&1 & echo $! >/run/over-pair.pid");
    sleep(2);
    (void)system("/usr/bin/over-pair screen");
    if(access(state_file,F_OK)==0) *pair=1;
    else (void)system("kill $(cat /run/over-pair.pid) 2>/dev/null || true");
    printf("\n%s\n",*pair?"Celular conectado ao OverOS!":"Pareamento ignorado. Continuando...");
  }
  struct termios raw=oldt;
  raw.c_lflag&=~(ICANON|ECHO);
  raw.c_cc[VMIN]=0;
  raw.c_cc[VTIME]=1;
  tcsetattr(STDIN_FILENO,TCSAFLUSH,&raw);
  if(tty>=0)ioctl(tty,KDSETMODE,KD_GRAPHICS);
}

static int run_setup(void) {
  char username[33]="over";
  int stage=0,mode=0,pair=0;
  for(;;) {
    draw_setup(stage,username,mode,pair);
    struct pollfd p={STDIN_FILENO,POLLIN,0};
    if(poll(&p,1,-1)<=0)continue;
    unsigned char c=0;
    if(read(STDIN_FILENO,&c,1)!=1)continue;
    if(c==27) {
      unsigned char a=0,b=0; struct pollfd k={STDIN_FILENO,POLLIN,0};
      if(poll(&k,1,60)>0 && read(STDIN_FILENO,&a,1)==1 && a=='[' && poll(&k,1,60)>0 && read(STDIN_FILENO,&b,1)==1) {
        if(b=='A')c='w'; else if(b=='B')c='s';
      }
    }
    if(stage==0) {
      if(c=='\r'||c=='\n') { stage=1; continue; }
      if(c==127||c==8) { size_t n=strlen(username); if(n>0)username[n-1]=0; continue; }
      if(((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='-')&&strlen(username)<31) {
        size_t n=strlen(username); username[n]=(char)((c>='A'&&c<='Z')?c+32:c); username[n+1]=0;
      }
    } else if(stage==1) {
      if(c=='w'||c=='W'||c=='k'||c=='K') mode=(mode+2)%3;
      else if(c=='s'||c=='S'||c=='j'||c=='J') mode=(mode+1)%3;
      else if(c=='1'){mode=0;stage=2;}
      else if(c=='2'){mode=1;stage=2;}
      else if(c=='3'){mode=2;stage=2;}
      else if(c=='\r'||c=='\n'||c==' ') {
        pair=0;
        if(save_setup(username,mode,pair)<0)return 1;
        run_pair_screen(&pair);
        if(save_setup(username,mode,pair)<0)return 1;
        break;
      }
    }
  }
  draw_setup(3,username,mode,pair);
  text(W/2-90,H/2+120,"CONFIGURACAO SALVA",1,rgb(75,213,226));
  for(int i=0;i<8;i++){struct pollfd p={STDIN_FILENO,POLLIN,0};if(poll(&p,1,250)>0)break;}
  return 0;
}

/* Fallback de emergencia: terminal leve, sem framebuffer nem aceleracao. */
static int run_text_fallback(void) {
  for (;;) {
    printf("\n=== OVEROS LITE - MODO DE RECUPERACAO ===\n");
    printf("A interface grafica falhou; o sistema continua utilizavel.\n\n");
    printf("1) Configurar rede (nmtui)\n");
    printf("2) Ver interfaces de rede\n");
    printf("0) Continuar no terminal\n");
    printf("Opcao: ");
    fflush(stdout);
    char line[32];
    if (!fgets(line, sizeof(line), stdin)) return 0;
    if (line[0]=='1') {
      if (system("command -v nmtui >/dev/null 2>&1") == 0)
        (void)system("nmtui");
      else {
        printf("nmtui nao instalado. Interfaces atuais:\n");
        (void)system("ip link 2>/dev/null || ifconfig 2>/dev/null || true");
        printf("A imagem precisa incluir NetworkManager e network-manager-tui.\n");
      }
    } else if (line[0]=='2') {
      (void)system("ip link 2>/dev/null || ifconfig 2>/dev/null || true");
      printf("\nPressione Enter para voltar...");
      (void)fgets(line, sizeof(line), stdin);
    } else if (line[0]=='0') return 0;
  }
}

int main(int argc,char **argv) {
  fb=open("/dev/fb0",O_RDWR);
  if(fb<0){ dprintf(2,"OverOS Shell: /dev/fb0 indisponivel: %s\n",strerror(errno)); return run_text_fallback(); }
  if(ioctl(fb,FBIOGET_FSCREENINFO,&fi)||ioctl(fb,FBIOGET_VSCREENINFO,&vi)){dprintf(2,"OverOS Shell: framebuffer invalido\n");close(fb); fb=-1; return run_text_fallback();}
  W=vi.xres; H=vi.yres; bpp=vi.bits_per_pixel; mapsz=fi.smem_len;
  if(W<320||H<200||(bpp!=16&&bpp!=24&&bpp!=32)){dprintf(2,"OverOS Shell: modo grafico nao suportado (%dx%d %dbpp)\n",W,H,bpp);close(fb); fb=-1; return run_text_fallback();}
  mem=mmap(NULL,mapsz,PROT_READ|PROT_WRITE,MAP_SHARED,fb,0);
  if(mem==MAP_FAILED){dprintf(2,"OverOS Shell: mmap falhou\n");mem=NULL; close(fb); fb=-1; return run_text_fallback();}
  backbuf=calloc(1,(size_t)mapsz);
  if(!backbuf){dprintf(2,"OverOS Shell: memoria de desenho insuficiente\\n");cleanup();return run_text_fallback();}
  tty=open("/dev/tty0",O_RDWR|O_NOCTTY);
  if(tty>=0){ioctl(tty,KDGETMODE,&old_graphics);ioctl(tty,KDSETMODE,KD_GRAPHICS);}
  if(tcgetattr(STDIN_FILENO,&oldt)==0){struct termios raw=oldt;raw.c_lflag&=~(ICANON|ECHO);raw.c_cc[VMIN]=0;raw.c_cc[VTIME]=1;tcsetattr(STDIN_FILENO,TCSAFLUSH,&raw);}
  if(argc>1 && strcmp(argv[1],"--setup")==0){int result=run_setup();cleanup();return result;}
  draw();
  for(;;){
    struct pollfd p={STDIN_FILENO,POLLIN,0}; if(poll(&p,1,250)>0&&p.revents&POLLIN){
      unsigned char c; if(read(STDIN_FILENO,&c,1)==1){
        if(c=='q'||c=='Q'||c==27)break;
        if((c=='p'||c=='P')&&page==3) {
          if(tty>=0)ioctl(tty,KDSETMODE,KD_TEXT);
          tcsetattr(STDIN_FILENO,TCSAFLUSH,&oldt);
          (void)system("/usr/bin/overosctl pair");
          struct termios raw=oldt;raw.c_lflag&=~(ICANON|ECHO);raw.c_cc[VMIN]=0;raw.c_cc[VTIME]=1;
          tcsetattr(STDIN_FILENO,TCSAFLUSH,&raw);
          if(tty>=0)ioctl(tty,KDSETMODE,KD_GRAPHICS);
        }
        if(c>='1'&&c<='5'){page=c-'1';selected=0;}
        if(c==' '||c=='\r'||c=='\n') { if(page==0)page=1; }
        if(c=='\t'||c=='j'||c=='J'||c==']')selected=(selected+1)%4;
        if(c=='k'||c=='K'||c=='[')selected=(selected+3)%4;
      }
      draw();
    }
  }
  cleanup(); return 0;
}
