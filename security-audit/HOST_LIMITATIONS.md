# 小主机测试边界

2026-09-23，新后端镜像在 `no-new-privileges=true` 下无法执行 JVM。内核审计明确记录：`profile=snap.docker.dockerd`、`info=no new privs`、执行 `/opt/java/openjdk/bin/java`、目标 `docker-default` 被拒绝。分别只使用 cap-drop ALL 和不增加选项均能执行同一镜像的 `java -version`；增加 no-new-privileges 时失败。

这是当前 Snap Docker/AppArmor 配置中的 profile 转换限制。本次没有关闭主机 AppArmor、修改 Docker 守护进程或放宽正在服务用户的容器。

隔离验收专用 Compose 允许 `AUDIT_NO_NEW_PRIVILEGES=false`，仅在本机私有验收配置中明确设置；默认仍为 true。后端和 Agent 保留非 root、全部 capability 移除、默认 seccomp、独立网络/卷及整组资源约束。其结果不能宣称已在完整 no-new-privileges 部署配置下验证。正式部署需要先在支持该配置的 Docker 环境演练，不能复制此验收例外掩盖运行限制。

失败的首次 HTTP 预检报告保留。此时业务用例未执行，不能算业务失败或通过。
