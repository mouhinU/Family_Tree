package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 控制器基类，提供会话信息提取的公共方法
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
public abstract class BaseController {

    /**
     * 从会话中获取当前登录用户ID
     *
     * @param session HTTP 会话
     * @return 用户ID
     */
    protected Long getCurrentUserId(HttpSession session) {
        return (Long) session.getAttribute(FamilyTreeConsts.SESSION_USER_ID);
    }

    /**
     * 从会话中获取当前家族ID
     *
     * @param session HTTP 会话
     * @return 家族ID
     * @throws BusinessException 未加入/创建家族时抛出
     */
    protected Long getCurrentFamilyId(HttpSession session) {
        Long familyId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID);
        if (familyId == null) {
            throw new BusinessException("请先加入或创建家族");
        }
        return familyId;
    }

    /**
     * 获取客户端真实IP（兼容反向代理）
     *
     * @param request HTTP 请求
     * @return 客户端IP地址
     */
    protected String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
