# 长期记忆维护边界

当前聊天代码不读取或写入长期记忆；不能通过只改环境变量重新开启。这样已删除身份不会被在途聊天重新写入。旧的用户名记忆不能按当前同名账号自动归属或自动删除。

V31 为既有删除 outbox 增加租约。`memory_deletion.py` 默认只读统计；显式 `--execute` 才处理至多一个批次。执行前必须停止该 Compose 项目全部 Agent API 写入实例，并在维护窗口内保持停止；工具会在领取前和完成前检查。它不会停止服务、安装调度、猜测 Milvus 地址或自动开启记忆。额外项目或外部写入者必须先由操作者清点和停用，否则不能执行维护。

删除按不可复用 subject UUID 过滤，拒绝仍存在于账号表的 subject。外部删除成功后执行 Strong 一致性查询确认无残留，才按租约所有者条件完成。失败延迟五分钟重试，八次失败进入 FAILED；租约超时可以恢复。人工处理失败原因后可按任务 ID、原因和审计记录重置单项，禁止直接清空队列。

示例（参数必须来自已核验部署；不包含凭据）：

```sh
python3 ops/memory_deletion.py --mysql-container PROJECT-mysql-1 \
  --agent-project PROJECT --milvus-url http://VERIFIED-MILVUS:19530
# 维护窗口全部写入者停止后，使用同一命令加 --execute。
```

`MILVUS_TOKEN` 由私有环境提供，不写入命令行或日志。遗留 username-only 数据必须另行清点，不属于此按 subject 删除工具的覆盖范围。

协议依据：[Milvus 删除接口](https://milvus.io/api-reference/restful/v3.0.x/v2/Vector%20(v2)/Delete.md)、[查询与一致性接口](https://milvus.io/api-reference/restful/v3.0.x/v2/Vector%20(v2)/Query.md)。确定性替身能验证调用和失败恢复，不能代替实际目标 Milvus 版本兼容验收。
