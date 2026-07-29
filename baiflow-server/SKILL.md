---
name: baiflow-server
description: BaiFlow Spring Boot 后端：API、认证、MyBatis Plus、MySQL、文件操作、下载任务、传输、通知、aria2
---

# BaiFlow Server

## 约束

- Controller → HTTP 映射 · Service → 业务逻辑 · Mapper → SQL
- DTO/VO/Entity/Request 分离，返回 `{ code, message, data, traceId }`
- 密码/token/hash 不存明文，文件 ID 仅在服务端解析路径
- `@Autowired` 字段注入，`@Slf4j` 日志，UTF-8 编码，中文注释
- 详见 `docs/06-coding-standards.md`

## 参考

- `docs/01-architecture.md` — 架构、需求、安全基线
- `docs/02-database.md` — 表结构
- `docs/03-api.md` — 接口设计
