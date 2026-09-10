package com.baiflow.setup.dto.response;

/**
 * 系统初始化状态 — 供客户端判断是否需要走首次部署向导。
 *
 * @param initialized 是否已完成首次初始化（true 表示初始化入口已永久关闭）
 */
public record SystemSetupStatus(boolean initialized) {
}
