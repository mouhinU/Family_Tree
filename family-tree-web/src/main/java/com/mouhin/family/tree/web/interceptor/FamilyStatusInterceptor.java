package com.mouhin.family.tree.web.interceptor;

import com.mouhin.family.tree.application.service.FamilyStatusGuardApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.enums.FamilyStatusEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 家族状态拦截器：把平台侧的冻结 / 归档标记落到家族内的请求入口。
 *
 * <p>冻结的家族只读：写请求一律拒绝，浏览不受影响。归档的家族对普通成员整体下线：
 * 读写请求都拒绝，仅保留登录、平台管理接口与"切换 / 加入 / 新建家族"这几条逃生入口，
 * 使成员仍能离开已归档家族。平台管理员本人通过 {@code /api/admin/**} 操作，不受影响。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Component
public class FamilyStatusInterceptor implements HandlerInterceptor {

    /**
     * 平台侧与认证侧接口：不落入单个家族的读写治理范围
     */
    private static final List<String> ALWAYS_ALLOWED_PREFIXES = List.of("/api/auth", "/api/admin");

    /**
     * 家族下线时仍需放行的逃生入口：查看我的家族、切换 / 加入家族
     */
    private static final List<String> ESCAPE_PATHS = List.of("/api/family/my-list", "/api/family/join");

    /**
     * 切换家族的带参路径前缀
     */
    private static final String SWITCH_FAMILY_PREFIX = "/api/family/switch/";

    /**
     * 新建家族接口：写入的是新家族而非当前家族
     */
    private static final String CREATE_FAMILY_PATH = "/api/family";

    /**
     * 只读方法，冻结状态下仍允许访问
     */
    private static final List<String> SAFE_METHODS = List.of("GET", "HEAD", "OPTIONS");

    private static final String FROZEN_MESSAGE = "该家族已被平台冻结，暂不可修改数据";
    private static final String ARCHIVED_MESSAGE = "该家族已被平台归档，仅平台管理员可查看";

    private final FamilyStatusGuardApplicationService familyStatusGuardService;

    public FamilyStatusInterceptor(FamilyStatusGuardApplicationService familyStatusGuardService) {
        this.familyStatusGuardService = familyStatusGuardService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path == null || isAlwaysAllowed(path) || isEscapePath(request, path)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return true;
        }
        Long familyId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID);
        FamilyStatusEnum status = familyStatusGuardService.statusOf(familyId);
        if (status == FamilyStatusEnum.NORMAL) {
            return true;
        }
        if (status.hidesFromMembers()) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, ARCHIVED_MESSAGE);
            return false;
        }
        if (status.blocksWrites() && !isSafeMethod(request)) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, FROZEN_MESSAGE);
            return false;
        }
        return true;
    }

    private boolean isAlwaysAllowed(String path) {
        for (String prefix : ALWAYS_ALLOWED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean isEscapePath(HttpServletRequest request, String path) {
        if (path.startsWith(SWITCH_FAMILY_PREFIX) || ESCAPE_PATHS.contains(path)) {
            return true;
        }
        return CREATE_FAMILY_PATH.equals(path) && "POST".equals(request.getMethod());
    }

    private boolean isSafeMethod(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod());
    }

    private void reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
