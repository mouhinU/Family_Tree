package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 跨族亲族候选视图对象（证据：姓名相同 + 生日吻合，仅管理员可见，确认后才成链）
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@Setter
public class AffinityCandidateVO {

    /**
     * 候选节点ID（他族）
     */
    private Long nodeId;

    /**
     * 候选节点姓名
     */
    private String nodeName;

    /**
     * 候选性别编码
     */
    private Integer gender;

    /**
     * 候选出生日期（ISO 字符串，可为 null）
     */
    private String birthDate;

    /**
     * 候选农历生日（可为 null）
     */
    private String lunarBirthDate;

    /**
     * 候选所在家族ID
     */
    private Long familyId;

    /**
     * 候选所在家族名称
     */
    private String familyName;

    /**
     * 匹配置信度：2-高（录入历法口径一致精确吻合） 1-中（未记录历法的回落比对命中）
     */
    private Integer confidence;
}
