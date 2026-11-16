package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 家族级配置数据对象
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@Getter
@Setter
@TableName("family_setting")
public class FamilySettingDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 配置键（白名单枚举）
     */
    private String settingKey;

    /**
     * 配置值（按配置键类型校验后落库）
     */
    private String settingValue;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
