/* Cliente da Kelly API local: comandos e resultados só são exibidos quando chegam do backend. */
(() => {
  const $ = (id) => document.getElementById(id);
  const messages = $("messages"), prompt = $("prompt"), timeline = $("timeline");
  const welcome = $("welcome"), toast = $("toast");
  let activeTab = "timeline";
  let files = [];
  let pendingAttachments = [];
  let liveMode = false;
  let recognition = null;
  let awaitingReply = false;
  let chatHistory = [];
  let chatSessionId = sessionStorage.getItem("overcode-session-id") || "";
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
      const health=await fetch("/api/health").then(r=>r.json());
      if(!health.executor_configured){addMessage("assistant","A API de conversa está pronta, mas o executor da Kelly Code ainda não foi conectado ao motor do terminal. Por isso não vou fingir que executei essa tarefa.");$("taskSubtitle").textContent="Executor de código não conectado";return}
      const response=await fetch("/api/tasks",{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer "+token},body:JSON.stringify({prompt:userText,project_dir:"."})});
      const result=await response.json();if(!response.ok)throw new Error(result.error||("HTTP "+response.status));
      $("taskTitle").textContent="Tarefa "+result.id.slice(0,8);
      $("taskSubtitle").textContent="Kelly Code · acompanhando eventos reais";
      eventCard({title:"Tarefa recebida",detail:"ID: "+result.id,kind:"success"});
      await followTask(result.id,token);
    }catch(error){eventCard({title:"Não foi possível executar",detail:error.message,kind:"info"});$("taskSubtitle").textContent="API indisponível ou executor não configurado";addMessage("assistant","Não consegui iniciar a tarefa pela API local: "+error.message+". Verifique se a Kelly API está ativa e se o adaptador do motor foi configurado.")}
  }
  function speakAnswer(text){
    if(!liveMode || !("speechSynthesis" in window)) return;
    window.speechSynthesis.cancel();
    const utterance=new SpeechSynthesisUtterance(text);
    utterance.lang="pt-BR";
    const chosen=$("voiceSelect").value;
    const voices=window.speechSynthesis.getVoices();
    utterance.voice=voices.find(v=>v.name===chosen)||voices.find(v=>v.lang.toLowerCase().startsWith("pt"))||null;
    utterance.onend=()=>{awaitingReply=false;if(liveMode) startRecognition()};
    utterance.onerror=()=>{awaitingReply=false;if(liveMode) startRecognition()};
    window.speechSynthesis.speak(utterance);
  }
  async function chatWithKelly(message, attachmentIds=[], fromVoice=false){
    const token=apiToken();if(!token){notify("Configure o token da Kelly API em Configurações.");if(fromVoice){awaitingReply=false;if(liveMode)startRecognition()}return}
    try{
      const r=await fetch("/api/chat",{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer "+token},body:JSON.stringify({message,attachments:attachmentIds,history:chatHistory,session_id:chatSessionId})});
      const d=await r.json();if(!r.ok)throw new Error(d.error||("HTTP "+r.status));
      const answer=d.answer||"Sem resposta do provedor.";
      if(d.session_id){chatSessionId=d.session_id;sessionStorage.setItem("overcode-session-id",chatSessionId)}
      chatHistory.push({role:"user",content:message},{role:"assistant",content:answer});chatHistory=chatHistory.slice(-30);
      addMessage("assistant",answer);
      if(liveMode || fromVoice) speakAnswer(answer);
      if(d.task_id){
        $("taskTitle").textContent="Tarefa "+d.task_id.slice(0,8);
        $("taskSubtitle").textContent="Kelly Code · trabalhando em segundo plano";
        timeline.innerHTML="";
        eventCard({title:"Tarefa enviada para Kelly Code",detail:"A conversa continua enquanto a tarefa é executada.",kind:"success"});
        followTask(d.task_id,token).catch(e=>eventCard({title:"Falha ao acompanhar tarefa",detail:e.message}));
      }
      return answer;
    }catch(e){if(fromVoice){awaitingReply=false;if(liveMode)startRecognition()}addMessage("assistant","Falha na conversa: "+e.message+". Confira a API Key em Configurações.");return ""}
  }
  function renderAttachments(){
    const area=$("attachmentList");area.innerHTML="";
    pendingAttachments.forEach((file,index)=>{
      const chip=document.createElement("span");chip.className="attachment-chip";
      const name=document.createElement("span");name.textContent=file.name;
      const remove=document.createElement("button");remove.type="button";remove.textContent="×";remove.setAttribute("aria-label","Remover "+file.name);
      remove.addEventListener("click",()=>{pendingAttachments.splice(index,1);renderAttachments()});
      chip.append(name,remove);area.append(chip);
    });
  }
  async function uploadSelected(file){
    if(file.size>12*1024*1024){notify("Cada arquivo pode ter no máximo 12 MB.");return}
    const token=apiToken();if(!token)return;
    const encoded=await new Promise((resolve,reject)=>{
      const reader=new FileReader();reader.onload=()=>resolve(String(reader.result).split(",")[1]||"");reader.onerror=()=>reject(new Error("Não consegui ler o arquivo."));reader.readAsDataURL(file);
    });
    const response=await fetch("/api/files",{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer "+token},body:JSON.stringify({name:file.name,content_type:file.type,content_base64:encoded})});
    const data=await response.json();if(!response.ok)throw new Error(data.error||("HTTP "+response.status));
    pendingAttachments.push(data);renderAttachments();notify("Arquivo anexado: "+data.name);
  }
  $("attachButton").addEventListener("click",()=>$("fileInput").click());
  $("fileInput").addEventListener("change",async e=>{
    for(const file of Array.from(e.target.files||[])){
      try{await uploadSelected(file)}catch(error){notify("Falha no anexo: "+error.message)}
    }
    e.target.value="";
  });
  function populateVoices(){
    if(!("speechSynthesis" in window))return;
    const select=$("voiceSelect"),previous=select.value;
    const saved=localStorage.getItem("overcode-voice")||previous;
    const voices=window.speechSynthesis.getVoices().slice().sort((a,b)=>{
      const ap=a.lang.toLowerCase().startsWith("pt")?0:1,bp=b.lang.toLowerCase().startsWith("pt")?0:1;
      return ap-bp||a.lang.localeCompare(b.lang)||a.name.localeCompare(b.name);
    });
    select.innerHTML='<option value="">Voz automática (português)</option>';
    voices.forEach(v=>{const option=document.createElement("option");option.value=v.name;option.textContent=v.name+" · "+v.lang;select.append(option)});
    if(voices.some(v=>v.name===saved))select.value=saved;
    select.title=voices.length?"Escolha uma voz instalada no dispositivo":"Nenhuma voz carregada pelo navegador";
  }
  function startRecognition(){
    if(!liveMode || !recognition)return;
    try{recognition.start();$("voiceStatus").textContent="Ouvindo…"}catch(_){}
  }
  function stopLive(){
    liveMode=false;
    awaitingReply=false;
    if(recognition){try{recognition.onend=null;recognition.stop()}catch(_){}recognition=null}
    if("speechSynthesis" in window)window.speechSynthesis.cancel();
    $("voiceButton").classList.remove("active");
    $("voiceButton").textContent="◉ Live";
    $("voiceButton").title="Iniciar modo Live";
    $("voiceStatus").textContent="Modo Live desligado";
  }
  function toggleLive(){
    if(liveMode){stopLive();return}
    const SpeechRecognition=window.SpeechRecognition||window.webkitSpeechRecognition;
    if(!SpeechRecognition){notify("Reconhecimento de voz não disponível neste navegador. Tente o Chrome atualizado.");return}
    if(!("speechSynthesis" in window)){notify("Este navegador não oferece síntese de voz.");return}
    recognition=new SpeechRecognition();
    recognition.lang="pt-BR";recognition.continuous=true;recognition.interimResults=false;
    recognition.onresult=async event=>{
      const result=event.results[event.results.length-1];
      const transcript=result&&result[0]?result[0].transcript.trim():"";
      if(!transcript)return;
      awaitingReply=true;
      try{recognition.stop()}catch(_){}
      addMessage("user",transcript);
      await chatWithKelly(transcript,[],true);
    };
    recognition.onerror=event=>{
      if(event.error==="not-allowed"||event.error==="service-not-allowed"){notify("Permita o acesso ao microfone nas configurações do navegador.");stopLive()}
      else $("voiceStatus").textContent="Voz: "+event.error;
    };
    recognition.onend=()=>{if(liveMode && !awaitingReply && !window.speechSynthesis.speaking)setTimeout(startRecognition,350)};
    liveMode=true;
    $("voiceButton").classList.add("active");
    $("voiceButton").textContent="■ Parar Live";
    $("voiceButton").title="Parar modo Live";
    populateVoices();startRecognition();
  }
  $("voiceButton").addEventListener("click",toggleLive);
  $("voiceSelect").addEventListener("change",e=>{
    localStorage.setItem("overcode-voice",e.target.value);
    const selected=e.target.selectedOptions[0];
    $("voiceStatus").textContent=e.target.value?"Voz: "+selected.textContent:"Voz automática";
    if(liveMode && window.speechSynthesis.speaking)window.speechSynthesis.cancel();
  });
  if("speechSynthesis" in window){populateVoices();window.speechSynthesis.onvoiceschanged=populateVoices;}
  function send(){
    const value=prompt.value.trim();if(!value && !pendingAttachments.length)return;
    const attachmentIds=pendingAttachments.map(x=>x.id);
    const suffix=pendingAttachments.length?" · Anexos: "+pendingAttachments.map(x=>x.name).join(", "):"";
    const message=value||"Analise os arquivos anexados.";
    addMessage("user",message+suffix);
    prompt.value="";prompt.style.height="auto";
    pendingAttachments=[];renderAttachments();
    chatWithKelly(message,attachmentIds);
  }
  $("sendButton").addEventListener("click",send);
  prompt.addEventListener("keydown",e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();send()}});
  prompt.addEventListener("input",()=>{prompt.style.height="auto";prompt.style.height=Math.min(prompt.scrollHeight,130)+"px"});
  document.querySelectorAll(".suggestion").forEach(b=>b.addEventListener("click",()=>{prompt.value=b.dataset.prompt;send()}));
  $("newChat").addEventListener("click",()=>{messages.innerHTML="";chatHistory=[];chatSessionId="";sessionStorage.removeItem("overcode-session-id");timeline.innerHTML='<div class="empty-activity"><div class="empty-icon">⌁</div><strong>Seu trabalho, sem caixa-preta.</strong><p>Planos, comandos, saídas e testes aparecerão aqui em tempo real.</p></div>';$("taskTitle").textContent="Nenhuma tarefa ativa";$("taskSubtitle").textContent="Quando a Code trabalhar, você verá tudo aqui.";welcome.classList.remove("hidden");notify("Nova conversa iniciada")});
  $("safeAutoApprove").addEventListener("change",e=>notify(e.target.checked?"Aprovação automática de ações seguras ativada (prévia).":"Aprovação automática desativada."));
  document.querySelectorAll(".tab").forEach(b=>b.addEventListener("click",()=>{document.querySelectorAll(".tab").forEach(x=>x.classList.remove("active"));b.classList.add("active");activeTab=b.dataset.tab;$("timeline").classList.toggle("hidden",activeTab!=="timeline");$("filesView").classList.toggle("hidden",activeTab!=="files")}));
  $("openSidebar").addEventListener("click",()=>$("sidebar").classList.add("open"));
  $("closeSidebar").addEventListener("click",()=>$("sidebar").classList.remove("open"));
  async function refreshConnection(){
    const el=$("connectionStatus");
    try{
      const response=await fetch("/api/health",{cache:"no-store"});
      if(!response.ok)throw new Error("HTTP "+response.status);
      const data=await response.json();
      el.innerHTML="<i></i> API "+(data.ok?"online":"offline")+(data.executor_configured?" · Code ativa":" · chat disponível");
      el.classList.toggle("connected",!!data.ok);
      el.title="Última verificação: "+now();
    }catch(_){
      el.innerHTML="<i></i> API desconectada";
      el.classList.remove("connected");
      el.title="Não foi possível alcançar /api/health";
    }
  }
  refreshConnection();
  setInterval(refreshConnection,15000);
})();
