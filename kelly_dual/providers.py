"""Provedores de IA da Kelly. Segredos ficam somente no servidor (arquivo 0600)."""
from __future__ import annotations

import json
import os
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

CONFIG = Path(os.environ.get("KELLY_PROVIDER_CONFIG", "~/.overos/kelly/providers.json")).expanduser()
ALLOWED = {"openai-compatible", "gemini"}


def _read() -> dict:
    try:
        data = json.loads(CONFIG.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def public_config() -> dict:
    data = _read()
    return {
        "provider": data.get("provider", "openai-compatible"),
        "model": data.get("model", "gpt-4o-mini"),
        "base_url": data.get("base_url", "https://api.openai.com/v1"),
        "has_api_key": bool(data.get("api_key")),
    }


def save_config(payload: dict) -> dict:
    provider = str(payload.get("provider", "openai-compatible")).strip()
    model = str(payload.get("model", "")).strip()
    base_url = str(payload.get("base_url", "")).strip().rstrip("/")
    if provider not in ALLOWED:
        raise ValueError("Provedor não suportado.")
    if not model or len(model) > 120:
        raise ValueError("Informe um modelo válido.")
    if provider == "openai-compatible":
        base_url = base_url or "https://api.openai.com/v1"
        if not base_url.startswith("https://"):
            raise ValueError("A URL do provedor precisa usar HTTPS.")
    old = _read()
    key = str(payload.get("api_key", "")).strip() or old.get("api_key", "")
    if payload.get("clear_api_key"):
        key = ""
    if key and len(key) > 4096:
        raise ValueError("API key inválida.")
    data = {"provider": provider, "model": model, "base_url": base_url, "api_key": key}
    CONFIG.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix="providers-", dir=str(CONFIG.parent))
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(data, stream)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(tmp, CONFIG)
        os.chmod(CONFIG, 0o600)
    finally:
        try:
            os.unlink(tmp)
        except FileNotFoundError:
            pass
    return public_config()


def _request(url: str, payload: dict, headers: dict) -> dict:
    raw = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=raw, headers={
        "Content-Type": "application/json", "User-Agent": "OverCode-Kelly/2.0", **headers
    }, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            result = json.loads(response.read(2_000_000).decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read(4000).decode("utf-8", "replace")
        raise RuntimeError(f"Provedor respondeu HTTP {exc.code}: {detail[:1000]}") from None
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RuntimeError(f"Não foi possível conectar ao provedor: {exc}") from None
    if not isinstance(result, dict):
        raise RuntimeError("Resposta inválida do provedor.")
    return result


def complete(message: str, system: str = "") -> str:
    cfg = _read()
    provider, model, key = cfg.get("provider"), cfg.get("model"), cfg.get("api_key")
    if provider not in ALLOWED or not model or not key:
        raise RuntimeError("Configure o provedor, modelo e API key em Configurações.")
    if provider == "gemini":
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"
        parts = ([{"text": system + "\\n\\n"}] if system else []) + [{"text": message}]
        data = _request(url, {"contents": [{"role": "user", "parts": parts}]}, {})
        try:
            return data["candidates"][0]["content"]["parts"][0]["text"]
        except (KeyError, IndexError, TypeError):
            raise RuntimeError("O Gemini não retornou texto.") from None
    base = str(cfg.get("base_url") or "https://api.openai.com/v1").rstrip("/")
    messages = ([{"role": "system", "content": system}] if system else [])
    messages.append({"role": "user", "content": message})
    data = _request(base + "/chat/completions",
                    {"model": model, "messages": messages, "stream": False},
                    {"Authorization": "Bearer " + key})
    try:
        return str(data["choices"][0]["message"]["content"])
    except (KeyError, IndexError, TypeError):
        raise RuntimeError("O provedor não retornou texto.") from None


def test_connection() -> dict:
    answer = complete("Responda somente: conexão OK.", "Você é um teste de conexão.")
    return {"ok": True, "message": answer[:300], **public_config()}
