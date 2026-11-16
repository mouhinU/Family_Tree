package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台侧家族治理请求：改名、变更状态或转让族长，按端点各取一个字段。
 *
 * <p>各端点只消费自身字段，因此不启用 Bean Validation：
 * 名称由领域对象 Family 校验，状态按 FamilyStatusEnum 白名单校验，
 * 新族长ID由应用服务校验存在性与成员归属。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class FamilyGovernDTO {

    /**
     * 新家族名称（改名时必填）
     */
    private String name;

    /**
     * 目标家族状态编码（变更状态时必填）
     */
    private String status;

    /**
     * 新族长用户ID（转让族长时必填，须为该家族成员）
     */
    private Long userId;
}
