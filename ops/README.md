# 整改版本运维入口

这些工具不自动安装定时任务、不修改运行中的服务。默认预览的工具必须显式执行参数才会写入；备份只读取源数据，但会生成私有加密文件。

## 构建和发布

1. 在构建主机固定仓库提交，执行 `python3 ops/release.py prepare --repo PATH --revision COMMIT --manifest PRIVATE/prepared.json`。同时构建后端、前端和 Agent，不以主分支相同就跳过 Agent。
2. 先使用 `apply` 的预览模式验证完整 Compose 和四个不同的服务凭据。运行环境文件权限必须为0600，至少包含 `AGENT_INTERNAL_TOKEN`、`MCP_INTERNAL_TOKEN`、`MCP_BACKEND_TOKEN`、`AGENT_CONTEXT_SECRET`，各自至少32字符；凭据生成和保管在仓库外完成。
3. 确认数据库、Redis、SMTP、上传挂载和数据库备份已准备；旧后台发布器必须停用或使用同一 `/run/lock/terrafood-maintenance.lock`，不能同时更新。先做数据库迁移预检，确认历史归属隔离对用户列表的影响。
4. 维护窗口使用 `apply --execute`。只有公共目录、四个实际镜像身份、Agent版本和MCP匿名拒绝都通过，才写入 deployed 标记。失败不写成功标记、不自动回滚数据库。
5. `terrafood_guardian.py` 是可审查的新守护版本；`--self-test` 不执行恢复。它不能证明断电/断网会被外部发现。本 PR 没有替换现场守护或更新器。

发布镜像保留非 root、cap-drop、no-new-privileges。小主机 Snap 环境存在加固兼容限制，见 `security-audit/HOST_LIMITATIONS.md`；不要直接复制测试例外到生产。镜像基座摘要锁定不代表没有新漏洞，应定期更新扫描记录。

## 迁移和数据

V26–V31 都是增量迁移。V28 隔离无法证明归属的历史菜品，并保留原ID与用户名快照；不靠当前同名账号自动认领。先运行 `security-audit/ownership-review.py` 的只读预检，再按证据逐项处理。长久记忆仍关闭，外部数据清理见 [MEMORY_DELETION.md](MEMORY_DELETION.md)。

图片原图保留。派生生成有所有者、租约和有界子进程；自动孤图删除入口拒绝执行，不能自行打开旧开关。维护入口：

```sh
java -Dloader.main=com.dayan.food.image.ImageMaintenance -cp app.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher --dry-run
```

数据库和上传路径通过环境提供。默认批量100，`IMAGE_AFTER` 指定游标；dry-run 不启动Spring/Flyway/任务。确认结果后另行 `--apply`；失败批次回滚并返回非零。绝对外部URL和派生引用不会被猜测转换成原图。

## 备份、恢复与监测

`backup.py` 生成一致性数据库快照和不可变上传目录副本，保存在权限0700的目录；私有口令文件0600，不放在备份介质同处。生成密文后附加独立密钥HMAC。源端自动删除必须保持关闭，避免数据库快照与后续文件复制之间丢失被引用原图。

`restore_verify.py` 先验证私有密文副本，再解密到全新目录；不覆盖已有文件，也不直接导入数据库。导入必须在独立新库执行，再核对表数量、约束、原图哈希、应用启动和业务只读路径。实际隔离演练见 `security-audit/maintenance-regression.py`；它硬编码只允许验收项目。

备份需要异机副本、定期恢复和明确保管责任。本轮只交付手动工具与合成恢复证据，没有建立线上持续备份。上线前确定业务可接受RPO/RTO、备份频次和异机留存期；不能把同机文件称为抗整机故障备份。

`external_probe.py` 应从服务器之外执行，返回结构化结果与非零故障码。接入通知平台需明确收件人、频率及故障/恢复去重，本 PR 不发送通知、不安装任务。未配置站外探针前，整机断电/全断网仍没有站外告警保障。

## 回滚

保留稳定身份与认证版本安全补丁作为最低版本，禁止退回用户名鉴权。数据库不删除新增表；原图不删除。可以关闭派生、缓存、Agent等可选能力，前端独立回退，但不能放开旧版本审核或CSRF。迁移后回退后端需要先验证Flyway与新增字段兼容，不能因为镜像能启动就宣称所有旧行为安全。
