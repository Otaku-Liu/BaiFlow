package com.baiflow.audit.service;

import com.baiflow.audit.constant.AuditTargetType;
import com.baiflow.audit.dto.response.LoginLogVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 审计服务接口 — 异步记录操作审计日志，并提供管理端查询能力。
 */
public interface BfAuditLogService {

    /**
     * 审计目标：类型与 ID 成对出现，只能经静态工厂构造。
     * <p>
     * 存在的理由：类型与 ID 在调用处总是相邻，若作为两个独立参数传入，极易与同为 String 的
     * action 整体错排，而编译器不会报错。私有构造 + 工厂方法把 {@code type} 锁死在工厂名里，
     * 调用方只能提供 ID —— {@code new AuditTarget(...)} 与 type/id 互换在编译期就传不过去。
     * <p>
     * 用 final class 而非 record：record 的规范构造器访问级别不得低于 record 本身、无法私有化，
     * 那样工厂只能是「约定」而非「强制」；且此处并不需要 record 的 equals / toString / 解构。
     */
    final class AuditTarget {

        private final String type;
        private final String id;

        private AuditTarget(String type, String id) {
            this.type = type;
            this.id = id;
        }

        /** 用户目标（id = 用户 ID；登录失败且用户名不存在时，为提交上来的用户名） */
        public static AuditTarget user(String id) {
            return new AuditTarget(AuditTargetType.USER, id);
        }

        /** 登录会话目标（id = 会话 ID） */
        public static AuditTarget session(String id) {
            return new AuditTarget(AuditTargetType.SESSION, id);
        }

        /** 登录设备目标（id = 设备名） */
        public static AuditTarget device(String id) {
            return new AuditTarget(AuditTargetType.DEVICE, id);
        }

        /** 系统级目标（无目标 ID，落库时 target_id 为空） */
        public static AuditTarget system() {
            return new AuditTarget(AuditTargetType.SYSTEM, null);
        }

        /** 目标类型，取值见 {@link AuditTargetType} */
        public String type() {
            return type;
        }

        /** 目标 ID，可空（系统级目标无 ID） */
        public String id() {
            return id;
        }
    }

    /**
     * 记录一条审计日志。
     * @param actorUserId 操作者 ID（可为空）
     * @param action      操作类型，取值见 {@link com.baiflow.audit.constant.AuditAction}
     * @param target      审计目标，不可为空；类型取值见 {@link com.baiflow.audit.constant.AuditTargetType}
     * @param ipAddress   IP 地址
     * @param userAgent   User-Agent
     * @param detail      操作详情
     */
    void log(String actorUserId, String action, AuditTarget target,
             String ipAddress, String userAgent, String detail);

    /**
     * 分页查询登录日志。
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     * @param username  用户名模糊搜索（可选）
     * @param status    LOGIN_SUCCESS / LOGIN_FAILED（可选）
     * @param startDate 开始日期（可选，格式 yyyy-MM-dd）
     * @param endDate   结束日期（可选，格式 yyyy-MM-dd）
     */
    Page<LoginLogVO> queryLoginLogs(int page, int size, String username, String status,
                                    String startDate, String endDate);
}
