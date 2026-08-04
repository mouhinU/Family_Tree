package com.mouhin.family.tree.service;

import java.util.List;

/**
 * 忌日提醒服务接口
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
public interface DeathAnniversaryService {

    /**
     * 获取未来 N 天内的忌日提醒列表
     *
     * @param familyId 家族ID
     * @param days     提前天数（含今天）
     * @return 即将到忌日的节点列表（含忌日日期和剩余天数）
     */
    List<DeathAnniversaryDTO> getUpcoming(Long familyId, int days);

    /**
     * 忌日提醒 DTO
     */
    class DeathAnniversaryDTO {
        private Long nodeId;
        private String name;
        private String deathDate;
        private int daysUntil;

        public Long getNodeId() { return nodeId; }
        public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDeathDate() { return deathDate; }
        public void setDeathDate(String deathDate) { this.deathDate = deathDate; }
        public int getDaysUntil() { return daysUntil; }
        public void setDaysUntil(int daysUntil) { this.daysUntil = daysUntil; }

        @Override
        public String toString() {
            return "DeathAnniversaryDTO{"
                    + "nodeId=" + nodeId
                    + ", name='" + name + '\''
                    + ", deathDate='" + deathDate + '\''
                    + ", daysUntil=" + daysUntil
                    + '}';
        }
    }
}
