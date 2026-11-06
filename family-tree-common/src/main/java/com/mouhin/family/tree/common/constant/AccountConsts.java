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
     * 族谱节点自动开通账号的用户名前缀，完整用户名为 fp_{节点ID}
     */
    public static final String NODE_ACCOUNT_USERNAME_PREFIX = "fp_";
    /**
     * 自动开通账号的初始密码，用户登录后应自行修改
     */
    public static final String NODE_ACCOUNT_INITIAL_PASSWORD = "123456";

    private AccountConsts() {
    }
}
