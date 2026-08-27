package com.mouhin.family.tree.common.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 智能录入响应对象（节点 + 关系）
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Data
public class AiSmartEntryVO {

    private List<AiNodeDTO> nodes;
    private List<AiRelationDTO> relations;

    @Data
    public static class AiNodeDTO {
        private String name;
        private Integer gender;
        private String birthDate;
        private String deathDate;
        private String lunarBirthDate;
        private String lunarDeathDate;
        private String zi;
        private String hao;
        private String hui;
        private String graveLocation;
        private String spouseName;
        private String spouseOriginFamily;
        private String remark;
    }

    @Data
    public static class AiRelationDTO {
        private String fromName;
        private String toName;
        /**
         * 1=亲子, 2=夫妻, 3=收养
         */
        private Integer relationType;
        private String marriageDate;
    }
}
