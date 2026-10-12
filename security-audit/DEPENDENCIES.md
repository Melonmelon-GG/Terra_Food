# 2026-09-23 制品依赖处置

先对运行JAR、Python已安装分发包、npm锁文件生成清单，再查询OSV。未内嵌Maven坐标的JAR按二进制SHA-256与实际Maven缓存比对，不能仅凭文件名猜版本。初始完整可解析清单349项，OSV返回6个包命中；原报告保留，不以改写历史结果消除命中。

| 包 | 原版本 | 本轮处置 | 依据及条件 |
|---|---|---|---|
| Jackson databind | 2.21.4 | Jackson BOM统一2.21.5 | [维护者公告](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5gvw-p9qm-jgwh)、[忽略字段公告](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5jmj-h7xm-6q6v)。涉及特定JsonView/Unwrapped/忽略字段组合；不等于已经复现本项目越权。 |
| Netty handler/codec | 4.1.135.Final | Netty家族统一4.1.137.Final | [SNI公告](https://github.com/netty/netty/security/advisories/GHSA-c4c3-7fpv-j4q5)、[重组公告](https://github.com/netty/netty/security/advisories/GHSA-fccg-mwvh-qqg4)、[解压公告](https://github.com/netty/netty/security/advisories/GHSA-558v-64gr-wgg4)。公开入口使用Tomcat，不能把Netty的SNI服务端条件直接写成本项目公网漏洞。 |
| Commons Lang | 3.17.0 | 3.18.0 | [Apache修复记录](https://github.com/apache/commons-lang/commit/b424803abdb2bec818e4fbcb251ce031c22aca53)，ClassUtils长输入递归风险。 |
| Log4j API | 2.24.3 | Log4j家族统一2.25.5 | [Apache公告](https://logging.apache.org/security.html#CVE-2026-49844)，特定MapMessage JSON格式化条件；不是Log4Shell。 |
| pip | 25.0.1 | 从运行镜像卸载 | 服务不执行动态安装。构建使用完整哈希锁和TLS；不把构建工具漏洞当成已复现聊天接口执行代码。 |

Jackson另有[外部类型属性公告](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-mhm7-754m-9p8w)的修复信息不完整。即使新版OSV不再匹配，也不能只靠扫描消失声称相关全部代码路径已修复。本项目不使用该组合做授权；授权保持Service身份、角色和数据归属检查。

这些是补丁级依赖家族调整，保留当前Spring Boot线，避免本轮跨到Spring Boot 4引入无关API改造。升级后仍必须重新构建、核对实际JAR版本、运行受影响测试和HTTP身份/上传路径。最终扫描结果以交付证据为准；此文件本身不表示验证已结束。

OS包清单另行导出；OSV应用包扫描不覆盖操作系统、私有源码、部署条件或尚未公开漏洞。镜像摘要固定保证可追溯，不代表自动获得未来安全更新。

最终应用清单349项全部解析，OSV复扫无命中、无查询错误。Nginx基座升级为1.30.5-alpine并固定官方摘要，参照[官方安全公告](https://nginx.org/en/security_advisories.html)。相关漏洞多数依赖特定模块/配置；不把升级前版本匹配当作本项目已可利用。代理改为非root，首次因PID目录权限失败，修正到/tmp后32次图片读取通过。
