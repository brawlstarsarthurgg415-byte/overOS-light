(() => {
  const $ = id => document.getElementById(id);
  const tokenKey = "kelly_api_token";
  const token = () => sessionStorage.getItem(tokenKey) || "";
  const feedback = (id, message) => { const el=$(id); if(el) el.textContent=message; };
  const workspace = document.querySelector(".workspace");
  const settings = $("settingsView");
  const simple = $("simpleView");
  const content = $("simpleContent");
  const labels = {projects:"Projetos",history:"Histórico",files:"Arquivos",connectors:"Conectores",status:"Status"};
  function card(title,description,tag){
    const el=document.createElement("article");el.className="capability-card";
    const h=document.createElement("h3");h.textContent=title;
    const p=document.createElement("p");p.textContent=description;
    const b=document.createElement("span");b.className="capability-tag";b.textContent=tag;
    el.append(h,p,b);return el;
  }
  function empty(message){content.innerHTML="";const p=document.createElement("p");p.className="simple-empty";p.textContent=message;content.append(p)}
  async function authorized(path, options={}) {
    const key=token();
    if(!key) throw new Error("Informe e guarde o Token da Kelly API em Configurações.");
    options.headers={...(options.headers||{}),Authorization:"Bearer "+key};
    const r=await fetch(path,options);
    const data=await r.json();
    if(!r.ok) throw new Error(data.error||("HTTP "+r.status));
    return data;
  }
  async function downloadFile(file,endpoint){
    const r=await fetch(endpoint,{headers:{Authorization:"Bearer "+token()}});
    if(!r.ok)throw new Error("Falha ao baixar arquivo (HTTP "+r.status+").");
    const blob=await r.blob(),url=URL.createObjectURL(blob),a=document.createElement("a");
    a.href=url;a.download=file.name;a.style.display="none";document.body.append(a);a.click();a.remove();URL.revokeObjectURL(url);
  }
  async function loadFiles(){
    empty("Carregando arquivos…");
    try{
      const [uploaded,project]=await Promise.all([authorized("/api/files"),authorized("/api/project-files")]);
      content.innerHTML="";
      const addHeading=label=>{const h=document.createElement("h3");h.textContent=label;h.style.gridColumn="1/-1";content.append(h)};
      const addRow=(file,endpoint,subtitle,canDelete)=>{
        const row=document.createElement("div");row.className="file-row";
        const info=document.createElement("div");info.className="file-info";
        const name=document.createElement("strong");name.textContent=file.name;
        const meta=document.createElement("small");meta.textContent=(file.size/1024).toFixed(1)+" KB · "+subtitle;
        info.append(name,meta);
        const actions=document.createElement("div");actions.className="file-actions";
        const dl=document.createElement("button");dl.className="secondary-btn";dl.textContent="Baixar";dl.addEventListener("click",()=>downloadFile(file,endpoint).catch(e=>notify(e.message)));
        actions.append(dl);
        if(canDelete){
          const del=document.createElement("button");del.className="secondary-btn";del.textContent="Excluir";del.addEventListener("click",async()=>{
            if(!confirm("Excluir "+file.name+"?"))return;
            try{await authorized("/api/files/"+encodeURIComponent(file.id),{method:"DELETE"});loadFiles()}catch(e){notify(e.message)}
          });
          actions.append(del);
        }
        row.append(info,actions);content.append(row);
      };
      addHeading("Arquivos enviados");
      if(uploaded.files.length)uploaded.files.forEach(file=>addRow(file,"/api/files/"+encodeURIComponent(file.id),file.content_type,true));
      else {const p=document.createElement("p");p.className="simple-empty";p.textContent="Nenhum anexo enviado.";content.append(p)}
      addHeading("Arquivos do projeto");
      if(project.files.length)project.files.forEach(file=>{
        const endpoint="/api/project-files/"+file.path.split("/").map(encodeURIComponent).join("/");
        addRow(file,endpoint,"gerado ou salvo no projeto",false);
      });
      else {const p=document.createElement("p");p.className="simple-empty";p.textContent="Ainda não há arquivos no diretório de projetos.";content.append(p)}
    }catch(e){empty(e.message)}
  }
  async function loadConnectors(){
    empty("Consultando ferramentas e integrações…");
    try{
      const d=await authorized("/api/capabilities");content.innerHTML="";
      content.append(card("API local","Comunicação autenticada entre a interface, a Kelly e o servidor local.","Disponível"));
      content.append(card("Provedor de IA",(d.provider.configured?d.provider.name+" · "+d.provider.model:"Nenhuma chave de provedor configurada."),d.provider.configured?"Conectado":"Configurar"));
      content.append(card("Kelly Code",d.executor_configured?"Executor de tarefas configurado.":"O executor ainda precisa ser conectado ao motor de código.",d.executor_configured?"Ativo":"Pendente"));
      content.append(card("Voz", "Escolha entre as vozes instaladas no navegador ou dispositivo. O reconhecimento e a fala funcionam em modo Live; áudio bidirecional em streaming ainda não está integrado.", "Voz do dispositivo"));
      content.append(card("Arquivos","Upload e download autenticados, limite de 12 MB por arquivo. Arquivos de texto podem ser incluídos no contexto da conversa.","Disponível"));
      const h=document.createElement("h3");h.textContent="Ferramentas expostas pela API";content.append(h);
      const list=document.createElement("div");list.className="tool-list";
      d.tools.forEach(t=>{const span=document.createElement("code");span.textContent=t;list.append(span)});content.append(list);
      const example=document.createElement("pre");example.className="api-example";example.textContent="GET  /api/health\nGET  /api/capabilities\nPOST /api/chat\nPOST /api/files\nGET  /api/files/{id}\nGET  /api/tasks/{id}/events";content.append(example);
    }catch(e){empty(e.message)}
  }
  async function loadStatus(){
    empty("Verificando serviços…");
    try{
      const [health,caps]=await Promise.all([fetch("/api/health").then(r=>r.json()),authorized("/api/capabilities")]);
      content.innerHTML="";
      content.append(card("Kelly API",health.ok?"Respondendo em /api/health.":"Não respondeu.",health.ok?"Online":"Offline"));
      content.append(card("Kelly Light",caps.provider.configured?"Provedor configurado para responder conversas.":"Configure um provedor e uma API Key nas Configurações.",caps.provider.configured?"Pronta":"Configuração pendente"));
      content.append(card("Kelly Code",caps.executor_configured?"Executor declarado no servidor.":"Executor declarado, mas ainda confirme o motor de execução.",caps.executor_configured?"Configurado":"Pendente"));
      content.append(card("Arquivos",caps.files.upload?"Upload e download habilitados.":"Anexos indisponíveis.",caps.files.upload?"OK":"Indisponível"));
      content.append(card("Modo Live",caps.voice.realtime_streaming?"Áudio em streaming habilitado.":"Voz por reconhecimento e síntese do navegador; sem streaming bidirecional.",caps.voice.realtime_streaming?"Live streaming":"Voz do navegador"));
      const refresh=document.createElement("button");refresh.className="primary-btn";refresh.textContent="Atualizar status";refresh.addEventListener("click",loadStatus);content.append(refresh);
    }catch(e){empty("Não foi possível consultar o status: "+e.message)}
  }
  function show(view) {
    workspace.classList.toggle("hidden", view!=="chat");
    settings.classList.toggle("hidden", view!=="settings");
    simple.classList.toggle("hidden", !labels[view]);
    if(labels[view]) {
      $("simpleTitle").textContent=labels[view];
      $("simpleDescription").textContent=view==="files"?"Arquivos enviados e downloads.":view==="connectors"?"Serviços, ferramentas e formas de integração disponíveis.":view==="status"?"Saúde dos serviços e recursos do OverCode.":"Esta área será conectada aos recursos do motor OverCode.";
      if(view==="files")loadFiles();
      if(view==="connectors")loadConnectors();
      if(view==="status")loadStatus();
      if(view==="projects"||view==="history")empty("Esta área será ativada na próxima etapa.");
    }
    document.querySelectorAll(".nav-item[data-view]").forEach(b=>b.classList.toggle("active",b.dataset.view===view));
    $("sidebar").classList.remove("open");
    if(view==="settings") load();
  }
  async function load() {
    $("apiTokenInput").value=token();
    try {
      const c=await authorized("/api/providers");
      $("providerSelect").value=c.provider||"openai-compatible";
      $("providerModel").value=c.model||"";
      $("providerBaseUrl").value=c.base_url||"https://api.openai.com/v1";
      $("keyStatus").textContent=c.has_api_key?"Já existe uma chave salva no servidor. Digite outra para substituí-la.":"Nenhuma chave cadastrada.";
      fields();
    } catch(e) { feedback("providerFeedback",e.message); }
  }
  function fields() {
    const gemini=$("providerSelect").value==="gemini";
    $("baseUrlGroup").classList.toggle("hidden",gemini);
    if(gemini&&!$("providerModel").value) $("providerModel").value="gemini-2.5-flash";
    if(!gemini&&!$("providerBaseUrl").value) $("providerBaseUrl").value="https://api.openai.com/v1";
  }
  $("saveApiToken").addEventListener("click",()=> {
    const value=$("apiTokenInput").value.trim();
    if(!value) { feedback("apiFeedback","Cole o token gerado no servidor."); return; }
    sessionStorage.setItem(tokenKey,value);
    feedback("apiFeedback","Token guardado somente nesta sessão.");
    load();
  });
  $("checkApi").addEventListener("click",async()=> {
    try { const d=await fetch("/api/health").then(r=>r.json()); feedback("apiFeedback",d.ok?"API online · "+(d.executor_configured?"executor conectado":"executor de código ainda não configurado"):"API offline"); }
    catch(e) { feedback("apiFeedback","API indisponível: "+e.message); }
  });
  $("providerSelect").addEventListener("change",fields);
  $("saveProvider").addEventListener("click",async()=> {
    feedback("providerFeedback","Salvando configuração no servidor…");
    try {
      const body={provider:$("providerSelect").value,model:$("providerModel").value.trim(),base_url:$("providerBaseUrl").value.trim(),api_key:$("providerKey").value};
      const d=await authorized("/api/providers",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
      $("providerKey").value="";
      feedback("keyStatus",d.has_api_key?"Chave salva no servidor; ela não é exibida novamente.":"Nenhuma chave cadastrada.");
      feedback("providerFeedback","Configuração salva. Agora teste a conexão.");
    } catch(e) { feedback("providerFeedback","Não foi possível salvar: "+e.message); }
  });
  $("testProvider").addEventListener("click",async()=> {
    feedback("providerFeedback","Testando conexão…");
    try { const d=await authorized("/api/providers/test",{method:"POST",headers:{"Content-Type":"application/json"},body:"{}"}); feedback("providerFeedback","Conexão OK · "+d.message); }
    catch(e) { feedback("providerFeedback","Falha no teste: "+e.message); }
  });
  document.querySelectorAll(".nav-item[data-view]").forEach(b=>b.addEventListener("click",()=>show(b.dataset.view)));
  fetch("/api/health").then(r=>r.json()).then(d=>{const el=$("connectionStatus");el.innerHTML="<i></i> API "+(d.ok?"online":"offline")+(d.executor_configured?" · Code ativa":" · chat disponível"); $("safeAutoApprove").disabled=!d.executor_configured;}).catch(()=>{$("connectionStatus").textContent="API indisponível";});
  $("modelSelect").addEventListener("click",()=>show("settings"));
  $("newChat").addEventListener("click",()=>show("chat"));
  function notify(message){const el=$("toast");el.textContent=message;el.classList.add("show");setTimeout(()=>el.classList.remove("show"),2500)}
})();
