package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.service.DeathAnniversaryDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 忌日提醒应用服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class DeathAnniversaryApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DeathAnniversaryApplicationService.class);

    private final FamilyNodeRepository familyNodeRepository;
    private final DeathAnniversaryDomainService deathAnniversaryDomainService;

    public DeathAnniversaryApplicationService(FamilyNodeRepository familyNodeRepository,
                                              DeathAnniversaryDomainService deathAnniversaryDomainService) {
        this.familyNodeRepository = familyNodeRepository;
        this.deathAnniversaryDomainService = deathAnniversaryDomainService;
    }

    /**
     * 获取未来 N 天内的忌日提醒列表
     *
     * @param familyId 家族ID
     * @param days     提前天数（含今天）
     * @return 忌日提醒列表
     */
    public List<DeathAnniversaryDTO> getUpcoming(Long familyId, int days) {
        List<FamilyNode> deceasedNodes = familyNodeRepository.findDeceasedByFamilyId(familyId);
        List<DeathAnniversaryDomainService.AnniversaryInfo> infos =
                deathAnniversaryDomainService.calculateUpcoming(deceasedNodes, days);

        return infos.stream()
                .map(this::toDTO)
                .toList();
    }

    private DeathAnniversaryDTO toDTO(DeathAnniversaryDomainService.AnniversaryInfo info) {
        DeathAnniversaryDTO dto = new DeathAnniversaryDTO();
        dto.setNodeId(info.getNodeId());
        dto.setName(info.getNodeName());
        dto.setDeathDate(info.getDeathDate() != null ? info.getDeathDate().toString() : null);
        dto.setDaysUntil((int) info.getDaysUntil());
        return dto;
    }

    /**
     * 忌日提醒 DTO
     */
    public static class DeathAnniversaryDTO {

        private Long nodeId;
        private String name;
        private String deathDate;
        private int daysUntil;

        public Long getNodeId() {
            return nodeId;
        }

        public void setNodeId(Long nodeId) {
            this.nodeId = nodeId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDeathDate() {
            return deathDate;
        }

        public void setDeathDate(String deathDate) {
            this.deathDate = deathDate;
        }

        public int getDaysUntil() {
            return daysUntil;
        }

        public void setDaysUntil(int daysUntil) {
            this.daysUntil = daysUntil;
        }

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
