# 跟随 main 的可核验更新

自动更新完整应用，不冻结或跳过前端。源码同步、镜像构建、实际部署分别记录，Git 已最新不能证明网站已更新。

`auto_update.py` 配合 `release.py` 使用。首次部署和新增/改变 Flyway 迁移走人工审阅的发布流程；已建立可信部署清单后，普通应用更新可自动执行。MySQL、Redis、上传卷不重建，后台服务只更新 backend、web、agent-api、agent-mcp。

## 私有配置

配置放在仓库之外，以下为示例，不包含凭据：

```json
{
  "repo": "/srv/terrafood",
  "remote": "origin",
  "branch": "main",
  "state": "/var/lib/terrafood-update",
  "lock": "/run/lock/terrafood-update.lock",
  "compose": "/srv/terrafood/deploy/docker-compose.json",
  "env_file": "/srv/terrafood/.env",
  "frontend_config": "/etc/terrafood/frontend.json",
  "project": "terrafood",
  "health_url": "http://127.0.0.1:8180",
  "build_root": "/srv/terrafood-build"
}
```

state 目录必须 0700；运行环境文件和前端配置文件0600。环境文件需有四个不同、各至少32字符的服务凭据：`AGENT_INTERNAL_TOKEN`、`MCP_INTERNAL_TOKEN`、`MCP_BACKEND_TOKEN`、`AGENT_CONTEXT_SECRET`，以及明确的 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`AGENT_MODEL`。不将旧Compose中已经展开的值当成今后可以轮换的配置来源。维护者在仓库外生成和保管，不放入命令参数或日志。受校验的七个字段只接受字面值，拒绝歧义插值；若既有凭据包含 `$`，先迁移到明确的凭据文件方案，不用静默转换来“兼容”。

前端配置参考 `ops/frontend-config.example.json`。明确选择 `map_provider: tianditu` 并提供有效32位浏览器Key，或明确选择 `osm` 且Key为空；生产缺失配置会停止发布。`api_base_url`只接受同源路径。允许字段之外的内容一律拒绝，不能把整份后端环境文件当作浏览器构建输入。

前端配置通过BuildKit secret送入构建，不复制到源码导出包；浏览器Key本身最终在客户端可见，不是服务端秘密。模型API Key绝不能进入前端。最终构建校验底图、标注/地址解析及备用源代码能力，生成公开的 `/.well-known/terrafood-build.json`（只有配置摘要、能力和关键资产摘要，没有Key）。前端镜像复用同时校验源码和完整配置摘要；只改Key或API路径也会重建。

运行配置的私有HMAC涵盖服务凭据、模型参数及四个应用服务展开后的环境、挂载、启动和安全参数；同源码下修改邮件等运行设置也会触发应用。发布后逐字段核验Compose与容器环境，并调用受鉴权保护的Agent `/ready` 验证实际进程加载的模型配置。`/health`只表示存活；`/ready`只证明本地配置，不能证明供应商可用或模型推理成功。HMAC仅保存在私有部署清单，不公开凭据摘要或配置正文。

如需使用经核验的 HTTPS PyPI 镜像，可添加 `pip_index_url`；仍执行 requirements.lock 的完整版本与哈希校验，拒绝 HTTP 或嵌入账号密码的索引地址。默认使用官方索引。

本机通过回环端口接受 Cloudflare Tunnel 时，可配置 `proxy_gateway`（本项目 Docker 网关地址）和 `proxy_config`（仓库外运行配置路径）。每次部署从目标提交的 performance/nginx.conf 生成配置，只信任该网关传入的 CF-Connecting-IP；上游模板结构不再适配时返回 ProxyTemplateReviewRequired。不要对公网任意来源信任该头。

## 使用

只读检查不获取代码、不构建、不写状态、不改服务：

```sh
python3 ops/auto_update.py --config /etc/terrafood-update.json check
```

首次完成备份、独立恢复与迁移演练、配置检查之后，手动建立已验证部署清单：

