# BaiFlow 编码规范

## Java 后端
- 使用 JDK 17。
- 包名使用 `com.baiflow` 开头。
- Controller 只处理请求和响应，不写业务逻辑。
- Service 承载业务逻辑、权限校验、事务边界和文件操作顺序。
- Mapper 只负责数据库访问。
- DTO、VO、Entity 分离。
- 统一响应结构，不在 Controller 中直接返回裸对象。
- 异常通过全局异常处理器转换为统一错误响应。
- 文件路径处理必须使用 `Path.normalize()` 和 Storage Root 校验。
- Service 接口方法必须有 Javadoc 注释，说明参数、返回值和业务含义。注释和代码中的提示信息使用中文。
- Service 实现类中复杂逻辑（事务边界、路径安全校验、文件操作编排）必须带中文行内注释说明意图。
- Controller 方法必须有注释说明接口用途、请求参数和返回内容，使用中文。
- 所有用户可见的提示信息（错误消息、响应消息）使用中文。
- 源文件使用 UTF-8 编码，确保中文不乱码。
- 所有 if/for/while 语句必须使用大括号，即使只有一行执行体。
- 后续阶段 TODO：引入 i18n 消息资源束以支持多语言切换。

### Lombok
- Entity 类使用 `@Data` 自动生成 getter/setter/toString/equals/hashCode。
- 仅需只读字段的类（如异常类）使用 `@Getter`。
- 配置属性类（@ConfigurationProperties）使用 `@Data` 替代手写 getter/setter。
- 不使用 Lombok 的 @Builder、@AllArgsConstructor 等可能引发歧义的注解。

### 实体类与 DDL 注释规范
- 每个实体类必须有类级 Javadoc，说明该实体对应的业务概念和核心用途。
- 实体的每个字段必须有字段级 Javadoc（`/** ... */`），说明字段的业务含义和取值范围（如枚举、null 的语义）。
- 枚举类中的每个枚举值必须有注释说明其含义。
- 数据库建表 SQL 脚本（`db/migration/` 下）作为参考文档保留，不再通过 Flyway 自动执行。
- DDL 脚本中的字段使用 `COMMENT` 描述业务含义，表使用注释说明用途。
- DDL 中的 comment 内容应与实体类字段 Javadoc 保持一致（中文）。

## 权限与安全
- 所有受保护 API 必须校验登录状态。
- 角色统一使用 `ADMIN`、`USER`、`GUEST`。
- 普通用户必须校验资源授权范围。
- 访客只能访问公开分享接口。
- 分享 token、提取码、隐私文件夹密码只保存 hash。
- 不在日志中打印密码、提取码、token、绝对文件路径。
- 分享访问、权限拒绝、隐私密码失败必须记录可审计信息。

## MyBatis Plus
- 简单 CRUD 使用 BaseMapper。
- 复杂查询使用 XML Mapper。
- 分页统一使用 MyBatis Plus 分页插件。
- 不在 XML 中拼接未清洗的用户输入。
- 逻辑删除字段统一为 `deleted`。

## MySQL
- 表名小写下划线。
- 字段名小写下划线。
- 主键统一为 `id`。
- 金额、大小、进度等字段明确单位。
- 所有业务表包含 `created_at` 和 `updated_at`。
- 需要审计的表保留操作日志。

## Vue 3
- 使用 Composition API 和 `<script setup>`。
- API 请求集中放在 `src/api/`。
- 页面级组件放在 `src/views/`。
- 通用组件放在 `src/components/`。
- 状态管理放在 `src/stores/`。
- 所有危险操作必须有确认弹窗。
- 长任务必须展示 loading、进度或状态。
- 权限不足时显示明确提示，不隐藏服务端错误。

## Android Java
- 网络请求集中在 network 模块。
- token 由统一 Interceptor 注入。
- 401 响应触发重新登录。
- 长时间上传下载必须使用前台通知。
- Activity 不直接写复杂业务逻辑。
- 网络失败、认证失败、文件权限失败必须明确提示。

## API
- REST 路径使用名词复数。
- 修改状态使用 POST 或 PATCH。
- 删除使用 DELETE。
- 分页参数统一为 `page` 和 `size`。
- 错误响应必须包含 `code` 和 `message`。
- 公开分享 API 必须与登录用户 API 分开命名。

## Git 与提交
- 每个阶段单独提交。
- 提交信息建议：`feat:`、`fix:`、`docs:`、`test:`、`chore:`。
- 不把 `.env`、构建产物、上传文件、下载文件提交到仓库。
