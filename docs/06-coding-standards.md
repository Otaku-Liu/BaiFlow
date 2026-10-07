# 编码规范

## Java 后端

- JDK 17，包名 `com.baiflow`
- Controller → HTTP 映射与请求/响应转换，不写业务逻辑
- Service → 业务逻辑、权限校验、事务边界、文件操作
- Mapper → 纯 `BaseMapper`，不写自定义查询方法
- DTO / VO / Entity / Request 分离
- **命名规则**：绑定 `bf_*` 表的类统一 `Bf` 前缀、**按表名命名**（Entity / Mapper / Service(+Impl) / Controller 四层，如 `bf_share_link` → `BfShareLink` / `BfShareLinkMapper` / `BfShareLinkService`）；无单一主表的业务类（`AuthService`、`PublicShareController`、`HealthService`）不带 `Bf`；DTO / VO / Request / enum 保持原名。
- 统一返回 `{ code, message, data, traceId }`
- 异常通过全局异常处理器转换
- 文件路径必须 `Path.normalize()` + Storage Root 校验
- **认证与授权判定集中在 `com.baiflow.auth.security.SecurityUtils`**：管理员判定走 `isAdmin(Authentication)`、「属主或管理员」走 `isOwnerOrAdmin(ownerId, userId, isAdmin)`，不在控制层/服务层就地拼判定（就地版本曾同时存在两种写法：5 份带空值守卫、2 份不带（`BfFileItemController` / `BfShareLinkController`），**后者**会在认证主体为空时 NPE）。两个方法都只返回布尔，失败动作由调用点决定（抛 `40301` 或静默跳过）。注意视图范围条件（`!isAdmin || viewUserId != null`）与 MyBatis 的 `.eq(!isAdmin, …)` 是查询过滤，不属本规则
- **不为 Redis 降级块抽通用包装类**：`try { … } catch (DataAccessException e) { log.warn("Redis 不可用…") }` 本身只有数行，而各键族的键前缀、TTL 与降级策略各不相同，把这一层抽成通用包装属过度抽象，各自保留更划算。**「键是否存在」这类判定尤其不抽公共类**：各调用点的降级方向本就相反（登录前置检查放行、解锁判定维持、定时任务跳过），抽成三态组件后仍要由调用点决定方向，只是多一层间接。其余跨键族的复用按具体场景单独讨论；**不涉及键判定的多行逻辑仍照常提取，且归到拥有该数据的服务上**（如登录锁 LOCKED→NORMAL 恢复改的是 `BfUser` 的状态，收在 `BfUserService.restoreLockedUser`，遵循本节「代码风格」的多行提取规则）。键前缀见 `docs/02-database.md`，锁语义见 `docs/03-api.md`
- **审计日志的 `action` / `target_type` 取值集中在 `com.baiflow.audit.constant.AuditAction` / `AuditTargetType`**，调用处不写字符串字面量。字符串值是对外契约（DB 列值、登录日志接口 `status` 参数、Web 端 `LoginLogsView.vue` 与 XML 的 `action IN (...)` 均按字面量匹配），**只增不改**；仅登录/会话类取值会出现在管理员登录日志页，其余只入库
- **审计目标直传 `targetType` + `targetId` 两个参数（`log(actorUserId, action, targetType, targetId, ip, userAgent, detail)`），不为目标引入值对象**：四类目标就是 `AuditTargetType` 的四个常量，包一层工厂类等于把同一组概念写两遍（曾存在工厂类 `BfAuditLogService.AuditTarget`，其 `user/session/device/system` 与常量一一对应，已删除）；唯一不变量是「`SYSTEM` 无 ID」，由调用处传 `null` 表达，语义见接口 Javadoc

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
- **多个注解各占一行**：方法 / 类 / 字段上有多个注解时分行写（`@Override` 与 `@Transactional` 不写在同一行）；参数上的注解放签名行内（`@Valid @RequestBody Xxx req`）不受此限
- **依赖注入用构造器注入**：依赖字段写 `private final`，类上加 `@RequiredArgsConstructor`（Lombok 生成构造器）；单构造器由 Spring 自动装配，**不写 `@Autowired`**，也不用 `@Autowired` 字段注入
- **定时任务统一放 `com.baiflow.schedule` 包（`XxxScheduler`），不内嵌在 Service 层**（即使 Service 内某个方法需要定时触发，也抽到独立 Scheduler 调用它）
- **不用 `var`，变量声明一律写明确类型**（泛型 + 链式调用时 `var` 可能推断出意外类型，显式类型更清晰、编译期可控）
- **不为一两行的简单逻辑单独提取方法**（一两处调用直接内联到调用处）；只有多条调用共用且逻辑多行时才值得提取

