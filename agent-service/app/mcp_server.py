from __future__ import annotations

import os
from typing import Any

import httpx
from mcp.server.fastmcp import FastMCP
from mcp.server.auth.provider import AccessToken, TokenVerifier
from mcp.server.auth.settings import AuthSettings
from pydantic import AnyHttpUrl


BACKEND_URL = os.getenv("BACKEND_INTERNAL_URL", "http://localhost:8080").rstrip("/")
INTERNAL_TOKEN = os.getenv("MCP_BACKEND_TOKEN", "")
MCP_TOKEN = os.getenv("MCP_INTERNAL_TOKEN", "")


class StaticServiceTokenVerifier(TokenVerifier):
    async def verify_token(self, token: str) -> AccessToken | None:
        if not MCP_TOKEN or not __import__("secrets").compare_digest(token, MCP_TOKEN):
            return None
        return AccessToken(token=token, client_id="terra-agent-api", scopes=["tools:invoke"])

mcp = FastMCP(
    "Terra Food Tools",
    instructions="提供菜品热度推荐、浏览足迹推荐和客户端音乐切换动作。",
    host="0.0.0.0",
    port=8091,
    json_response=True,
    token_verifier=StaticServiceTokenVerifier(),
    auth=AuthSettings(
        issuer_url=AnyHttpUrl("http://agent-api:8090"),
        resource_server_url=AnyHttpUrl("http://agent-mcp:8091/mcp"),
        required_scopes=["tools:invoke"],
    ),
)


async def _backend_request(method: str, path: str, **kwargs: Any) -> Any:
    if not INTERNAL_TOKEN:
        raise RuntimeError("MCP_BACKEND_TOKEN is not configured")
    headers = dict(kwargs.pop("headers", {}))
    headers["X-MCP-Backend-Token"] = INTERNAL_TOKEN
    async with httpx.AsyncClient(base_url=BACKEND_URL, timeout=12.0) as client:
        response = await client.request(method, path, headers=headers, **kwargs)
        response.raise_for_status()
        return response.json()


@mcp.tool()
async def recommend_local_foods(
    subject_id: str,
    context: str,
    province: str = "",
    city: str = "",
    limit: int = 5,
) -> dict[str, Any]:
    """按省份/城市筛选当地菜品，并按照网站点击热度从高到低推荐。"""
    foods = await _backend_request(
        "GET",
        "/api/internal/agent/recommendations",
        params={
            "subjectId": subject_id,
            "context": context,
            "province": province,
            "city": city,
            "personalized": "false",
            "limit": min(max(limit, 1), 10),
        },
    )
    return {"recommendations": foods, "basis": "local_heat"}


@mcp.tool()
async def recommend_from_recent_history(
    subject_id: str,
    context: str,
    province: str = "",
    city: str = "",
    limit: int = 5,
) -> dict[str, Any]:
    """依据用户最近30天浏览地区偏好推荐未浏览菜品，再按点击热度排序。"""
    foods = await _backend_request(
        "GET",
        "/api/internal/agent/recommendations",
        params={
            "subjectId": subject_id,
            "context": context,
            "province": province,
            "city": city,
            "personalized": "true",
            "limit": min(max(limit, 1), 10),
        },
    )
    return {"recommendations": foods, "basis": "recent_history_and_heat"}


@mcp.tool()
async def switch_background_music(track_query: str, available_tracks: list[str]) -> dict[str, Any]:
    """让网页播放器按歌曲名或歌手匹配并切换背景音乐。"""
    query = track_query.strip()
    if not query:
        return {"switched": False, "reason": "未提供歌曲名"}
    return {
        "switched": True,
        "matchedByClient": True,
        "availableTracks": available_tracks,
        "client_action": {"type": "SWITCH_MUSIC", "query": query},
    }


if __name__ == "__main__":
    mcp.run(transport="streamable-http")
