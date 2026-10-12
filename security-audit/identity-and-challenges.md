# 身份、验证码和历史归属整改

- 请求主体必须为 `AppUserPrincipal`。Service 使用 `AuthenticatedActor.resolve`，写事务的第一条身份查询使用 ID `FOR UPDATE` 并核验 subject、认证版本、角色和 active。不得在随后重新按用户名认领账号。用户名仅是已有接口传参/展示属性。没有认证上下文的定时任务必须另行使用明确的稳定 ID。
- `X-Expected-User-Id` 在写入前与 Session 主体比较，冲突返回 409 `IDENTITY_CHANGED`；客户端必须在异步 CSRF 获取前捕获该值。匿名受保护入口为 401，权限/CSRF 拒绝仍为 403。
- 登录不等待成就。身份确认后的通知请求独立幂等授予首次登录成就。
- 注册、重置、算术验证码统一 Redis Lua 代次：发布重置尝试次数；校验与领取原子执行。邮件凭据还在业务 MySQL 事务登记唯一 generation。**领取后业务失败要求重新获取验证码**，不自动解锁、不恢复旧码；旧请求的 consume 不删除新代次。密码重置挑战绑定不可复用 subjectId，删号同名重建无权使用旧码。保留兑换墓碑覆盖 Redis 备份恢复窗口。
- 算术题不是可靠机器人验证；IP/Session/站点发行限额仍独立执行。登录用户名与邮箱统一映射 subject 失败桶，成功重置该桶。IP 限额与账号失败限额分离。429 携带 `Retry-After`，Redis 不可用不能默默放行。

## 可信代理配置

`app.security.trusted-proxies`（部署环境 `TRUSTED_PROXY_CIDRS`）只包含实际 Nginx/隧道代理地址或专用受控子网，默认仅回环。后端从真实连接对端开始从右向左检查 X-Forwarded-For；非可信对端头部一律忽略。**不要使用 0.0.0.0/0、::/0 或包含任意容器的宽泛私网作为快捷配置。** 边缘必须覆盖外来客户端头；真实 Cloudflare -> Nginx 链要同时核验受控源，不能直接相信公网 CF-Connecting-IP。发布前验证两客户端独立限额与伪造头无效。默认未指定代理时有安全性但可能共享额度，不能宣称真实 IP 部署已验证。

## 历史归属安全闸门

不修改已发布 V22。V28 对 V22 安装时间之前的现有关联新增 `LEGACY_UNVERIFIED` 状态，保留原用户 ID 与展示快照，禁止据此本人编辑或私人作者读取。时间不明采用保守隔离。迁移后新业务创建记录默认 VERIFIED。管理员显式审核/管理通道仍保留。

这可能暂时限制合法历史作者，需要可靠历史备份、不可复用身份记录或人工核验资料逐项证明；不能把同名、相同正文、现有 ID 本身当作证据。该迁移不自动认领、删除或重分配任何记录。

只读预检（使用权限为 0600 的 MySQL option 文件，不在命令行给密码）：

```sh
python3 security-audit/ownership-review.py --defaults-extra-file /private/read-only.cnf --database dayan_food > ownership-precheck.json
```

确认独立证据后填写 1–100 条 JSON 数组，每条：`foodId`、`expectedUserId`、`expectedStatus`、`action`（verify/quarantine）、`evidenceFile`（本机非空证据文件）。显式执行：

```sh
python3 security-audit/ownership-review.py --defaults-extra-file /private/reviewer.cnf --database dayan_food --apply-reviewed-manifest reviewed.json --reviewer reviewer-identifier
```

每条使用行锁、原值条件和同事务审计；保留证据 SHA-256，不复制敏感证据正文。出现并发变化停止并返回非零，之前已完成条目不会伪称回滚，重新预检后处理剩余项。工具永不支持改派另一个用户。真实数据本轮不自动执行该命令。
