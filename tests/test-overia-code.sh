#!/usr/bin/env bash
# Verificações rápidas do instalador e do motor overia-code.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALLER="$ROOT/instalar-overia-code-v1_6.sh"

bash -n "$INSTALLER"
python3 - "$INSTALLER" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
source = p.read_text(encoding="utf-8")
marker = "#__OVERIA_PY_INICIO__\n"
if marker not in source:
    raise SystemExit("ERRO: marcador do motor Python não encontrado")
engine = source.split(marker, 1)[1]
compile(engine, str(p) + ":engine", "exec")
checks = {
    "versão 1.6 preservada": 'VERSAO = "1.6"' in engine,
    "confirmação de comandos": "def confirma(" in engine,
    "execução isolada": "def executa(" in engine,
    "conversa em etapas": "def turno(" in engine,
    "seleção de modelos": "def escolhe_modelo(" in engine,
    "integração Gemini": "def usa_gemini(" in engine,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("OK  " if ok else "FALHA ") + name)
if failed:
    raise SystemExit("Falharam: " + ", ".join(failed))
print("OK  sintaxe Python do motor")
PY
echo "Todas as verificações estáticas passaram."
