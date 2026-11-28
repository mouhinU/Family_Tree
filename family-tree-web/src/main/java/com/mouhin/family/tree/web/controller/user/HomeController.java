package com.mouhin.family.tree.web.controller.user;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 公开首页与已登录应用入口路由。
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Controller
public class HomeController {

    private static final String APP_ENTRY = "/index.html";
    private static final String HOME_ENTRY = "/home.html";

    /**
     * 根路径按会话状态展示公开首页或族谱工作台。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @return 内部转发的静态页面
     */
    @GetMapping("/")
    public String home(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        boolean authenticated = session != null
                && session.getAttribute(FamilyTreeConsts.SESSION_USER_ID) != null;
        return "forward:" + (authenticated ? APP_ENTRY : HOME_ENTRY);
    }

    /**
     * 公开首页的固定入口，登录状态不影响可访问性。
     *
     * @param response HTTP 响应
     * @return 内部转发的公开首页
     */
    @GetMapping("/home")
    public String publicHome(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return "forward:" + HOME_ENTRY;
    }
}
