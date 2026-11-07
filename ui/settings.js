(() => {
  const $ = id => document.getElementById(id);
  const tokenKey = "kelly_api_token";
  const token = () => sessionStorage.getItem(tokenKey) || "";
  const feedback = (id, message) => { const el=$(id); if(el) el.textContent=message; };
  const workspace = document.querySelector(".workspace");
  const settings = $("settingsView");
  const simple = $("simpleView");
  const labels = {projects:"Projetos",history:"Histórico",files:"Arquivos",connectors:"Conectores"};
  function show(view) {
    workspace.classList.toggle("hidden", view!=="chat");
    settings.classList.toggle("hidden", view!=="settings");
    simple.classList.toggle("hidden", !labels[view]);
    if(labels[view]) { $("simpleTitle").textContent=labels[view]; $("simpleDescription").textContent="Esta área será conectada aos recursos do motor OverCode."; }
    document.querySelectorAll(".nav-item[data-view]").forEach(b=>b.classList.toggle("active",b.dataset.view===view));
    $("sidebar").classList.remove("open");
    if(view==="settings") load();
  }
  async function authorized(path, options={}) {
    const key=token();
    if(!key) throw new Error("Informe e guarde o Token da Kelly API nesta tela.");
    options.headers={...(options.headers||{}),Authorization:"Bearer "+key};
    const r=await fetch(path,options), data=await r.json();
    if(!r.ok) throw new Error(data.error||("HTTP "+r.status));
    return data;
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
  fetch("/api/health").then(r=>r.json()).then(d=>{const el=$("connectionStatus");el.innerHTML="<i></i> API "+(d.ok?"online":"offline");}).catch(()=>{$("connectionStatus").textContent="API indisponível";});
  $("modelSelect").addEventListener("click",()=>show("settings"));
})();