# OverOS Control Center

Interface local do painel V2. A interface já está separada do servidor: ela só consulta a API da mesma origem em `/api/v1/status` e não tenta executar comandos no navegador.

## Contrato de status esperado

```json
{
  "memory": {"used_percent": 42, "used_mb": 300, "total_mb": 720},
  "cpu": {"usage_percent": 8, "model": "CPU detectada"},
  "disk": {"used_percent": 31, "used_gb": 2.1, "total_gb": 7.5},
  "temperature_c": 48,
  "network": {"connected": true, "ssid": "Wi-Fi", "ip": "192.168.1.20", "interface": "wlan0", "gateway": "192.168.1.1"},
  "services": [{"name": "overos-panel", "active": true}],
  "logs": [{"time": "12:30:00", "level": "info", "message": "Sistema iniciado"}]
}
```

A interface trata todos os valores da API como dados não confiáveis e os insere usando `textContent`. Não há endpoint de execução de shell. Botões administrativos só serão ativados quando existir backend com autenticação, CSRF, allowlist e auditoria.

## Segurança obrigatória no servidor

- Bind local/LAN configurável, nunca exposição pública automática.
- Autenticação forte e sessão expirada; cookies `HttpOnly`, `SameSite` e `Secure` quando HTTPS estiver habilitado.
- CSRF token em toda operação mutável.
- Cabeçalhos CSP, `X-Content-Type-Options`, `Referrer-Policy` e `frame-ancestors` no HTTP response.
- API read-only para status e logs; ações privilegiadas com nomes e parâmetros fixos.