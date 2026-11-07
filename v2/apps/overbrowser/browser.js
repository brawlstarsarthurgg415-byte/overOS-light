(() => {"use strict";
const $=s=>document.querySelector(s), notice=$("#notice"), address=$("#address"), startAddress=$("#startAddress"), start=$("#start"), engine=$("#engine"), menu=$("#menuDialog");
let privateMode=false, tabCount=1, history=[], favorites=JSON.parse(localStorage.getItem("over-browser-favorites")||"[]");
function toast(s){notice.textContent=s;notice.classList.add("show");clearTimeout(toast.timer);toast.timer=setTimeout(()=>notice.classList.remove("show"),2300)}
function normalize(v){v=v.trim();if(!v)return "";if(/^https?:\/\//i.test(v))return v;if(/^[\w-]+\.[a-z]{2,}/i.test(v))return "https://"+v;return "https://www.google.com/search?q="+encodeURIComponent(v)}
function navigate(value){const url=normalize(value);if(!url)return;address.value=url;start.hidden=true;engine.hidden=false;const host=window.overBrowser;if(host&&typeof host.navigate==="function"){host.navigate(url,{private:privateMode})}else{$("#engineMessage").textContent="O endereço foi reconhecido: "+url+". A navegação real depende da integração do motor web nativo, ainda não incluída nesta prévia da interface."}if(!privateMode){history.push({url,at:Date.now()});localStorage.setItem("over-browser-history",JSON.stringify(history.slice(-100)))}}
$("#addressForm").addEventListener("submit",e=>{e.preventDefault();navigate(address.value)});
$("#startForm").addEventListener("submit",e=>{e.preventDefault();navigate(startAddress.value)});
$("#back").addEventListener("click",()=>{if(window.overBrowser?.back)window.overBrowser.back();else toast("Voltar: aguardando integração do motor web")});
$("#forward").addEventListener("click",()=>{if(window.overBrowser?.forward)window.overBrowser.forward();else toast("Avançar: aguardando integração do motor web")});
$("#reload").addEventListener("click",()=>{if(window.overBrowser?.reload)window.overBrowser.reload();else toast("Recarregar: aguardando integração do motor web")});
$("#home").addEventListener("click",home);$("#returnHome").addEventListener("click",home);
function home(){engine.hidden=true;start.hidden=false;address.value=""}
$("#privacy").addEventListener("click",()=>{privateMode=!privateMode;$("#privacy").classList.toggle("enabled",privateMode);$("#privacy small").textContent=privateMode?"Ativado":"Privado";toast(privateMode?"Modo privado ativado nesta sessão":"Modo privado desativado")});
$("#bookmark").addEventListener("click",()=>{const u=address.value;if(!u){toast("Abra uma página antes de favoritar");return}if(!favorites.includes(u))favorites.push(u);localStorage.setItem("over-browser-favorites",JSON.stringify(favorites));toast("Favorito salvo neste perfil")});
$("#newTab").addEventListener("click",()=>{tabCount++;const b=document.createElement("button");b.className="tab active";b.innerHTML="<span>◉</span> Nova guia <b>×</b>";document.querySelectorAll(".tab").forEach(x=>x.classList.remove("active"));$("#tabs").insertBefore(b,$("#newTab"));home();toast("Nova guia criada")});
document.querySelectorAll("[data-url]").forEach(b=>b.addEventListener("click",()=>navigate(b.dataset.url)));
$("#addShortcut").addEventListener("click",()=>toast("Atalhos personalizados serão configurados em OverSettings"));
$("#menu").addEventListener("click",()=>menu.showModal());$("#closeMenu").addEventListener("click",()=>menu.close());
menu.addEventListener("click",e=>{const b=e.target.closest("[data-action]");if(!b)return;menu.close();const a=b.dataset.action;if(a==="favorites")toast(favorites.length?favorites.join(" • "):"Nenhum favorito salvo");else if(a==="history")toast(privateMode?"Histórico não é salvo no modo privado":(history.length+" páginas nesta sessão"));else toast(a==="downloads"?"Gerenciador de downloads ainda não integrado":"Configurações de privacidade em desenvolvimento")});
history=JSON.parse(localStorage.getItem("over-browser-history")||"[]");
})();