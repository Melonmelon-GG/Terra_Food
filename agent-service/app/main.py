from __future__ import annotations

import asyncio
import json
import secrets
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException, status
from langchain.agents import create_agent
from langchain_core.documents import Document
from langchain_core.messages import AIMessage, ToolMessage
from langchain_core.tools import tool
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_milvus import Milvus
from langchain_community.embeddings.fastembed import FastEmbedEmbeddings
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field

from .config import settings


app = FastAPI(title="Terra Food Agent", version="0.1.0")
_memory_store: Milvus | None = None
_memory_lock = asyncio.Lock()


class ChatRequest(BaseModel):
    username: str = Field(min_length=1, max_length=50)
    displayName: str = Field(min_length=1, max_length=50)
    message: str = Field(min_length=1, max_length=1000)
    availableTracks: list[str] = Field(default_factory=list, max_length=100)
    currentFoodId: int | None = Field(default=None, gt=0)
    currentFoodName: str | None = Field(default=None, max_length=100)


class ClientAction(BaseModel):
    type: str
    query: str


class FoodRecommendation(BaseModel):
    id: int
    name: str


class CommentDraft(BaseModel):
    foodId: int
    foodName: str
    content: str


class ChatResponse(BaseModel):
    reply: str
    clientAction: ClientAction | None = None
    recommendations: list[FoodRecommendation] = Field(default_factory=list)
    commentDraft: CommentDraft | None = None


def require_internal_token(x_agent_internal_token: str = Header(default="")) -> None:
    if not settings.internal_token or not secrets.compare_digest(x_agent_internal_token, settings.internal_token):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="invalid internal token")


async def get_memory_store() -> Milvus | None:
    global _memory_store
    if _memory_store is not None:
        return _memory_store
    async with _memory_lock:
        if _memory_store is None:
            try:
                embeddings = FastEmbedEmbeddings(
                    model_name=settings.embedding_model,
                    cache_dir="/root/.cache/fastembed",
                )
                _memory_store = Milvus(
                    embedding_function=embeddings,
                    connection_args={"uri": settings.milvus_uri},
                    collection_name=settings.memory_collection,
                    auto_id=True,
                    enable_dynamic_field=True,
                    index_params={"index_type": "AUTOINDEX", "metric_type": "COSINE"},
                )
            except Exception:
                # Milvus暂不可用时对话仍可继续，只是不读取长期记忆。
                return None
    return _memory_store


async def recall_memories(username: str, query: str) -> str:
    store = await get_memory_store()
    if store is None:
        return "暂无可用的长期对话记忆。"
    safe_username = username.replace("\\", "\\\\").replace("'", "\\'")
    try:
        documents = await asyncio.to_thread(
            store.similarity_search,
            query,
            4,
            expr=f"username == '{safe_username}'",
        )
    except Exception:
        return "长期记忆暂时不可用。"
    return "\n".join(f"- {document.page_content}" for document in documents) or "暂无相关记忆。"


async def remember_exchange(username: str, user_message: str, reply: str) -> None:
    store = await get_memory_store()
    if store is None:
        return
    document = Document(
        page_content=f"用户：{user_message}\n余：{reply}",
        metadata={"username": username, "kind": "conversation"},
    )
    try:
        await asyncio.to_thread(store.add_documents, [document])
    except Exception:
        pass


def message_text(message: AIMessage) -> str:
    if isinstance(message.content, str):
        return message.content
    parts: list[str] = []
    for block in message.content:
        if isinstance(block, dict) and block.get("type") == "text":
            parts.append(str(block.get("text", "")))
    return "\n".join(part for part in parts if part).strip()


def ensure_doctor_address(reply: str) -> str:
    stripped = reply.strip()
    if not stripped:
        return "博士，我暂时没有组织好回答，请再说一次。"
    if stripped.startswith("博士"):
        return stripped
    return f"博士，{stripped}"


