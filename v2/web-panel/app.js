'use strict';
const $ = (id) => document.getElementById(id);
const hasNum = (n) => n !== null && n !== undefined && n !== '' && Number.isFinite(Number(n));
const fmt = (n, unit = '') => hasNum(n) ? `${Math.round(Number(n))}${unit}` : '—';
const setText = (id, value) => { const el = $(id); if (el) el.textContent = value ?? '—'; };
const setMeter = (id, value) => { const el = $(id); if (el) el.style.width = `${Math.max(0, Math.min(100, Number(value) || 0))}%`; };
let toastTimer;
function toast(message) {
  const el = $('toast'); el.textContent = message; el.classList.add('show');
  clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('show'), 2600);
}
function renderServices(items) {
  const root = $('serviceList'); root.replaceChildren();
  if (!Array.isArray(items) || !items.length) {
    const empty = document.createElement('div'); empty.className = 'empty-state'; empty.textContent = 'A API respondeu, mas ainda não informou serviços.'; root.append(empty); return;
  }
  for (const item of items.slice(0, 12)) {
    const row = document.createElement('div'); row.className = 'service-row';
    const name = document.createElement('span'); name.className = 'service-name';
    const dot = document.createElement('i'); dot.className = `service-dot${item.active ? '' : ' off'}`;
    const label = document.createElement('span'); label.textContent = String(item.name || 'Serviço');
    name.append(dot, label);
    const state = document.createElement('span'); state.className = 'service-state'; state.textContent = item.active ? 'Ativo' : 'Parado';
    row.append(name, state); root.append(row);
  }
  setText('serviceCount', `${items.length} serviços`);
}
function renderLogs(items) {
  const root = $('logList'); root.replaceChildren();
  if (!Array.isArray(items) || !items.length) {
    const empty = document.createElement('div'); empty.className = 'empty-state'; empty.textContent = 'Nenhum evento recente foi retornado.'; root.append(empty); return;
  }
  for (const item of items.slice(0, 8)) {
    const row = document.createElement('div'); row.className = 'log-row';
    const time = document.createElement('span'); time.className = 'log-time'; time.textContent = String(item.time || '--:--');
    const level = document.createElement('span'); level.className = `log-level ${['warn','error'].includes(item.level) ? item.level : ''}`; level.textContent = String(item.level || 'info');
    const message = document.createElement('span'); message.textContent = String(item.message || 'Evento do sistema');
    row.append(time, level, message); root.append(row);
  }
}
function update(data) {
  const memory = data.memory || {};
  const cpu = data.cpu || {};
  const disk = data.disk || {};
  const network = data.network || {};
  setText('memoryValue', hasNum(memory.used_percent) ? fmt(memory.used_percent, '%') : '—');
  setMeter('memoryBar', memory.used_percent);
  setText('memoryDetail', memory.used_mb != null && memory.total_mb != null ? `${fmt(memory.used_mb)} MB de ${fmt(memory.total_mb)} MB` : 'Memória utilizada');
  setText('cpuValue', hasNum(cpu.usage_percent) ? fmt(cpu.usage_percent, '%') : '—');
  setMeter('cpuBar', cpu.usage_percent); setText('cpuDetail', cpu.model || 'Uso médio do processador');
  setText('diskValue', hasNum(disk.used_percent) ? fmt(disk.used_percent, '%') : '—');
  setMeter('diskBar', disk.used_percent); setText('diskDetail', disk.used_gb != null && disk.total_gb != null ? `${disk.used_gb} GB de ${disk.total_gb} GB` : 'Partição do sistema');
  setText('tempValue', data.temperature_c != null ? fmt(data.temperature_c, '°C') : '—');
  setText('tempDetail', data.temperature_c != null ? 'Temperatura informada pelo sensor' : 'Sensor indisponível');
  setText('networkName', network.connected ? (network.ssid || 'Conectado') : 'Sem conexão ativa');
  setText('networkAddress', network.ip || 'Endereço IP não disponível');
  setText('networkInterface', network.interface || '—'); setText('networkGateway', network.gateway || '—');
  const net = $('networkState'); net.textContent = network.connected ? 'Conectada' : 'Desconectada'; net.classList.toggle('muted', !network.connected);
  renderServices(data.services); renderLogs(data.logs);
  setText('lastUpdate', `Atualizado às ${new Date().toLocaleTimeString('pt-BR')}`);
}
async function refresh() {
  const badge = $('connection');
  try {
    const response = await fetch('/api/v1/status', { credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' } });
    if (response.status === 401 || response.status === 403) throw new Error('Autenticação necessária');
    if (!response.ok) throw new Error(`API respondeu ${response.status}`);
    const data = await response.json();
    if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('Resposta inválida da API');
    update(data);
    badge.classList.add('online'); badge.replaceChildren(document.createElement('i'), document.createTextNode(' API conectada'));
  } catch (error) {
    badge.classList.remove('online'); badge.replaceChildren(document.createElement('i'), document.createTextNode(' API desconectada'));
    setText('lastUpdate', error.message === 'Failed to fetch' ? 'Servidor de diagnóstico indisponível' : error.message);
  }
}
$('refreshButton').addEventListener('click', refresh);
$('diagnosticButton').addEventListener('click', () => toast('A coleta de diagnóstico será habilitada após a API autenticada estar pronta.'));
refresh(); setInterval(refresh, 10000);