## MyBatis Plus
- 每个实体有对应 `IService`（实体 Service）；领域 Service 可 `extends IService<主实体>` 或注入实体 Service
- 单表查询在 Service 层用 `lambdaQuery()` / `getOne` / `list` / `count` / `page` / `remove`，尽量不手写 SQL
- 条件构造器优先用 `LambdaQueryWrapper` / `LambdaUpdateWrapper`（列名走方法引用，编译期类型安全、重命名字段不炸），**不使用字符串列名的 `QueryWrapper`**
- Mapper 保持纯 `BaseMapper<T>`（不写自定义查询方法）
- 多表 JOIN / 特殊 SQL（如 MySQL `ON DUPLICATE KEY UPDATE`）→ XML Mapper（仅剩审计登录日志 JOIN 与笔记进度 upsert）
- 分页用 MyBatis Plus 分页插件
- SQL 关键字大写、列名表名小写下划线、多行格式化、子句独占一行
- SQL 日志 SLF4J 桥接

## 日志
- SLF4J + Logback（Spring Boot 默认），不用 `System.out`
- `@Slf4j` 获取 Logger
- **箭头统一**：框架自己打的日志一律用 `==>`（出向）/ `<==`（回向），与 MyBatis 的 `==>  Preparing:` / `<==      Total:` 同一套，不混用 `→` / `←`
- MyBatis SQL 日志：`Slf4jImpl`，mapper 包 DEBUG（但只在 dev profile 默认开）
- HTTP 请求日志：`HttpLoggingFilter` 统一记录方法/URI/状态码/耗时，以及请求头/参数/请求体/响应体（**不脱敏**，故日志不得外发）；下载/预览/SSE/头像路径不走该过滤器
- **SQL 计时**：每条 SQL 在 SQL 内容之后另起一行 `==> 执行时间：xxms`（与 MyBatis 同一个 logger，方法名即左侧那一列）
- 请求链路用 `X-Trace-Id` + MDC（日志 pattern 含 `%X{traceId}`），响应体由 `ApiResponse` 回填同一 id
- 以上都在 `docs/01-architecture.md`「日志与可观测性」有完整说明（不做请求级汇总，要数条数就数这些行）；见 `docs/01-architecture.md`「SQL 计时日志」

## 数据库往返

数据库在远端、单次往返 40–75ms（见 `docs/01-architecture.md`「数据库连接与往返成本」），**每个接口的往返次数当作预算来管**。写代码前先数一遍要发几条查询，改完在提交信息里说明往返次数的变化。

- **同一行、同一值不在同一请求内查两次**，跨层也算：过滤器/拦截器已经读到的实体存进 request attribute 复用（如已登录用户），Service 不要再查一遍
- **禁止 N+1**：循环里 `getById`/`selectById`/`count` 一律改批量（`listByIds` / `selectBatchIds` / `in(...)`）；逐条 `save`/`updateById` 改批量
- **列表接口的分页方式看目录规模**：MyBatis-Plus 的 `page()` 默认**先发一条 COUNT 再发分页查询 = 两次往返**，而 `list()` + 内存切片是一次往返、`total` 白拿。个人规模（几十~几百项）用后者更省；目录可能到数千项时再改 `page()`（或关掉 `searchCount` 自行控制），别不假思索用 `page()`
- **读接口不写库**：GET/POST 查询类接口里的 `update`/`insert` 要挪成异步或定时落库
- **逐级遍历父链/子树**不要循环查询：先取自身行的路径，推出各级祖先路径后一条 `IN` 查询拿回整条链；确需递归时用一条 CTE（先例：`BfFileItemMapper.xml` 的 `sumFolderSize`）
- **「确保存在」式检查**合并成一次查询，不要每次请求各查一遍
- **查询只选需要的列**：列表 VO 不带该页面用不到的字段（如 `hash_sha256`、`relative_path`）；计数类查询只 `select` 主键或分组列
- 排查手段：SQL 计时日志（每条 DEBUG 一行耗时），见 `docs/01-architecture.md`「SQL 计时日志」

## MySQL
- 表名/字段名小写下划线
- 主键 `id`，时间字段 `created_at` / `updated_at` / `deleted_at`（逻辑删除时间，`NULL` 表示未删除）
- 密码/token/hash 不存明文

## 权限与安全
- 受保护 API 校验登录状态
- 角色：ADMIN / USER / GUEST
- 普通用户校验资源授权范围
- 不在日志中打印绝对路径；**请求/响应体目前有意不脱敏**（便于排查，见 `docs/01-architecture.md`「HTTP 请求日志」）→ **日志文件不得外发**

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
