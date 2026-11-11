package com.mouhin.family.tree.common.constant;

/**
 * 账号开通常量
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
public final class AccountConsts {

    /**
     * 密码最小长度
     */
    public static final int MIN_PASSWORD_LENGTH = 6;
    /**
     * 邮箱最大长度（与 sys_user.email 列宽一致）
     */
    public static final int MAX_EMAIL_LENGTH = 100;
    /**
     * 手机号最大长度（与 sys_user.phone 列宽一致）
     */
    public static final int MAX_PHONE_LENGTH = 20;
    /**
     * 手机号格式：允许空串（选填留空时前端提交空字符串），非空须为11位大陆手机号
     */
    public static final String PHONE_REGEXP = "^$|^1[3-9]\\d{9}$";
    /**
     * 登录标识中的手机号形态（纯数字且长度达到手机号位数）
     */
    public static final int PHONE_DIGIT_LENGTH = 11;
    /**
     * 族谱节点自动开通账号的兜底用户名前缀：姓名不可用（脱敏/空）或重名时，用户名为 fp_{节点ID}
     */
    public static final String NODE_ACCOUNT_USERNAME_PREFIX = "fp_";
    /**
     * 族谱节点自动开通账号的初始口令。仅作首登引导用：账号开通即置 must_change_password，
     * 首次登录强制修改后此口令失效（一次性）。因自动开通无人工投递渠道，暂沿用固定引导口令，
     * 后续如需随机化需同步引入口令下发流程。
     */
    public static final String NODE_ACCOUNT_INITIAL_PASSWORD = "123456";

    private AccountConsts() {
    }
}
