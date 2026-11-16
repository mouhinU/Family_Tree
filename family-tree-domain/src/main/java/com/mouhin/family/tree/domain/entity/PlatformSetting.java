package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 平台级配置实体
 *
 * <p>只保存被显式覆盖的配置项，未覆盖的取值由 {@code PlatformSettingKeyEnum} 的默认值提供，
 * 因此 settingKey 唯一，新增配置项无需回填数据。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class PlatformSetting {

    /**
     * 配置值最大长度，与 platform_setting.setting_value 列宽一致
     */
    public static final int MAX_VALUE_LENGTH = 200;

    private Long id;
    private String settingKey;
    private String settingValue;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验配置记录数据的完整性
     */
    public void validate() {
        if (settingKey == null || settingKey.isBlank()) {
            throw new BusinessException("配置缺少配置键");
        }
        if (settingValue == null) {
            throw new BusinessException("配置缺少配置值");
        }
        if (settingValue.length() > MAX_VALUE_LENGTH) {
            throw new BusinessException("配置值长度不能超过" + MAX_VALUE_LENGTH);
        }
    }

    /**
     * 判断配置是否指向指定配置键
     *
     * @param targetKey 待比对的配置键
     * @return 是否指向该配置键
     */
    public boolean isKey(String targetKey) {
        return Objects.equals(settingKey, targetKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PlatformSetting that = (PlatformSetting) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "PlatformSetting{"
                + "id=" + id
                + ", settingKey='" + settingKey + '\''
                + '}';
    }
}
