(() => {
"use strict";
const apps=[
{id:"over-browser",name:"Over Browser",category:"Internet",icon:"◉",tag:"Navegador",size:"A definir",description:"Navegue na internet com abas, favoritos, histórico e privacidade sob seu controle.",state:"planned"},
{id:"over-files",name:"OverFiles",category:"Sistema",icon:"▰",tag:"Arquivos",size:"Muito leve",description:"Organize pastas, arquivos, unidades e transferências em uma interface simples.",state:"planned"},
{id:"over-terminal",name:"OverTerminal",category:"Ferramentas",icon:"⌘",tag:"Terminal",size:"Muito leve",description:"Terminal rápido com abas, histórico, perfis e atalhos para tarefas do sistema.",state:"planned"},
{id:"over-settings",name:"OverSettings",category:"Sistema",icon:"⚙",tag:"Personalização",size:"Muito leve",description:"Personalize aparência, painel, atalhos, rede, energia, tela e notificações.",state:"planned"},
{id:"over-control",name:"OverControl",category:"Sistema",icon:"◈",tag:"Central de controle",size:"Muito leve",description:"Acesse rapidamente Wi-Fi, áudio, brilho, bateria e informações do computador.",state:"planned"},
{id:"over-media",name:"OverMedia",category:"Multimídia",icon:"♫",tag:"Multimídia",size:"A definir",description:"Central leve para abrir e organizar suas mídias locais.",state:"planned"},
{id:"over-archive",name:"OverArchive",category:"Ferramentas",icon:"▤",tag:"Compactação",size:"A definir",description:"Crie e extraia arquivos compactados com uma interface direta.",state:"planned"},
{id:"over-store",name:"OverStore",category:"Sistema",icon:"✣",tag:"Loja",size:"Leve",description:"Descubra, instale, atualize e remova aplicativos da OverOS.",state:"installed"}
];
const $=s=>document.querySelector(s), catalog=$("#catalog"), installed=$("#installed"), search=$("#search"), dialog=$("#detail");
let category="Tudo", query="";
const installedIds=new Set(JSON.parse(localStorage.getItem("overos-installed")||'["over-store"]'));
const escapeHtml=s=>String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
function toast(s){const t=$("#toast");t.textContent=s;t.classList.add("show");clearTimeout(toast.timer);toast.timer=setTimeout(()=>t.classList.remove("show"),2400)}
function render(){
 const list=apps.filter(a=>(category==="Tudo"||a.category===category)&&(a.name+" "+a.description+" "+a.category).toLowerCase().includes(query));
 $("#sectionTitle").textContent=query?"Resultados da pesquisa":category==="Tudo"?"Recomendados para você":category;
 $("#count").textContent=list.length+" aplicativos";
 catalog.innerHTML=list.map(a=>`<article class="app-card"><div class="card-top"><div class="app-icon">${a.icon}</div><span class="tag">${escapeHtml(a.tag)}</span></div><h3>${escapeHtml(a.name)}</h3><p>${escapeHtml(a.description)}</p><div class="card-bottom"><span class="size">${escapeHtml(a.size)}</span><button class="install" data-id="${a.id}" data-action="${installedIds.has(a.id)?"open":"install"}">${installedIds.has(a.id)?"Detalhes":"Saiba mais"}</button></div></article>`).join("")||'<div class="empty">Nenhum aplicativo encontrado. Tente outra pesquisa.</div>';
 renderInstalled();
}
function renderInstalled(){const list=apps.filter(a=>installedIds.has(a.id));installed.innerHTML=list.length?list.map(a=>`<div class="installed-item">${a.icon} &nbsp;${escapeHtml(a.name)} <span class="tag">Instalado</span></div>`).join(""):'<div class="empty">Seus aplicativos instalados aparecerão aqui.</div>'}
function details(id){const a=apps.find(x=>x.id===id);if(!a)return;$("#detailBody").innerHTML=`<div class="app-icon">${a.icon}</div><p class="eyebrow">${escapeHtml(a.category)}</p><h2>${escapeHtml(a.name)}</h2><p class="muted">${escapeHtml(a.description)}</p><p class="muted">Estado: ${installedIds.has(id)?"Instalado":"Planejado para a OverOS V2"}</p><p class="muted">A instalação será habilitada quando o pacote oficial estiver disponível e verificado.</p>`;dialog.showModal()}
catalog.addEventListener("click",e=>{const b=e.target.closest("button[data-id]");if(b)details(b.dataset.id)});
$("#nav").addEventListener("click",e=>{const b=e.target.closest("[data-category]");if(!b)return;category=b.dataset.category;document.querySelectorAll(".nav").forEach(n=>n.classList.toggle("active",n===b));render()});
search.addEventListener("input",()=>{query=search.value.trim().toLowerCase();render()});
$("#heroBrowse").addEventListener("click",()=>{search.focus();window.scrollTo({top:0,behavior:"smooth"})});
$("#updates").addEventListener("click",()=>toast("Você está usando o catálogo local. Verificação online ainda não configurada."));
$(".close").addEventListener("click",()=>dialog.close());
dialog.addEventListener("click",e=>{if(e.target===dialog)dialog.close()});
document.addEventListener("keydown",e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==="k"){e.preventDefault();search.focus()}if(e.key==="Escape"&&dialog.open)dialog.close()});
render();
})();