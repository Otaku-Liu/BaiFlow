package com.baiflow.setup.service.impl;

import com.baiflow.auth.config.BaiflowProperties;
import com.baiflow.auth.dto.response.LoginResponse;
import com.baiflow.auth.service.AuthService;
import com.baiflow.audit.service.BfAuditLogService;
import com.baiflow.common.constant.ErrorCode;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.common.util.RequestUtil;
import com.baiflow.setup.dto.request.SystemSetupRequest;
import com.baiflow.setup.entity.BfSystemSetting;
import com.baiflow.setup.service.BfSystemSettingService;
import com.baiflow.setup.service.SystemSetupService;
import com.baiflow.user.dto.request.CreateUserRequest;
import com.baiflow.user.dto.response.UserInfo;
import com.baiflow.user.entity.BfUser;
import com.baiflow.user.enums.UserRole;
import com.baiflow.user.service.BfUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 系统初始化服务实现 — 首次部署创建第一个管理员。
 * <p>
 * 安全设计：
 * <ul>
 *   <li>初始化入口由 {@code bf_system_setting.initialized_at} 单向标记控制，写入后永久关闭</li>
 *   <li>入口本身公开，因此必须携带启动时生成的一次性令牌（打印到启动日志 + 落文件，仅属主可读）</li>
 *   <li>令牌比对使用常量时间比较；失败次数走 Redis 滑动窗口限流（窗口只在首次失败时开始计时，
 *       不会被连续请求无限续期）</li>
 *   <li>令牌在**事务提交成功后**才作废，避免回滚导致令牌丢失</li>
 * </ul>
 */
@Slf4j
@Service
public class SystemSetupServiceImpl implements SystemSetupService {

    /** 初始化完成标记的键名 */
    private static final String KEY_INITIALIZED_AT = "initialized_at";
    /** 令牌尝试失败次数上限（窗口内） */
    private static final int MAX_TOKEN_FAILURES = 10;
    /** 令牌尝试失败窗口（分钟） */
    private static final int TOKEN_FAILURE_MINUTES = 15;
    /** Redis 失败计数键前缀 */
    private static final String SETUP_FAIL_KEY = "setup:fail:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private BfSystemSettingService settingService;
    @Autowired
    private BfUserService userService;
    @Autowired
    private AuthService authService;
    @Autowired
    private BfAuditLogService auditService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private BaiflowProperties baiflowProperties;

    /** 进程内的当前初始化令牌（启动时准备，初始化成功提交后清空） */
    private volatile String activeToken;

