/* Painel de demonstração. A ligação com API autenticada/SSE será integrada na próxima etapa. */
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
  function showDemo(userText){
    $("taskTitle").textContent="Demonstração: analisar projeto";
    $("taskSubtitle").textContent="Kelly Code · atividade simulada, sem executar comandos reais";
    timeline.innerHTML="";
    eventCard({title:"Pedido encaminhado para Kelly Code",detail:"A Kelly Light continua disponível para conversar.",kind:"success"});
    eventCard({title:"Analisando a estrutura do projeto",detail:"Inspecionando diretórios e identificando os arquivos relevantes.",kind:"running"});
    setTimeout(()=>eventCard({title:"Comando executado",detail:"Exemplo ilustrativo de como o comando e a saída aparecerão quando a integração estiver ativa.",command:"find . -maxdepth 2 -type f | sort",output:"./README.md\n./src/main.py\n./tests/test_main.py\n./ui/index.html"}),450);
    setTimeout(()=>eventCard({title:"Plano preparado",detail:"1. Ler os arquivos principais. 2. Identificar alterações. 3. Implementar em etapas. 4. Executar testes.",kind:"success"}),900);
    setTimeout(()=>{eventCard({title:"Aguardando integração real",detail:"Esta tela é uma prévia visual. Nenhum comando foi executado pelo painel.",kind:"info"});$("taskSubtitle").textContent="Prévia visual · aguardando API real"},1400);
    addMessage("assistant","Entendi. Vou encaminhar esse tipo de trabalho para a Kelly Code. Esta versão mostra uma prévia de como você verá os comandos e os resultados; a execução real será conectada ao motor Overcode na próxima etapa.");
  }
  function send(){const value=prompt.value.trim();if(!value)return;addMessage("user",value);prompt.value="";prompt.style.height="auto";if(/@code|crie|criar|projeto|implemente|program|corrija|corrigir|instale|construa|desenvolva|site|aplicativo/i.test(value)){showDemo(value)}else{addMessage("assistant","A conversa local de demonstração recebeu sua mensagem. Para respostas reais, este painel precisa ser conectado ao modelo Kelly Live.")}}
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
