# 编码规范

## Java 后端

- JDK 17，包名 `com.baiflow`
- Controller → HTTP 映射与请求/响应转换，不写业务逻辑
- Service → 业务逻辑、权限校验、事务边界、文件操作
- Mapper → 纯 `BaseMapper`，不写自定义查询方法（单表查询在 Service 层 IService 完成）
- DTO / VO / Entity / Request 分离
- 统一返回 `{ code, message, data, traceId }`
- 异常通过全局异常处理器转换
- 文件路径必须 `Path.normalize()` + Storage Root 校验

### 注释规范
- Service 接口方法 → Javadoc（参数、返回值、业务含义），使用中文
- Service 实现复杂逻辑 → 中文行内注释说明意图
- Controller 方法 → 注释说明接口用途
- 用户可见消息 → 中文文案（作为 i18n key，经 `I18nUtil.translate()` 按 `Accept-Language` 返回中/英；动态拼接消息的前缀也走 `translate("前缀：")`）
- 源文件 UTF-8 编码

### Lombok
- Entity 用 `@Data`，只读字段用 `@Getter`
- 日志用 `@Slf4j`
- 不用 `@Builder`、`@AllArgsConstructor` 等可能歧义的注解

### 代码风格
- if/for/while 必须用大括号（即使一行）
- 依赖注入用 `@Autowired` 字段注入

## MyBatis Plus
- 每个实体有对应 `IService`（实体 Service）；领域 Service 可 `extends IService<主实体>` 或注入实体 Service
- 单表查询在 Service 层用 `lambdaQuery()` / `getOne` / `list` / `count` / `page` / `remove`，尽量不手写 SQL
- Mapper 保持纯 `BaseMapper<T>`（不写自定义查询方法）
- 多表 JOIN / 特殊 SQL（如 MySQL `ON DUPLICATE KEY UPDATE`）→ XML Mapper（仅剩审计登录日志 JOIN 与笔记进度 upsert）
- 分页用 MyBatis Plus 分页插件
- SQL 关键字大写、列名表名小写下划线、多行格式化、子句独占一行
- 逻辑删除字段统一 `deleted`
- SQL 日志 SLF4J 桥接

## 日志
- SLF4J + Logback（Spring Boot 默认），不用 `System.out`
- `@Slf4j` 获取 Logger
- MyBatis SQL 日志：`Slf4jImpl`，mapper 包 DEBUG
- HTTP 请求日志：`HttpLoggingFilter` 统一记录方法/URI/状态码/耗时

## MySQL
- 表名/字段名小写下划线
- 主键 `id`，时间字段 `created_at` / `updated_at` / `deleted_at`
- 密码/token/hash 不存明文

## 权限与安全
- 受保护 API 校验登录状态
- 角色：ADMIN / USER / GUEST
- 普通用户校验资源授权范围
- 不在日志中打印密码、token、绝对路径

## Vue 3 前端
- Composition API + `<script setup>`
- API 请求 → `src/api/`，页面 → `src/views/`，通用组件 → `src/components/`，状态 → `src/stores/`
- 危险操作确认弹窗，长任务展示 loading/进度/状态
- 权限不足显示明确提示

## Android Java
- 网络请求集中 network 模块
- token 由 Interceptor 注入，401 → 重新登录
- 长传输前台通知，Activity 不写复杂业务逻辑
- 网络/认证/权限失败明确提示

## API 设计
- REST 路径名词复数
- 集合：`GET /api/items` / `POST /api/items`
- 单个：`GET/PATCH/DELETE /api/items/{id}`
- 批量：`DELETE /api/items?ids=id1,id2`
- 分页：`page` / `size`
- 错误响应必须含数字 `code`（5 位错误码，见 `docs/03-api.md` 错误码表）和 `message`

## Git
- 提交信息：`feat:` / `fix:` / `docs:` / `test:` / `chore:`
- 不提交 `.env`、构建产物、上传/下载文件
