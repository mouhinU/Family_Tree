package com.mouhin.family.tree.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 巡检用节点生日行：仅承载农历/阳历一致性重算所需的最小列。
 *
 * @author Family-Tree
 * @date 2026-11-25
 */
@Getter
@Setter
public class NodeLunarRowDO {

    /**
     * 节点主键
     */
    private Long id;

    /**
     * 节点姓名
     */
    private String name;

    /**
     * 阳历出生日期（唯一真值）
     */
    private LocalDate birthDate;

    /**
     * 登记的农历生日串
     */
    private String lunarBirthDate;

    @Override
    public String toString() {
        return "NodeLunarRowDO{id=" + id + ", name='" + name + '\''
                + ", birthDate=" + birthDate + ", lunarBirthDate='" + lunarBirthDate + '\'' + '}';
    }
}