def find_client_action(value: Any) -> ClientAction | None:
    if isinstance(value, str):
        try:
            return find_client_action(json.loads(value))
        except (json.JSONDecodeError, TypeError):
            return None
    if isinstance(value, list):
        for item in value:
            action = find_client_action(item)
            if action:
                return action
        return None
    if not isinstance(value, dict):
        return None
    candidate = value.get("client_action") or value.get("clientAction")
    if isinstance(candidate, dict) and candidate.get("type") and candidate.get("query"):
        return ClientAction(type=str(candidate["type"]), query=str(candidate["query"]))
    for child in value.values():
        action = find_client_action(child)
        if action:
            return action
    return None


def find_food_recommendations(value: Any) -> list[FoodRecommendation]:
    if isinstance(value, str):
        try:
            return find_food_recommendations(json.loads(value))
        except (json.JSONDecodeError, TypeError):
            return []
    if isinstance(value, list):
        recommendations: list[FoodRecommendation] = []
        for item in value:
            recommendations.extend(find_food_recommendations(item))
        return recommendations
    if not isinstance(value, dict):
        return []
    recommendations: list[FoodRecommendation] = []
    candidate = value.get("recommendations")
    if isinstance(candidate, list):
        for item in candidate:
            if isinstance(item, dict) and item.get("id") is not None and item.get("name"):
                try:
                    recommendations.append(FoodRecommendation(id=int(item["id"]), name=str(item["name"])))
                except (TypeError, ValueError):
                    continue
    for key, child in value.items():
        if key != "recommendations":
            recommendations.extend(find_food_recommendations(child))
    return recommendations


def find_comment_draft(value: Any) -> CommentDraft | None:
    if isinstance(value, str):
        try:
            return find_comment_draft(json.loads(value))
        except (json.JSONDecodeError, TypeError):
            return None
    if isinstance(value, list):
        for item in value:
            draft = find_comment_draft(item)
            if draft:
                return draft
        return None
    if not isinstance(value, dict):
        return None
    candidate = value.get("comment_draft") or value.get("commentDraft")
    if isinstance(candidate, dict):
        try:
            return CommentDraft(
                foodId=int(candidate["foodId"]),
                foodName=str(candidate["foodName"]),
                content=str(candidate["content"]),
            )
        except (KeyError, TypeError, ValueError):
            return None
    for child in value.values():
        draft = find_comment_draft(child)
        if draft:
            return draft
    return None


