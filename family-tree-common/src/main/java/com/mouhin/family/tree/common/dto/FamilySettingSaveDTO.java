package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 家族配置批量保存请求对象
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class FamilySettingSaveDTO {

    /**
     * 待保存的配置项：配置键编码 → 配置值。
     * <p>
     * 仅允许 {@code FamilySettingKeyEnum} 已登记的键；未登记的键与超量请求由领域服务拒绝。
     */
    private Map<String, String> settings = new LinkedHashMap<>();
}
