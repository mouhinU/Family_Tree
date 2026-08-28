package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 家族信息更新请求（堂号、祖籍）
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
public class FamilyInfoUpdateDTO {

    /**
     * 堂号
     */
    private String hallName;

    /**
     * 祖籍
     */
    private String ancestralHome;
}
