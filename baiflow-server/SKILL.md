---
name: baiflow-server
description: BaiFlow Spring Boot 后端：API、认证、MyBatis Plus、MySQL、文件操作、传输、通知
---

# BaiFlow Server

## 约束

- Controller → HTTP 映射 · Service → 业务逻辑 · Mapper → SQL
- DTO / VO / Entity / Request 分离；统一返回 `{ code, message, data, traceId }`
- 密码 / token 只存 hash；文件 ID 到服务端路径的解析只在后端发生
- `@Autowired` 字段注入、`@Slf4j` 日志、UTF-8 编码、中文注释
- 绑定 `bf_*` 表的类加 `Bf` 前缀并**按表名命名**（Entity / Mapper / Service(+Impl) / Controller）；无单一主表的业务类与 DTO / VO / enum 不带 `Bf`

编码细则（注释、Lombok、MyBatis Plus、日志、API 设计）见 `docs/06-coding-standards.md`；架构 / 表结构 / 接口见 `docs/01`、`docs/02`、`docs/03`。
