"""Armazenamento autenticado de anexos do OverCode."""
from __future__ import annotations
import base64, hashlib, json, mimetypes, re, uuid
from pathlib import Path

MAX_FILE_BYTES = 8 * 1024 * 1024
TEXT_EXTENSIONS = {".txt",".md",".py",".js",".ts",".tsx",".jsx",".html",".css",".json",".yaml",".yml",".toml",".ini",".sh",".log",".csv",".xml",".sql",".kt",".java",".c",".cpp",".h",".rs",".go"}

def _root(root: Path) -> Path:
    path=(root/".overcode"/"uploads").resolve()
    path.mkdir(parents=True,exist_ok=True)
    return path

def save_upload(root: Path, name: str, encoded: str, content_type: str="") -> dict:
    raw_name=Path(str(name).replace("\\","/")).name.strip()
    if not raw_name or raw_name in {".",".."}:
        raise ValueError("Nome de arquivo inválido.")
    if len(raw_name)>180:
        raw_name=raw_name[-180:]
    try:
        data=base64.b64decode(encoded,validate=True)
    except Exception:
        raise ValueError("Conteúdo do arquivo inválido (base64).") from None
    if not data:
        raise ValueError("O arquivo está vazio.")
    if len(data)>MAX_FILE_BYTES:
        raise ValueError("Cada arquivo pode ter no máximo 8 MB.")
    file_id=uuid.uuid4().hex
    folder=_root(root)
    ext=Path(raw_name).suffix.lower()[:16]
    target=folder/(file_id+ext)
    target.write_bytes(data)
    mime=str(content_type or mimetypes.guess_type(raw_name)[0] or "application/octet-stream")
    if not re.fullmatch(r"[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+", mime):
        mime=mimetypes.guess_type(raw_name)[0] or "application/octet-stream"
    meta={"id":file_id,"name":raw_name,"size":len(data),"content_type":mime,
          "sha256":hashlib.sha256(data).hexdigest(),"download":"/api/files/"+file_id}
    (folder/(file_id+".json")).write_text(json.dumps(meta,ensure_ascii=False),encoding="utf-8")
    return meta

def list_uploads(root: Path) -> list[dict]:
    folder=_root(root)
    result=[]
    for meta_file in folder.glob("*.json"):
        try: result.append(json.loads(meta_file.read_text(encoding="utf-8")))
        except (OSError,ValueError): continue
    return sorted(result,key=lambda x:x.get("name","").casefold())

def get_upload(root: Path, file_id: str):
    if not re.fullmatch(r"[a-f0-9]{32}",str(file_id)):
        return None
    folder=_root(root)
    meta_file=folder/(file_id+".json")
    try: meta=json.loads(meta_file.read_text(encoding="utf-8"))
    except (OSError,ValueError): return None
    candidates=list(folder.glob(file_id+".*"))
    target=next((p for p in candidates if p.suffix!=".json" and p.is_file()),None)
    if target is None: return None
    return meta,target,target.read_bytes()

def delete_upload(root: Path, file_id: str) -> bool:
    found=get_upload(root,file_id)
    if not found: return False
    meta,target,_=found
    target.unlink(missing_ok=True)
    (target.parent/(file_id+".json")).unlink(missing_ok=True)
    return True

def readable_text(meta: dict, data: bytes, limit: int=120_000) -> str | None:
    ext=Path(meta.get("name","")).suffix.lower()
    if ext not in TEXT_EXTENSIONS: return None
    try: return data[:limit].decode("utf-8")
    except UnicodeDecodeError: return "[Arquivo de texto não UTF-8; anexado, mas não foi extraído para a conversa.]"

def list_project_files(root: Path, max_items: int=500) -> list[dict]:
    base=root.resolve()
    result=[]
    for path in base.rglob("*"):
        if len(result)>=max_items: break
        try:
            if any(part.startswith(".") for part in path.relative_to(base).parts): continue
            if path.is_symlink() or not path.is_file(): continue
            stat=path.stat()
            result.append({
                "path":path.relative_to(base).as_posix(),
                "name":path.name,
                "size":stat.st_size,
                "modified_at":stat.st_mtime,
                "download":"/api/project-files/"+path.relative_to(base).as_posix()
            })
        except OSError:
            continue
    return sorted(result,key=lambda x:x["path"].casefold())

def get_project_file(root: Path, relative: str):
    base=root.resolve()
    candidate=(base/str(relative)).resolve()
    if candidate==base or base not in candidate.parents or not candidate.is_file() or candidate.is_symlink():
        return None
    try:
        if candidate.stat().st_size>20*1024*1024: return None
        return candidate,candidate.read_bytes()
    except OSError:
        return None