    @Override
    public boolean isInitialized() {
        try {
            return settingService.count(new LambdaQueryWrapper<BfSystemSetting>()
                    .eq(BfSystemSetting::getSettingKey, KEY_INITIALIZED_AT)) > 0;
        } catch (Exception e) {
            // fail-closed：查不到状态时保守视为已初始化，宁可让用户查库也不要误开放入口
            log.warn("无法查询系统初始化状态（数据库表可能尚未创建），保守视为已初始化: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public String prepareOnStartup() {
        if (isInitialized()) {
            return null;
        }

        // 兜底：库中已存在管理员（老版本升级上来的部署没有初始化标记）→ 补写标记，永久关闭入口
        Long adminCount = userService.count(new LambdaQueryWrapper<BfUser>()
                .eq(BfUser::getRole, UserRole.ADMIN));
        if (adminCount != null && adminCount > 0) {
            try {
                markInitialized();
            } catch (DuplicateKeyException e) {
                log.debug("初始化标记已存在，无需补写");
            }
            log.warn("检测到已存在管理员用户，系统自动标记为已初始化；若需走首次初始化向导，"
                    + "请清空 bf_user 与 bf_system_setting 两张表后重启");
            return null;
        }

        return ensureToken();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse initialize(SystemSetupRequest request) {
        String ip = RequestUtil.getClientIp();
        String ua = RequestUtil.getClientUserAgent();

        // 先判初始化状态：已完成初始化的实例直接 403，既不消耗 Redis 配额，
        // 也不可能被令牌失败计数锁住入口
        if (isInitialized()) {
            throw new BusinessException(ErrorCode.SETUP_ALREADY_INITIALIZED, "系统已完成初始化，初始化入口已关闭");
        }
        if (isRateLimited(ip)) {
            throw new BusinessException(ErrorCode.SETUP_RATE_LIMITED, "初始化令牌错误次数过多，请稍后再试");
        }

        String expected = ensureToken();
        if (!constantTimeEquals(expected, request.setupToken())) {
            recordTokenFailure(ip);
            auditService.log(null, "SYSTEM_SETUP_FAILED", "SYSTEM", null, ip, ua, "初始化令牌不正确");
            throw new BusinessException(ErrorCode.SETUP_TOKEN_INVALID, "初始化令牌不正确，请在服务器启动日志中查看");
        }

        String username = request.username().trim();
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? username : request.displayName().trim();
        // 创建路径复用管理台同一套逻辑（用户名唯一校验 + BCrypt 哈希）
        UserInfo created = userService.createUser(
                new CreateUserRequest(username, request.password(), displayName, UserRole.ADMIN));

        // 标记写库：setting_key 唯一索引是并发下的最终防线（重复插入 → 事务整体回滚）
        try {
            markInitialized();
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SETUP_ALREADY_INITIALIZED, "系统已完成初始化，初始化入口已关闭");
        }

        closeEntryAfterCommit(ip);

        auditService.log(created.id(), "SYSTEM_SETUP", "USER", created.id(), ip, ua,
                "首次部署初始化完成，创建管理员：" + username);
        log.info("系统首次初始化完成：管理员 '{}' 已创建，初始化入口已关闭", username);

        BfUser admin = userService.getById(created.id());
        if (admin == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "管理员创建后读取失败");
        }
        // 直接签发登录会话，前端无需再输一次刚设置的密码
        return authService.issueSession(admin);
    }

    /** 写入初始化标记（幂等：已存在时唯一索引抛 DuplicateKeyException，由调用方决定处理） */
    private void markInitialized() {
        BfSystemSetting setting = new BfSystemSetting();
        setting.setSettingKey(KEY_INITIALIZED_AT);
        setting.setSettingValue(LocalDateTime.now().toString());
        settingService.save(setting);
    }

    /**
     * 关闭入口：作废令牌 + 清失败计数。
     * <p>
     * 必须等事务**提交成功**后再执行——在事务内删令牌文件、清内存令牌的话，
     * 一旦后续步骤回滚，系统仍是未初始化状态却再也拿不到原令牌。
     */
    private void closeEntryAfterCommit(final String ip) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidateToken();
            clearTokenFailures(ip);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidateToken();
                clearTokenFailures(ip);
            }
        });
    }

    private void invalidateToken() {
        activeToken = null;
        deleteTokenFile();
    }

    /** 取当前令牌：进程内已有则复用，否则优先复用令牌文件（重启不换），最后才新生成 */
    private synchronized String ensureToken() {
        if (activeToken != null) {
            return activeToken;
        }
        String token = readTokenFile();
        if (token == null || token.isBlank()) {
            token = generateToken();
            writeTokenFile(token);
        }
        activeToken = token;
        return token;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String readTokenFile() {
        Path path = Path.of(baiflowProperties.getSetup().getTokenPath());
        try {
            if (Files.exists(path)) {
                return Files.readString(path).trim();
            }
        } catch (IOException e) {
            log.warn("读取初始化令牌文件失败（{}）: {}", path, e.getMessage());
        }
        return null;
    }

    private void writeTokenFile(String token) {
        Path path = Path.of(baiflowProperties.getSetup().getTokenPath());
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, token);
            // 令牌等同管理员入口凭据，尽力收紧为仅属主可读（非 POSIX 文件系统忽略失败）
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                log.debug("当前文件系统不支持设置 POSIX 权限，跳过: {}", path);
            }
        } catch (IOException e) {
            log.warn("写入初始化令牌文件失败（{}），请仅从启动日志获取令牌: {}", path, e.getMessage());
        }
    }

    private void deleteTokenFile() {
        Path path = Path.of(baiflowProperties.getSetup().getTokenPath());
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除初始化令牌文件失败（{}）: {}", path, e.getMessage());
        }
    }

    /** 常量时间比较，避免按前缀逐字节试探令牌 */
    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isRateLimited(String ip) {
        try {
            String value = redisTemplate.opsForValue().get(SETUP_FAIL_KEY + ip);
            return value != null && Integer.parseInt(value) >= MAX_TOKEN_FAILURES;
        } catch (DataAccessException | NumberFormatException e) {
            log.warn("Redis 不可用，跳过初始化令牌限流检查: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 记录令牌尝试失败。
     * <p>
     * 窗口**只在首次失败时开始计时**：若每次都续期，任何人都能靠持续请求把窗口无限延长，
     * 把合法管理员永久挡在初始化入口之外。
     */
    private void recordTokenFailure(String ip) {
        try {
            String key = SETUP_FAIL_KEY + ip;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, TOKEN_FAILURE_MINUTES, TimeUnit.MINUTES);
            }
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过初始化令牌失败计数: {}", e.getMessage());
        }
    }

    private void clearTokenFailures(String ip) {
        try {
            redisTemplate.delete(SETUP_FAIL_KEY + ip);
        } catch (DataAccessException e) {
            log.warn("Redis 不可用，跳过清除初始化令牌失败计数: {}", e.getMessage());
        }
    }
}
