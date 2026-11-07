/* Cliente da Kelly API local: comandos e resultados só são exibidos quando chegam do backend. */
(() => {
  const $ = (id) => document.getElementById(id);
  const messages = $("messages"), prompt = $("prompt"), timeline = $("timeline");
  const welcome = $("welcome"), toast = $("toast");
  let activeTab = "timeline";
  let files = [];
  let toastTimer;
  const now = () => new Date().toLocaleTimeString("pt-BR",{hour:"2-digit",minute:"2-digit"});
  function notify(text){toast.textContent=text;toast.classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>toast.classList.remove("show"),2600)}
  function addMessage(role,text){const item=document.createElement("div");item.className="message "+role;const avatar=document.createElement("div");avatar.className="avatar";avatar.textContent=role==="user"?"T":"K";const bubble=document.createElement("div");bubble.className="bubble";bubble.textContent=text;if(role==="user"){item.append(bubble,avatar)}else{item.append(avatar,bubble)}messages.append(item);item.scrollIntoView({behavior:"smooth",block:"end"});welcome.classList.add("hidden")}
  function eventCard({title,detail,kind="info",command,output}){const card=document.createElement("div");card.className="activity-event "+(kind==="running"?"running":kind==="success"?"success":"");const dot=document.createElement("span");dot.className="event-dot";const heading=document.createElement("div");heading.className="event-title";heading.textContent=title;const time=document.createElement("span");time.className="event-time";time.textContent=now();heading.append(time);card.append(dot,heading);if(detail){const d=document.createElement("div");d.className="event-detail";d.textContent=detail;card.append(d)}if(command){const cmd=document.createElement("div");cmd.className="command-card";const head=document.createElement("div");head.className="command-head";head.textContent="TERMINAL · COMANDO EXECUTADO";const code=document.createElement("code");code.textContent="$ "+command;cmd.append(head,code);if(output!==undefined){const out=document.createElement("div");out.className="command-output";out.textContent="SAÍDA\n"+output;cmd.append(out)}card.append(cmd)}timeline.append(card);timeline.scrollTop=timeline.scrollHeight}
  const TOKEN_KEY="kelly_api_token";
  function apiToken(){
    let token=sessionStorage.getItem(TOKEN_KEY)||"";
    if(!token){token=window.prompt("Cole o token local da Kelly API (ele fica apenas nesta sessão):")||"";if(token)sessionStorage.setItem(TOKEN_KEY,token)}
    return token;
  }
  async function followTask(taskId,token){
    const response=await fetch("/api/tasks/"+encodeURIComponent(taskId)+"/events",{headers:{Authorization:"Bearer "+token}});
    if(!response.ok)throw new Error("Não foi possível acompanhar a tarefa (HTTP "+response.status+").");
    const reader=response.body.getReader(),decoder=new TextDecoder();let buffer="";
    while(true){
      const {value,done}=await reader.read();if(done)break;
      buffer+=decoder.decode(value,{stream:true});
      const blocks=buffer.split("\n\n");buffer=blocks.pop()||"";
      for(const block of blocks){
        const line=block.split("\n").find(x=>x.startsWith("data: "));if(!line)continue;
        let ev;try{ev=JSON.parse(line.slice(6))}catch(_){continue}
        const data=ev.data||{},kind=ev.type||"info";
        let command=data.command,output=data.output;
        if(data.stdout||data.stderr)output=[data.stdout||"",data.stderr?"STDERR\n"+data.stderr:""].filter(Boolean).join("\n");
        eventCard({title:ev.message||kind,detail:data.detail||data.step||"",kind:["completed","success"].includes(kind)?"success":kind.includes("fail")?"info":kind.includes("start")||kind==="working"?"running":"info",command,output});
        if(data.files||data.changed_files){files=(data.files||data.changed_files).map(x=>typeof x==="string"?x:(x.path||JSON.stringify(x)));$("filesView").textContent=files.join("\n")||"Nenhum arquivo alterado informado."}
        if(["completed","failed","cancelled"].includes(kind)){
          const task=await fetch("/api/tasks/"+encodeURIComponent(taskId),{headers:{Authorization:"Bearer "+token}}).then(x=>x.json());
          if(task.result)addMessage("assistant",task.result);
          if(task.error)addMessage("assistant","A tarefa falhou: "+task.error);
          $("taskSubtitle").textContent="Tarefa "+ev.status;return;
        }
      }
    }
  }
  async function submitTask(userText){
    const token=apiToken();if(!token){notify("Token não informado. A tarefa não foi enviada.");return}
    $("taskTitle").textContent="Enviando tarefa para Kelly Code…";
    $("taskSubtitle").textContent="Conectando à API local autenticada";
    timeline.innerHTML="";eventCard({title:"Conectando à Kelly API",detail:"Os eventos serão recebidos do motor conectado; nenhum comando fictício será exibido.",kind:"running"});
    try{
      const response=await fetch("/api/tasks",{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer "+token},body:JSON.stringify({prompt:userText,project_dir:"."})});
      const result=await response.json();if(!response.ok)throw new Error(result.error||("HTTP "+response.status));
      $("taskTitle").textContent="Tarefa "+result.id.slice(0,8);
      $("taskSubtitle").textContent="Kelly Code · acompanhando eventos reais";
      eventCard({title:"Tarefa recebida",detail:"ID: "+result.id,kind:"success"});
      await followTask(result.id,token);
    }catch(error){eventCard({title:"Não foi possível executar",detail:error.message,kind:"info"});$("taskSubtitle").textContent="API indisponível ou executor não configurado";addMessage("assistant","Não consegui iniciar a tarefa pela API local: "+error.message+". Verifique se a Kelly API está ativa e se o adaptador do motor foi configurado.")}
  }
  function send(){const value=prompt.value.trim();if(!value)return;addMessage("user",value);prompt.value="";prompt.style.height="auto";if(/@code|crie|criar|projeto|implemente|program|corrija|corrigir|instale|construa|desenvolva|site|aplicativo|configure|analise o repositório|execute os testes/i.test(value)){submitTask(value)}else{addMessage("assistant","A Kelly Light ainda precisa ser ligada a um modelo de conversa. Para tarefas de código e alterações no projeto, peça explicitamente a implementação e eu encaminho para a Kelly Code pela API local.")}}
  $("sendButton").addEventListener("click",send);
  prompt.addEventListener("keydown",e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();send()}});
  prompt.addEventListener("input",()=>{prompt.style.height="auto";prompt.style.height=Math.min(prompt.scrollHeight,130)+"px"});
  document.querySelectorAll(".suggestion").forEach(b=>b.addEventListener("click",()=>{prompt.value=b.dataset.prompt;send()}));
  $("newChat").addEventListener("click",()=>{messages.innerHTML="";timeline.innerHTML='<div class="empty-activity"><div class="empty-icon">⌁</div><strong>Seu trabalho, sem caixa-preta.</strong><p>Planos, comandos, saídas e testes aparecerão aqui em tempo real.</p></div>';$("taskTitle").textContent="Nenhuma tarefa ativa";$("taskSubtitle").textContent="Quando a Code trabalhar, você verá tudo aqui.";welcome.classList.remove("hidden");notify("Nova conversa iniciada")});
  $("safeAutoApprove").addEventListener("change",e=>notify(e.target.checked?"Aprovação automática de ações seguras ativada (prévia).":"Aprovação automática desativada."));
  document.querySelectorAll(".tab").forEach(b=>b.addEventListener("click",()=>{document.querySelectorAll(".tab").forEach(x=>x.classList.remove("active"));b.classList.add("active");activeTab=b.dataset.tab;$("timeline").classList.toggle("hidden",activeTab!=="timeline");$("filesView").classList.toggle("hidden",activeTab!=="files")}));
  $("openSidebar").addEventListener("click",()=>$("sidebar").classList.add("open"));
  $("closeSidebar").addEventListener("click",()=>$("sidebar").classList.remove("open"));
  $("voiceButton").addEventListener("click",()=>notify("Microfone e síntese de voz serão conectados ao serviço Kelly Live."));
  $("modelSelect").addEventListener("click",()=>notify("Seletor de modelos será conectado à família Kelly."));
})();
