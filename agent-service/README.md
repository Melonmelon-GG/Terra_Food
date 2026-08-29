# “余”网站 Agent

该服务由两个进程组成：

- `agent-api`：FastAPI + LangChain，负责人格对话、工具选择和 Milvus 长期记忆。
- `agent-mcp`：Streamable HTTP MCP 服务，提供热度推荐、足迹推荐、评论发布和音乐切换工具。

Java 后端仍是用户身份与业务权限的唯一可信来源。浏览器只访问 `/api/agent/chat`；Java 根据 Session 注入用户名，再把请求转发给 Agent。LangChain 会把用户名绑定在工具包装器中，用户提示词和模型都不能切换成其他账号。

## 本地启动

1. 将根目录 `.env.agent.example` 复制为 `.env`，设置强随机的 `AGENT_INTERNAL_TOKEN`、MinIO 凭据和模型密钥。
2. 启动依赖与 Agent：

```bash
docker compose --profile agent up -d --build
```

3. 启动 Java 后端时使用同一内部令牌：

```bash
AGENT_INTERNAL_TOKEN=与.env相同的值
AGENT_SERVICE_URL=http://127.0.0.1:8090
```

4. 正常启动 Vue 前端。登录后，页面右下区域会显示“余”的对话入口。

MCP、Milvus、etcd 和 MinIO 只位于 Docker 内部网络，不应通过公网反向代理。Agent API 也只绑定到主机回环地址，由同机 Java 后端访问。

## 人物卡

当前只提供临时人格。人物卡完成后，把稳定设定整理进 `AGENT_PERSONA`；人物立绘可替换前端 `AgentPanel.vue` 中的文字占位，不需要修改工具或业务接口。
