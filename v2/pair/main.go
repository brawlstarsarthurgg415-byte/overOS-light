package main

import (
	"bufio"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"flag"
	"fmt"
	"math/big"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	qrcode "github.com/skip2/go-qrcode"
)

const (
	listenAddr = ":8766"
	pairTTL = 2 * time.Minute
	maxAttempts = 5
)

type Pending struct {
	ID string
	Nonce string
	Expires time.Time
	Scanned bool
	Code string
	Attempts int
	Completed bool
	DeviceID string
	DeviceName string
}

type Device struct {
	ID string
	Name string
	TokenHash string
	CreatedAt time.Time
}

type PairServer struct {
	mu sync.Mutex
	pending *Pending
	devices []Device
	stateFile string
	fingerprint string
	hostIP string
}

func randomBytes(n int) ([]byte,error) {
	b:=make([]byte,n)
	_,err:=rand.Read(b)
	return b,err
}

func randomID(n int) (string,error) {
	b,err:=randomBytes(n)
	if err!=nil{return "",err}
	return hex.EncodeToString(b),nil
}

func randomCode() (string,error) {
	for {
		n,err:=rand.Int(rand.Reader,big.NewInt(1000000))
		if err!=nil{return "",err}
		code:=fmt.Sprintf("%06d",n.Int64())
		// Código usado na demonstração visual nunca é uma credencial real.
		if code!="482917" { return code,nil }
	}
}

