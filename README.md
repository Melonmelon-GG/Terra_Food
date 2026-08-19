# 大炎珍馐志

一个用于记录、整理与传播中国地区美食的 Java + Vue + MySQL 前后端分离项目。

## 技术栈

- 后端：Java 21、Spring Boot、MyBatis、Flyway、MySQL
- 前端：Vue 3、TypeScript、Vite、Vue Router、Axios

## 本地启动

先创建数据库：`CREATE DATABASE dayan_food CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`

后端进入 `dayanfood-backend`，按需设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，运行 `mvn spring-boot:run`。Flyway 会自动创建表并写入示例数据。

前端进入 `web`，运行 `npm install` 和 `npm run dev`，访问 http://localhost:5173。

地图选点会由浏览器通过 OpenStreetMap Nominatim 逆地理接口直接读取省市；未收录的城市会自动创建地区记录。可使用 `VITE_GEOCODING_BASE_URL` 切换到自建或其他 Nominatim 兼容服务。公共服务调用已限制为每秒不超过一次并缓存相同坐标结果。

## Excel 导入

管理员登录后可在“菜品管理”区域导入 `.xlsx` 或 `.xls` 文件。系统会尽量从不规则表头与错位列中识别省份、城市、店名/菜品名、地址、推荐菜、点评和记录人：

- 地址只精确到城市时，自动使用内置城市中心坐标与“市中心”地址。
- 表格未填写记录人，或记录人无法匹配已注册用户时，统一记为“无名”。
- 相同地区、名称和地址的数据不会重复写入。
- 无法可靠识别的行会跳过，并在管理页面给出原表行号和原因。

## 登录账号

首次启动后端时会创建两个演示账号，密码以 BCrypt 哈希保存：

- 普通用户：`user / user123`
- 管理员：`admin / admin123`

生产或共享环境请通过 `INITIAL_USER_PASSWORD` 和 `INITIAL_ADMIN_PASSWORD` 环境变量覆盖初始密码。普通用户可以浏览和收录菜品，管理员登录后进入管理后台并拥有内容删除权限。
