# 大炎珍馐志

一个用于记录、整理与传播中国地区美食的 Java + Vue + MySQL 前后端分离项目。

## 技术栈

- 后端：Java 21、Spring Boot、Spring Data JPA、Flyway、MySQL
- 前端：Vue 3、TypeScript、Vite、Vue Router、Axios

## 本地启动

先创建数据库：`CREATE DATABASE dayan_food CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`

后端进入 `dayanfood-backend`，按需设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，运行 `mvn spring-boot:run`。Flyway 会自动创建表并写入示例数据。

前端进入 `web`，运行 `npm install` 和 `npm run dev`，访问 http://localhost:5173。
