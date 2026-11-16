package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 平台级配置批量保存请求对象
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class PlatformSettingSaveDTO {

    /**
     * 待保存的配置项：配置键编码 → 配置值。
     * <p>
     * 仅允许 {@code PlatformSettingKeyEnum} 已登记的键；取值与代码默认值相同的键不写覆盖行，
     * 已有的覆盖行会被删除，即"改回默认即恢复默认"。
     */
    private Map<String, String> settings = new LinkedHashMap<>();

    @Override
    public String toString() {
        return "PlatformSettingSaveDTO{settings=" + settings + "}";
    }
}