async def build_tools(request: ChatRequest):
    client = MultiServerMCPClient(
        {"terra_food": {"transport": "streamable_http", "url": settings.mcp_url}}
    )
    raw_tools = {item.name: item for item in await client.get_tools()}

    @tool
    async def recommend_local_foods(province: str = "", city: str = "", limit: int = 5) -> Any:
        """按用户指定的省份或城市，依据点击热度推荐当地菜。"""
        return await raw_tools["recommend_local_foods"].ainvoke(
            {"username": request.username, "province": province, "city": city, "limit": limit}
        )

    @tool
    async def recommend_from_recent_history(province: str = "", city: str = "", limit: int = 5) -> Any:
        """依据当前用户最近30天足迹与热度推荐尚未浏览的菜。"""
        return await raw_tools["recommend_from_recent_history"].ainvoke(
            {"username": request.username, "province": province, "city": city, "limit": limit}
        )

    @tool
    async def prepare_food_comment(content: str) -> Any:
        """为当前菜品生成可由用户一键发布的评论草稿；此工具不会直接发布评论。"""
        if request.currentFoodId is None or not request.currentFoodName:
            return {"prepared": False, "reason": "请先打开要评论的菜品详情页"}
        normalized_content = content.strip()
        if not normalized_content:
            return {"prepared": False, "reason": "评论内容不能为空"}
        if len(normalized_content) > 500:
            return {"prepared": False, "reason": "评论不能超过500个字符"}
        return {
            "prepared": True,
            "comment_draft": {
                "foodId": request.currentFoodId,
                "foodName": request.currentFoodName,
                "content": normalized_content,
            },
        }

    @tool
    async def switch_background_music(track_query: str) -> Any:
        """按歌曲名或歌手请求网页切换背景音乐。"""
        return await raw_tools["switch_background_music"].ainvoke(
            {"track_query": track_query, "available_tracks": request.availableTracks}
        )

    return [
        recommend_local_foods,
        recommend_from_recent_history,
        prepare_food_comment,
        switch_background_music,
    ]


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse, dependencies=[Depends(require_internal_token)])
async def chat(request: ChatRequest) -> ChatResponse:
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not configured")

    memories = await recall_memories(request.username, request.message)
    tools = await build_tools(request)
    model = ChatOpenAI(
        model=settings.model,
        api_key=settings.openai_api_key,
        base_url=settings.openai_base_url,
        temperature=0.7,
    )
    current_food_context = (
        f"当前页面菜品：{request.currentFoodName}（ID {request.currentFoodId}）。"
        if request.currentFoodId and request.currentFoodName
        else "当前页面不是菜品详情页，没有可直接使用的菜品。"
    )
    system_prompt = f"""
{settings.persona}
当前用户是 {request.displayName}（账号 {request.username}）。
{current_food_context}
称呼硬性规则：每一条回复的第一句话都必须称用户为“博士”。显示名称只能作为补充，不能替代“博士”。
你是美食网站内的助手，只能操作当前用户；工具中的身份已由系统绑定。
能力规则：
1. 当用户询问当地菜时，提取省份/城市并调用本地热度推荐工具，说明推荐依据是点击热度。
2. 当用户希望依据口味或最近浏览推荐时，调用足迹推荐工具。
3. 当用户希望为当前菜品写评论并给出观点或内容时，先润色成真诚、具体的评论，再调用评论草稿工具。任何情况下都不要直接发布；草稿生成后请提示用户点击“一键发布评论”按钮确认。
4. 用户只说“帮我评论”但没有提供任何观点时，询问他的真实感受；当前不在菜品详情页时，请他先打开目标菜品页面。
5. 用户提到“这道菜、当前菜品”或说出当前页面菜品名时，直接使用当前页面菜品，不要再次询问编号。
6. 普通问候、心情分享、角色话题和日常交流属于闲聊。闲聊时保持余的人格、自然回应并结合相关记忆，不调用菜品、评论或音乐工具；不要强行把话题拉回推荐菜。
7. 切换音乐必须调用音乐工具。若曲目不在列表中，诚实说明网页只能匹配现有曲目。
8. 不编造菜品ID、评论状态、浏览记录、可播放曲目或工具执行结果。

与当前问题相关的长期对话记忆：
{memories}
""".strip()
    agent = create_agent(model=model, tools=tools, system_prompt=system_prompt)
    result = await agent.ainvoke({"messages": [{"role": "user", "content": request.message}]})
    messages = result.get("messages", [])
    assistant_messages = [item for item in messages if isinstance(item, AIMessage)]
    raw_reply = message_text(assistant_messages[-1]) if assistant_messages else "我暂时没有组织好回答，请再说一次。"
    reply = ensure_doctor_address(raw_reply)

    action = None
    for item in messages:
        if isinstance(item, ToolMessage):
            action = find_client_action(item.content) or find_client_action(item.additional_kwargs)
            if action:
                break

    recommendation_map: dict[int, FoodRecommendation] = {}
    for item in messages:
        if isinstance(item, ToolMessage):
            for recommendation in find_food_recommendations(item.content):
                recommendation_map[recommendation.id] = recommendation

    comment_draft = None
    for item in messages:
        if isinstance(item, ToolMessage):
            comment_draft = find_comment_draft(item.content) or find_comment_draft(item.additional_kwargs)
            if comment_draft:
                break

    await remember_exchange(request.username, request.message, reply)
    return ChatResponse(
        reply=reply,
        clientAction=action,
        recommendations=list(recommendation_map.values()),
        commentDraft=comment_draft,
    )
