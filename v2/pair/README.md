# OverOS Pair

Serviço local de pareamento entre o computador OverOS e o aplicativo Android.

## Fluxo
1. O computador cria sessão aleatória de dois minutos e exibe somente o QR Code.
2. O aplicativo valida o QR por HTTPS com pinning SHA-256 do certificado apresentado no próprio QR.
3. O desafio do QR é consumido uma única vez. Só depois disso o computador gera e mostra o código aleatório de seis dígitos.
4. O usuário digita o código no aplicativo. O servidor compara em tempo constante, limita a cinco tentativas e emite token aleatório.
5. Apenas o hash SHA-256 do token é salvo no computador. O token original só é entregue uma vez.

QR e código não são fixos, expiram e não aceitam o código de demonstração. A tela pode ser pulada com P + Enter. Endpoints internos aceitam somente conexões loopback.

Porta HTTPS: 8766.

## API
- POST /api/v1/pair/scan
- POST /api/v1/pair/complete
- GET /api/v1/pair/verify
- GET /api/v1/pair/devices
- DELETE /api/v1/pair/devices/{device_id}

O serviço implementa pareamento, autenticação da credencial, listagem e revogação. Ele ainda não é o backend de terminal remoto/Kelly; esse será integrado às próximas funções autenticadas do OverOS Control.
