package com.mouhin.family.tree.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 祭奠统计视图对象（某长辈某类祭奠的汇总：总次数、人员明细）
 *
 * @author Family-Tree
 * @date 2026-08-01
 */
@Data
public class OfferingStatVO {

    /**
     * 祭奠类型：1-香烛 2-烧纸
     */
    private Integer offeringType;

    /**
     * 祭奠类型名称（香烛 / 烧纸）
     */
    private String typeName;

    /**
     * 累计总次数
     */
    private Long totalCount;

    /**
     * 参与人数（去重）
     */
    private Integer userCount;

    /**
     * 人员明细（按累计次数降序）
     */
    private List<OfferingUserVO> users = new ArrayList<>();
}