```sh
TERRA_RELEASE_BUILD_ROOT=/srv/terrafood-build python3 ops/release.py prepare \
  --frontend-config /etc/terrafood/frontend.json \
  --repo /srv/terrafood --revision FULL_COMMIT --manifest /var/lib/terrafood-update/prepared.json

python3 ops/release.py apply --manifest /var/lib/terrafood-update/prepared.json \
  --frontend-config /etc/terrafood/frontend.json \
  --compose /srv/terrafood/deploy/docker-compose.json --env-file /srv/terrafood/.env \
  --project terrafood --deployed /var/lib/terrafood-update/deployed.json --execute
```

apply 不带 execute 只检查配置，不替换容器。发布时会核验四个运行镜像、后端目录 JSON、前端 HTML 摘要和实际 JS/CSS 响应、Agent 版本及 MCP 无凭据拒绝；全部通过后，原子保存 deployed.json 与对应的 deployed.compose.json。实际升级时的数据库备份、迁移演练、旧制品保全由运维步骤负责；工具不自动降级数据库。

替换前还会以候选后端镜像的实际 UID，使用只读上传卷、只读容器文件系统及无网络的短任务检查现有文件可读性，不启动 Java 业务。初次切换为非 root 运行时，先备份并为服务账号设置上传目录/文件的正确属主及目录写权限；不要只改目录而遗漏640的旧文件，不要把权限改为777。新建空环境仍需运维确认卷初始化写权限。

之后既有定时任务可调用：

```sh
python3 ops/auto_update.py --config /etc/terrafood-update.json run
```

不创建第二套定时任务。建议服务 TimeoutStartSec=7200、TimeoutStopSec=30、KillMode=control-group。三个镜像各有1800秒构建上限，准备子进程上限6000秒；外层超时留有取代码、配置检查和发布余量。Snap 主机的 native Compose 插件负责读取真实主机路径，Buildx 使用 Snap wrapper，构建目录需在其可见位置。

未合并热修复已人工部署时，可在私有更新配置指定 `required_ancestor` 为该修复完整提交；上游main尚未包含它时返回 `AwaitingUpstreamHotfix`，保留当前运行制品。合并后自动继续。该保护不改变远端、不强推，不把旧main覆盖热修复；记录设置依据，后续可以解除。

## 全面配置摸查

```sh
python3 ops/audit_configuration.py --config /etc/terrafood-update.json
# 可选：只调用供应商GET /models，不调用模型生成接口
python3 ops/audit_configuration.py --config /etc/terrafood-update.json --probe-provider
```

摸查列出源码环境变量消费点、Compose到容器的逐字段一致性、运行文件同名值漂移、地图制品配置、模型与内部凭据、数据端口、Cookie和邮件TLS配置。只输出字段名、布尔结果和错误类别。不会打印配置值、凭据或供应商完整响应；供应商认证请求拒绝重定向，防止将Authorization转发到其他主机。

这不能替代SMTP真实投递、数据库实际授权、Spring命令行覆盖、真实模型推理或用户网络验证，报告保留未核验项。模型不在GET /models清单中也不能单凭此断言不可用，不自动替换模型。

## 失败与恢复

- 跟踪分支取不到就失败，不用本地 SHA 冒充上游。
- 只接受 main 的干净已跟踪文件、正常快进；不强制覆盖工作区。
- 已成功构建且源码标签匹配的组件可以复用；不完整构建不产生完整 prepared 清单。
- Git SHA 相同仍核验运行镜像和 HTTP；旧容器或缺容器不会变成 no-update。
- 发布失败保留前一次 deployed 清单及可复用 prepared 清单，下次继续处理。
- InitialReviewedDeploymentRequired 表示首次运行制品来源尚未确认；MigrationReviewRequired 表示数据库改动需要恢复/升级演练。不要删除保护或伪造 deployed 标记消除错误。
- 构建使用独立更新锁，保活仍可工作；替换应用和验证阶段使用共同维护锁。超时/中断非零退出，不自动回滚已迁移的数据。
- 日志只给阶段、版本、错误类别；status.json、prepared.json、deployed.json 分别表示执行、准备、部署状态。

## 验证

所有验证在小主机执行：`python3 -m unittest discover -s ops/tests -v`。覆盖源码已同步但容器未部署、构建/部署失败重试、超时、中断、缺报告、错误镜像、错误 HTML/SPA 资源回退、配置及初次迁移保护。现场安装与首次发布证据另行记录，不能将替身单测当成真实生产部署证明。
