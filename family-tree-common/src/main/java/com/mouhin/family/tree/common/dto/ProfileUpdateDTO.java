package com.mouhin.family.tree.common.dto;

import lombok.Data;

/**
 * 个人信息更新请求对象
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Data
public class ProfileUpdateDTO {

    /** 昵称 */
    private String nickname;

    /** 出生日期（yyyy-MM-dd） */
    private String birthDate;

    /** 所属辈分（第几世） */
    private Integer generation;
}
