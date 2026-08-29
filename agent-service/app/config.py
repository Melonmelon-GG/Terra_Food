from __future__ import annotations

import os
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