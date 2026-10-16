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
  "project": "terrafood",
  "health_url": "http://127.0.0.1:8180",
  "build_root": "/srv/terrafood-build"
}
```

state 目录必须 0700；运行环境文件 0600。环境文件需有四个不同、各至少32字符的服务凭据：`AGENT_INTERNAL_TOKEN`、`MCP_INTERNAL_TOKEN`、`MCP_BACKEND_TOKEN`、`AGENT_CONTEXT_SECRET`。维护者在仓库外生成和保管，不放入命令参数或日志。

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
  --repo /srv/terrafood --revision FULL_COMMIT --manifest /var/lib/terrafood-update/prepared.json

python3 ops/release.py apply --manifest /var/lib/terrafood-update/prepared.json \
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
