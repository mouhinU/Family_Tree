package com.mouhin.family.tree.common.constant;

/**
 * 族谱业务常量
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
public final class FamilyTreeConsts {

    /**
     * 默认世代层级
     */
    public static final int DEFAULT_GENERATION = 1;
    /**
     * 最大世代层级深度
     */
    public static final int MAX_GENERATION_DEPTH = 100;
    /**
     * 节点名称最大长度
     */
    public static final int MAX_NAME_LENGTH = 50;
    /**
     * 备注最大长度
     */
    public static final int MAX_REMARK_LENGTH = 500;
    /**
     * Session 中用户ID的 key
     */
    public static final String SESSION_USER_ID = "SESSION_USER_ID";
    /**
     * Session 中用户名的 key
     */
    public static final String SESSION_USERNAME = "SESSION_USERNAME";
    /**
     * Session 中当前家族ID的 key
     */
    public static final String SESSION_FAMILY_ID = "SESSION_FAMILY_ID";
    /**
     * 邀请码长度
     */
    public static final int INVITE_CODE_LENGTH = 8;
    /**
     * 单个家族最大成员数
     */
    public static final int MAX_FAMILY_MEMBERS = 50;
    /**
     * 写接口限流：滑动窗口时长（秒）
     */
    public static final int RATE_LIMIT_WINDOW_SECONDS = 60;
    /**
     * 写接口限流：窗口内最大请求数
     */
    public static final int RATE_LIMIT_MAX_REQUESTS = 30;
    /**
     * 默认分页页码
     */
    public static final int DEFAULT_PAGE = 1;
    /**
     * 默认分页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 50;

    private FamilyTreeConsts() {
    }
}
