package main
import (
 "bufio"
 "encoding/json"
 "net/http"
 "os"
 "path/filepath"
 "runtime"
 "strconv"
 "strings"
 "syscall"
 "time"
)
func readMemInfo()(uint64,uint64){
 f,e:=os.Open("/proc/meminfo"); if e!=nil{return 0,0}; defer f.Close()
 values:=map[string]uint64{}; sc:=bufio.NewScanner(f)
 for sc.Scan(){p:=strings.Fields(sc.Text());if len(p)>=2&&(p[0]=="MemTotal:"||p[0]=="MemAvailable:"){n,_:=strconv.ParseUint(p[1],10,64);values[p[0]]=n}}
 total,available:=values["MemTotal:"],values["MemAvailable:"];if total==0{return 0,0};used:=total;if available<=total{used=total-available};return total/1024,used/1024
}
func readBattery()(int,string){
 entries,e:=os.ReadDir("/sys/class/power_supply");if e!=nil{return -1,"indisponível"}
 for _,entry:=range entries{base:=filepath.Join("/sys/class/power_supply",entry.Name());typ,_:=os.ReadFile(filepath.Join(base,"type"));if strings.TrimSpace(string(typ))!="Battery"{continue};raw,e:=os.ReadFile(filepath.Join(base,"capacity"));if e!=nil{return -1,"indisponível"};level,e:=strconv.Atoi(strings.TrimSpace(string(raw)));if e!=nil{return -1,"indisponível"};status,_:=os.ReadFile(filepath.Join(base,"status"));return level,strings.TrimSpace(string(status))}
 return -1,"sem bateria detectada"
}
func systemStatus(w http.ResponseWriter,r *http.Request,s *PairServer){
 if r.Method!=http.MethodGet{writeJSON(w,405,map[string]string{"error":"método não permitido"});return}
 if _,ok:=s.authenticatedDevice(r);!ok{writeJSON(w,401,map[string]string{"error":"pareie o celular primeiro"});return}
 totalMem,usedMem:=readMemInfo();battery,batteryStatus:=readBattery();var stat syscall.Statfs_t
 storageTotal,storageUsed:=uint64(0),uint64(0)
 if syscall.Statfs("/",&stat)==nil{storageTotal=stat.Blocks*uint64(stat.Bsize)/(1024*1024*1024);storageUsed=(stat.Blocks-stat.Bfree)*uint64(stat.Bsize)/(1024*1024*1024)}
 load:="";if b,e:=os.ReadFile("/proc/loadavg");e==nil{p:=strings.Fields(string(b));if len(p)>0{load=p[0]}}
 uptime:=int64(0);if b,e:=os.ReadFile("/proc/uptime");e==nil{p:=strings.Fields(string(b));if len(p)>0{if v,x:=strconv.ParseFloat(p[0],64);x==nil{uptime=int64(v)}}}
 hostname,_:=os.Hostname()
 payload:=map[string]any{"hostname":hostname,"cpu_cores":runtime.NumCPU(),"cpu_load_1m":load,"memory_total_mb":totalMem,"memory_used_mb":usedMem,"memory_percent":percent(usedMem,totalMem),"battery_percent":battery,"battery_status":batteryStatus,"storage_total_gb":storageTotal,"storage_used_gb":storageUsed,"uptime_seconds":uptime,"server_time":time.Now().UTC().Format(time.RFC3339)}
 w.Header().Set("Content-Type","application/json");w.Header().Set("Cache-Control","no-store");_ = json.NewEncoder(w).Encode(payload)
}
func percent(used,total uint64)uint64{if total==0{return 0};return used*100/total}
func registerSystemRoutes(mux *http.ServeMux,s *PairServer){mux.HandleFunc("/api/v1/system/status",func(w http.ResponseWriter,r *http.Request){systemStatus(w,r,s)})}
