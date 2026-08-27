package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.domain.entity.FamilyNode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 忌日计算领域服务。
 * <p>
 * 根据已故节点的去世日期，计算未来 N 天内即将到来的忌日。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class DeathAnniversaryDomainService {

    private static final Logger logger = LoggerFactory.getLogger(DeathAnniversaryDomainService.class);

    /**
     * 计算未来 N 天内即将到来的忌日列表
     *
     * @param deceasedNodes 已故节点列表（deathDate 不为空）
     * @param days          提前天数（含今天）
     * @return 忌日信息列表，按剩余天数升序排列
     */
    public List<AnniversaryInfo> calculateUpcoming(List<FamilyNode> deceasedNodes,
                                                   int days) {
        LocalDate today = LocalDate.now();
        List<AnniversaryInfo> result = new ArrayList<>();

        for (FamilyNode node : deceasedNodes) {
            try {
                LocalDate deathDate = node.getDeathDate();
                if (deathDate == null) {
                    continue;
                }

                // 计算今年忌日
                LocalDate thisYearAnniversary =
                        deathDate.withYear(today.getYear());
                if (thisYearAnniversary.isBefore(today)) {
                    // 今年的忌日已过，计算明年的
                    thisYearAnniversary =
                            deathDate.withYear(today.getYear() + 1);
                }

                long daysUntil = ChronoUnit.DAYS.between(today,
                        thisYearAnniversary);

                if (daysUntil >= 0 && daysUntil <= days) {
                    result.add(new AnniversaryInfo(
                            node.getId(),
                            node.getName(),
                            deathDate,
                            thisYearAnniversary,
                            daysUntil));
                }
            } catch (Exception e) {
                logger.warn(
                        "Failed to calculate death anniversary for node {}: {}",
                        node.getId(), e.getMessage());
            }
        }

        result.sort(Comparator.comparingLong(AnniversaryInfo::getDaysUntil));
        return result;
    }

    /**
     * 忌日信息结果对象
     */
    @Getter
    public static class AnniversaryInfo {

        private final Long nodeId;
        private final String nodeName;
        private final LocalDate deathDate;
        private final LocalDate anniversaryDate;
        private final long daysUntil;

        /**
         * 构造忌日信息
         *
         * @param nodeId          节点ID
         * @param nodeName        节点名称
         * @param deathDate       去世日期
         * @param anniversaryDate 今年/明年的忌日日期
         * @param daysUntil       距今天数
         */
        public AnniversaryInfo(Long nodeId, String nodeName,
                               LocalDate deathDate,
                               LocalDate anniversaryDate,
                               long daysUntil) {
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.deathDate = deathDate;
            this.anniversaryDate = anniversaryDate;
            this.daysUntil = daysUntil;
        }
    }
}
