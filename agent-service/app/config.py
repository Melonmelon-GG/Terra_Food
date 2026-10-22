from __future__ import annotations

import os
import hashlib
import hmac
import json
from dataclasses import dataclass
from pathlib import Path


def load_persona() -> str:
    override = os.getenv("AGENT_PERSONA", "").strip()
    if override:
        return override

    persona_path = Path(__file__).with_name("personas") / "yu.md"
    return persona_path.read_text(encoding="utf-8").strip()


@dataclass(frozen=True)
class Settings:
    internal_token: str = os.getenv("AGENT_INTERNAL_TOKEN", "")
    mcp_internal_token: str = os.getenv("MCP_INTERNAL_TOKEN", "")
    backend_url: str = os.getenv("BACKEND_INTERNAL_URL", "http://localhost:8080")
    mcp_url: str = os.getenv("MCP_SERVER_URL", "http://localhost:8091/mcp")
    model: str = os.getenv("AGENT_MODEL", "deepseek-v4-flash")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "BAAI/bge-small-zh-v1.5")
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    openai_base_url: str | None = os.getenv("OPENAI_BASE_URL") or None
    milvus_uri: str = os.getenv("MILVUS_URI", "http://localhost:19530")
    memory_collection: str = os.getenv("MILVUS_MEMORY_COLLECTION", "terra_agent_memory")
    persona: str = load_persona()


settings = Settings()


def model_configuration_ready(value: Settings) -> bool:
    """Local readiness only: this does not prove provider access or model quality."""
    from urllib.parse import urlsplit
    key = value.openai_api_key
    base = value.openai_base_url or 'https://api.openai.com/v1'
    url = urlsplit(base)
    return bool(len(key) >= 16 and key == key.strip() and not any(c.isspace() for c in key)
                and not key.lower().startswith(('your-', 'replace-', 'change-me', 'sk-your', 'ci-dummy'))
                and value.model.strip() and value.model == value.model.strip() and url.scheme in ('https', 'http') and url.hostname
                and not url.username and not url.password and not url.query and not url.fragment)


def model_configuration_identity(value: Settings) -> str:
    payload = {'api_key': value.openai_api_key, 'base_url': value.openai_base_url or '', 'model': value.model}
    encoded = json.dumps(payload, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode()
    return hmac.new(value.internal_token.encode(), encoded, hashlib.sha256).hexdigest()