func tokenHash(token string) string {
	sum:=sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func (s *PairServer) loadDevices() error {
	if err:=os.MkdirAll(filepath.Dir(s.stateFile),0700); err!=nil{return err}
	raw,err:=os.ReadFile(s.stateFile)
	if errors.Is(err,os.ErrNotExist){return nil}
	if err!=nil{return err}
	var devices []Device
	if err:=json.Unmarshal(raw,&devices); err!=nil{
		return fmt.Errorf("arquivo de pareamentos inválido; recusando sobrescrever: %w",err)
	}
	s.devices=devices
	return nil
}

func (s *PairServer) saveDevicesLocked() error {
	if err:=os.MkdirAll(filepath.Dir(s.stateFile),0700); err!=nil{return err}
	raw,err:=json.MarshalIndent(s.devices,"","  ")
	if err!=nil{return err}
	tmp:=s.stateFile+".tmp"
	if err:=os.WriteFile(tmp,raw,0600); err!=nil{return err}
	if err:=os.Chmod(tmp,0600); err!=nil{return err}
	return os.Rename(tmp,s.stateFile)
}

func writeJSON(w http.ResponseWriter,status int,value any) {
	w.Header().Set("Content-Type","application/json; charset=utf-8")
	w.Header().Set("Cache-Control","no-store")
	w.Header().Set("X-Content-Type-Options","nosniff")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func readJSON(w http.ResponseWriter,r *http.Request,dst any) error {
	if r.Method!=http.MethodPost{return errors.New("método não permitido")}
	r.Body=http.MaxBytesReader(w,r.Body,4096)
	defer r.Body.Close()
	dec:=json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	return dec.Decode(dst)
}

func localRequest(r *http.Request) bool {
	host,_,err:=net.SplitHostPort(r.RemoteAddr)
	if err!=nil{return false}
	ip:=net.ParseIP(host)
	return ip!=nil&&ip.IsLoopback()
}

func (s *PairServer) internalOnly(w http.ResponseWriter,r *http.Request) bool {
	if !localRequest(r){
		writeJSON(w,http.StatusForbidden,map[string]string{"error":"endpoint interno"})
		return false
	}
	return true
}

func (s *PairServer) createSession(w http.ResponseWriter,r *http.Request) {
	if !s.internalOnly(w,r){return}
	if r.Method!=http.MethodPost{writeJSON(w,405,map[string]string{"error":"método não permitido"});return}
	s.mu.Lock()
	hostIP:=s.hostIP
	s.mu.Unlock()
	if hostIP==""{writeJSON(w,503,map[string]string{"error":"OverOS ainda não tem endereço IPv4 privado na rede local"});return}
	id,err:=randomID(16)
	if err!=nil{writeJSON(w,500,map[string]string{"error":"não foi possível gerar sessão"});return}
	nb,err:=randomBytes(32)
	if err!=nil{writeJSON(w,500,map[string]string{"error":"não foi possível gerar desafio"});return}
	nonce:=hex.EncodeToString(nb)
	qr:=map[string]any{
		"type":"overos-pair","version":1,
		"url":"https://"+net.JoinHostPort(hostIP,"8766"),
		"session_id":id,"nonce":nonce,"tls_sha256":s.fingerprint,
	}
	s.mu.Lock()
	s.pending=&Pending{ID:id,Nonce:nonce,Expires:time.Now().Add(pairTTL)}
	s.mu.Unlock()
	writeJSON(w,200,map[string]any{"session_id":id,"expires_in":int(pairTTL.Seconds()),"qr":qr})
}

func (s *PairServer) sessionStatus(w http.ResponseWriter,r *http.Request) {
	if !s.internalOnly(w,r){return}
	if r.Method!=http.MethodGet{writeJSON(w,405,map[string]string{"error":"método não permitido"});return}
	id:=r.URL.Query().Get("session_id")
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.pending==nil||s.pending.ID!=id{writeJSON(w,404,map[string]string{"state":"missing"});return}
	p:=s.pending
	if time.Now().After(p.Expires){writeJSON(w,200,map[string]string{"state":"expired"});return}
	state:="waiting_scan"
	if p.Scanned{state="waiting_code"}
	if p.Completed{state="paired"}
	writeJSON(w,200,map[string]any{"state":state,"code":p.Code,"device_name":p.DeviceName,"expires_at":p.Expires.UTC().Format(time.RFC3339)})
}

func (s *PairServer) cancelSession(w http.ResponseWriter,r *http.Request) {
	if !s.internalOnly(w,r){return}
	if r.Method!=http.MethodPost{writeJSON(w,405,map[string]string{"error":"método não permitido"});return}
	s.mu.Lock()
	s.pending=nil
	s.mu.Unlock()
	writeJSON(w,200,map[string]bool{"cancelled":true})
}

func (s *PairServer) scan(w http.ResponseWriter,r *http.Request) {
	var req map[string]string
	if err:=readJSON(w,r,&req);err!=nil{writeJSON(w,400,map[string]string{"error":"requisição inválida"});return}
	sessionID,nonce:=req["session_id"],req["nonce"]
	s.mu.Lock()
	defer s.mu.Unlock()
	p:=s.pending
	if p==nil||p.ID!=sessionID||time.Now().After(p.Expires)||p.Completed{
		writeJSON(w,410,map[string]string{"error":"QR expirado ou sessão inexistente"});return
	}
	if p.Scanned{writeJSON(w,409,map[string]string{"error":"este QR já foi utilizado"});return}
	if len(nonce)!=len(p.Nonce)||subtle.ConstantTimeCompare([]byte(nonce),[]byte(p.Nonce))!=1{
		writeJSON(w,403,map[string]string{"error":"QR não corresponde a esta sessão"});return
	}
	code,err:=randomCode()
	if err!=nil{writeJSON(w,500,map[string]string{"error":"falha ao gerar código"});return}
	p.Scanned=true
	p.Code=code
	writeJSON(w,200,map[string]any{"ok":true,"next":"enter_computer_code","expires_in":int(time.Until(p.Expires).Seconds())})
}

func validDeviceID(id string) bool {
	if len(id)<8||len(id)>64{return false}
	for _,r:=range id{
		if !(r>='a'&&r<='z'||r>='A'&&r<='Z'||r>='0'&&r<='9'||r=='-'||r=='_'||r=='.'){return false}
	}
	return true
}

func (s *PairServer) complete(w http.ResponseWriter,r *http.Request) {
	var req map[string]string
	if err:=readJSON(w,r,&req);err!=nil{writeJSON(w,400,map[string]string{"error":"requisição inválida"});return}
	sessionID,code,deviceID:=req["session_id"],req["code"],req["device_id"]
	if len(code)!=6||!validDeviceID(deviceID){writeJSON(w,400,map[string]string{"error":"código ou identificação do celular inválidos"});return}
	for _,digit:=range code{if digit<'0'||digit>'9'{writeJSON(w,400,map[string]string{"error":"o código precisa ter seis dígitos"});return}}
	name:=strings.TrimSpace(strings.Map(func(ch rune)rune{if ch<32||ch==127{return -1};return ch},req["device_name"]))
	if len(name)>80{name=name[:80]}
	if name==""{name="OverOS Control"}

	s.mu.Lock()
	defer s.mu.Unlock()
	p:=s.pending
	if p==nil||p.ID!=sessionID||time.Now().After(p.Expires)||!p.Scanned||p.Completed{
		writeJSON(w,410,map[string]string{"error":"sessão expirada ou QR ainda não validado"});return
	}
	if p.Attempts>=maxAttempts{s.pending=nil;writeJSON(w,429,map[string]string{"error":"limite de tentativas excedido; gere um novo QR"});return}
	p.Attempts++
	if len(code)!=len(p.Code)||subtle.ConstantTimeCompare([]byte(code),[]byte(p.Code))!=1{
		writeJSON(w,401,map[string]string{"error":"código incorreto"});return
	}
	token,err:=randomID(32)
	if err!=nil{writeJSON(w,500,map[string]string{"error":"não foi possível criar credencial"});return}
	oldDevices:=append([]Device(nil),s.devices...)
	device:=Device{ID:deviceID,Name:name,TokenHash:tokenHash(token),CreatedAt:time.Now().UTC()}
	for i:=range s.devices{if s.devices[i].ID==device.ID{s.devices=append(s.devices[:i],s.devices[i+1:]...);break}}
	s.devices=append(s.devices,device)
	if err:=s.saveDevicesLocked();err!=nil{
		s.devices=oldDevices
		writeJSON(w,500,map[string]string{"error":"não foi possível persistir o pareamento"});return
	}
	p.Completed=true
	p.DeviceID=device.ID
	p.DeviceName=device.Name
	writeJSON(w,200,map[string]any{"ok":true,"device_id":device.ID,"device_name":device.Name,"access_token":token})
}

func bearer(r *http.Request) string {
	value:=r.Header.Get("Authorization")
	if !strings.HasPrefix(value,"Bearer "){return ""}
	return strings.TrimSpace(strings.TrimPrefix(value,"Bearer "))
}

func (s *PairServer) authenticatedDevice(r *http.Request)(Device,bool) {
	token:=bearer(r)
	if token==""{return Device{},false}
	hash:=tokenHash(token)
	s.mu.Lock()
	defer s.mu.Unlock()
	for _,d:=range s.devices{
		if len(hash)==len(d.TokenHash)&&subtle.ConstantTimeCompare([]byte(hash),[]byte(d.TokenHash))==1{return d,true}
	}
	return Device{},false
}

func (s *PairServer) verify(w http.ResponseWriter,r *http.Request) {
	if r.Method!=http.MethodGet{writeJSON(w,405,map[string]string{"error":"método não permitido"});return}
	d,ok:=s.authenticatedDevice(r)
	if !ok{writeJSON(w,401,map[string]string{"error":"dispositivo não pareado"});return}
	writeJSON(w,200,map[string]any{"ok":true,"device_id":d.ID,"device_name":d.Name,"created_at":d.CreatedAt})
}

func (s *PairServer) listDevices(w http.ResponseWriter,r *http.Request) {
	if r.Method!=http.MethodGet{writeJSON(w,405,map[string]string{"error":"método não permitido"});return}
	if _,ok:=s.authenticatedDevice(r);!ok{writeJSON(w,401,map[string]string{"error":"autenticação necessária"});return}
	s.mu.Lock()
	defer s.mu.Unlock()
	out:=make([]map[string]any,0,len(s.devices))
	for _,d:=range s.devices{out=append(out,map[string]any{"id":d.ID,"name":d.Name,"created_at":d.CreatedAt})}
	writeJSON(w,200,map[string]any{"devices":out})
}

func (s *PairServer) revokeDevice(w http.ResponseWriter,r *http.Request) {
	if r.Method!=http.MethodDelete{writeJSON(w,405,map[string]string{"error":"método não permitido"});return}
	actor,ok:=s.authenticatedDevice(r)
	if !ok{writeJSON(w,401,map[string]string{"error":"autenticação necessária"});return}
	id:=strings.TrimPrefix(r.URL.Path,"/api/v1/pair/devices/")
	if id==""||id!=actor.ID{writeJSON(w,403,map[string]string{"error":"um dispositivo só pode revogar a própria credencial"});return}
	s.mu.Lock()
	defer s.mu.Unlock()
	for i,d:=range s.devices{
		if d.ID==id{
			old:=append([]Device(nil),s.devices...)
			s.devices=append(s.devices[:i],s.devices[i+1:]...)
			if err:=s.saveDevicesLocked();err!=nil{s.devices=old;writeJSON(w,500,map[string]string{"error":"falha ao salvar revogação"});return}
			writeJSON(w,200,map[string]bool{"revoked":true});return
		}
	}
	writeJSON(w,404,map[string]string{"error":"dispositivo não encontrado"})
}

func lanIPv4s()([]net.IP,error) {
	ifaces,err:=net.Interfaces()
	if err!=nil{return nil,err}
	var found []net.IP
	for _,iface:=range ifaces{
		if iface.Flags&net.FlagUp==0||iface.Flags&net.FlagLoopback!=0{continue}
		addrs,err:=iface.Addrs()
		if err!=nil{continue}
		for _,addr:=range addrs{
			var ip net.IP
			switch v:=addr.(type){case *net.IPNet:ip=v.IP;case *net.IPAddr:ip=v.IP}
			ip=ip.To4()
			if ip==nil||ip.IsLoopback()||ip.IsLinkLocalUnicast()||!ip.IsPrivate(){continue}
			found=append(found,ip)
		}
	}
	if len(found)==0{return nil,errors.New("sem IPv4 na LAN")}
	return found,nil
}

func makeTLSCertificate()(tls.Certificate,string,error) {
	pub,priv,err:=ed25519.GenerateKey(rand.Reader)
	if err!=nil{return tls.Certificate{},"",err}
	serial,err:=rand.Int(rand.Reader,big.NewInt(1<<62))
	if err!=nil{return tls.Certificate{},"",err}
	now:=time.Now()
	tmpl:=x509.Certificate{SerialNumber:serial,Subject:pkix.Name{CommonName:"OverOS Pair local"},NotBefore:now.Add(-time.Minute),NotAfter:now.Add(24*time.Hour),KeyUsage:x509.KeyUsageDigitalSignature,ExtKeyUsage:[]x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},BasicConstraintsValid:true,DNSNames:[]string{"localhost"}}
	der,err:=x509.CreateCertificate(rand.Reader,&tmpl,&tmpl,pub,priv)
	if err!=nil{return tls.Certificate{},"",err}
	keyDER,err:=x509.MarshalPKCS8PrivateKey(priv)
	if err!=nil{return tls.Certificate{},"",err}
	certPEM:=pem.EncodeToMemory(&pem.Block{Type:"CERTIFICATE",Bytes:der})
	keyPEM:=pem.EncodeToMemory(&pem.Block{Type:"PRIVATE KEY",Bytes:keyDER})
	cert,err:=tls.X509KeyPair(certPEM,keyPEM)
	if err!=nil{return tls.Certificate{},"",err}
	sum:=sha256.Sum256(der)
	return cert,hex.EncodeToString(sum[:]),nil
}

func(s *PairServer)handler()http.Handler {
	mux:=http.NewServeMux()
	mux.HandleFunc("/internal/pair/start",s.createSession)
	mux.HandleFunc("/internal/pair/status",s.sessionStatus)
	mux.HandleFunc("/internal/pair/cancel",s.cancelSession)
	mux.HandleFunc("/api/v1/pair/scan",s.scan)
	mux.HandleFunc("/api/v1/pair/complete",s.complete)
	mux.HandleFunc("/api/v1/pair/verify",s.verify)
	mux.HandleFunc("/api/v1/pair/devices",s.listDevices)
	mux.HandleFunc("/api/v1/pair/devices/",s.revokeDevice)
	mux.HandleFunc("/healthz",func(w http.ResponseWriter,r *http.Request){writeJSON(w,200,map[string]any{"ok":true,"service":"overos-pair"})})
	registerSystemRoutes(mux,s)
	return mux
}

func serve(stateDir string)error {
	if err:=os.MkdirAll(stateDir,0700);err!=nil{return err}
	s:=&PairServer{stateFile:filepath.Join(stateDir,"paired-devices.json")}
	if err:=s.loadDevices();err!=nil{return err}
	cert,fp,err:=makeTLSCertificate()
	if err!=nil{return err}
	s.fingerprint=fp
	internal:=&http.Server{Addr:"127.0.0.1:8767",Handler:s.handler(),ReadHeaderTimeout:5*time.Second,ReadTimeout:10*time.Second,WriteTimeout:10*time.Second,IdleTimeout:30*time.Second}
	go func(){if err:=internal.ListenAndServe();err!=nil&&err!=http.ErrServerClosed{log.Printf("listener interno: %v",err)}}()
	server:=&http.Server{Addr:listenAddr,Handler:s.handler(),ReadHeaderTimeout:5*time.Second,ReadTimeout:10*time.Second,WriteTimeout:10*time.Second,IdleTimeout:30*time.Second,TLSConfig:&tls.Config{Certificates:[]tls.Certificate{cert},MinVersion:tls.VersionTLS12}}
	var ln net.Listener
	for {
		ips,ipErr:=lanIPv4s()
		if ipErr!=nil{log.Printf("aguardando endereço IPv4 privado para pareamento");time.Sleep(3*time.Second);continue}
		s.mu.Lock()
		s.hostIP=ips[0].String()
		s.mu.Unlock()
		ln,err=net.Listen("tcp",net.JoinHostPort(s.hostIP,"8766"))
		if err==nil{break}
		log.Printf("não foi possível abrir listener LAN: %v",err)
		time.Sleep(3*time.Second)
	}
	log.Printf("OverOS Pair HTTPS ativo apenas em %s:8766; estado em %s",s.hostIP,stateDir)
	return server.ServeTLS(ln,"","")
}

func localJSON(method,path string,body any)(map[string]any,error) {
	var reader *strings.Reader
	if body==nil{reader=strings.NewReader("")}else{b,err:=json.Marshal(body);if err!=nil{return nil,err};reader=strings.NewReader(string(b))}
	req,err:=http.NewRequest(method,"http://127.0.0.1:8767"+path,reader)
	if err!=nil{return nil,err}
	req.Header.Set("Content-Type","application/json")
	client:=&http.Client{Timeout:3*time.Second}
	resp,err:=client.Do(req)
	if err!=nil{return nil,err}
	defer resp.Body.Close()
	var result map[string]any
	if err:=json.NewDecoder(resp.Body).Decode(&result);err!=nil{return nil,err}
	if resp.StatusCode<200||resp.StatusCode>=300{return result,fmt.Errorf("%v",result["error"])}
	return result,nil
}

func screen()error {
	input:=make(chan string,4)
	go func(){
		scanner:=bufio.NewScanner(os.Stdin)
		for scanner.Scan(){
			line:=strings.TrimSpace(scanner.Text())
			select{case input<-line:default:}
		}
	}()
	for {
		start,err:=localJSON(http.MethodPost,"/internal/pair/start",map[string]any{})
		if err!=nil{
			fmt.Print("\033[2J\033[H")
			fmt.Println("                 OVEROS PAIR")
			fmt.Println("Aguardando conexão de rede para gerar o QR Code...")
			fmt.Println("[ P + Enter ] Pular")
			select{case line:=<-input:if line==""||strings.EqualFold(line,"p")||strings.EqualFold(line,"pular"){return nil};case <-time.After(3*time.Second):}
			continue
		}
		session:=fmt.Sprint(start["session_id"])
		qrMap,ok:=start["qr"].(map[string]any)
		if !ok{return errors.New("servidor não retornou QR Code")}
		payload,err:=json.Marshal(qrMap)
		if err!=nil{return err}
		qr,err:=qrcode.New(string(payload),qrcode.Medium)
		if err!=nil{return err}
		ascii:=qr.ToString(false)
		fmt.Print("\033[2J\033[H")
		fmt.Println("                 OVEROS PAIR")
		fmt.Println("       Escaneie com o aplicativo OverOS Control")
		fmt.Println()
		fmt.Print(ascii)
		fmt.Println()
		fmt.Println("       [ P + Enter ] Pular configuração do aplicativo")
		fmt.Println("       O código só aparece depois da leitura válida.")
		shownCode:=""
		ticker:=time.NewTicker(700*time.Millisecond)
		done:=false
		for !done{
			select{
			case line:=<-input:
				if line==""||strings.EqualFold(line,"p")||strings.EqualFold(line,"pular"){
					_,_=localJSON(http.MethodPost,"/internal/pair/cancel",map[string]any{})
					ticker.Stop()
					fmt.Println("\nConfiguração ignorada. Continuando o OverOS...")
					return nil
				}
			case <-ticker.C:
				status,err:=localJSON(http.MethodGet,"/internal/pair/status?session_id="+session,nil)
				if err!=nil{continue}
				switch status["state"]{
				case "waiting_code":
					code:=fmt.Sprint(status["code"])
					if code!=""&&code!="<nil>"&&code!=shownCode{
						shownCode=code
						fmt.Print("\033[2J\033[H")
						fmt.Println("                 OVEROS PAIR")
						fmt.Println()
						fmt.Println("       QR Code validado pelo aplicativo.")
						fmt.Println()
						fmt.Println("          CÓDIGO DE PAREAMENTO")
						fmt.Printf("\n                 %s\n\n",code)
						fmt.Println("       Digite este código no celular.")
						fmt.Println("       [ P + Enter ] Cancelar e pular")
					}
				case "paired":
					ticker.Stop()
					fmt.Print("\033[2J\033[H")
					fmt.Println("                 OVEROS PAIR")
					fmt.Println("\n       Celular pareado com sucesso!")
					fmt.Printf("       Dispositivo: %v\n",status["device_name"])
					fmt.Println("\n       Iniciando o OverOS...")
					time.Sleep(2*time.Second)
					return nil
				case "expired","missing":done=true
				}
			}
		}
		ticker.Stop()
		fmt.Println("\nQR expirou. Gerando uma nova sessão...")
	}
}

func main() {
	if len(os.Args)<2{fmt.Println("Uso: over-pair serve|screen");os.Exit(2)}
	switch os.Args[1]{
	case "serve":
		fs:=flag.NewFlagSet("serve",flag.ExitOnError)
		dir:=fs.String("state-dir",os.Getenv("OVEROS_PAIR_STATE_DIR"),"diretório persistente das credenciais")
		_=fs.Parse(os.Args[2:])
		if *dir==""{*dir="/var/lib/overos/pair"}
		if err:=serve(*dir);err!=nil{log.Fatal(err)}
	case "screen":
		if err:=screen();err!=nil{log.Fatal(err)}
	default:
		fmt.Println("Uso: over-pair serve|screen")
		os.Exit(2)
	}
}
