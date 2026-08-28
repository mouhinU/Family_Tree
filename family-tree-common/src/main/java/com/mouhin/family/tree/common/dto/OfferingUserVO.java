package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 祭奠人员统计视图对象（某用户对某长辈的累计祭奠情况）
 *
 * @author Family-Tree
 * @date 2026-08-01
 */
@Getter
@Setter
public class OfferingUserVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 累计次数
     */
    private Long count;

    /**
     * 最近一次祭奠时间（yyyy-MM-dd HH:mm）
     */
    private String lastTime;
}
