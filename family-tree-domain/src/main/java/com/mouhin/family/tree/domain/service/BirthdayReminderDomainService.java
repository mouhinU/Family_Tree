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
 * 生日提醒计算领域服务。
 * <p>
 * 根据节点出生日期，计算未来 N 天内即将到来的生日。
 * 与忌日提醒不同，生日仅统计健在成员。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Service
public class BirthdayReminderDomainService {

    private static final Logger logger = LoggerFactory.getLogger(BirthdayReminderDomainService.class);

    /**
     * 计算未来 N 天内即将到来的生日列表
     *
     * @param livingNodes 健在节点列表（deathDate 为空）
     * @param days        提前天数（含今天）
     * @return 生日信息列表，按剩余天数升序排列
     */
    public List<BirthdayInfo> calculateUpcoming(List<FamilyNode> livingNodes, int days) {
        LocalDate today = LocalDate.now();
        List<BirthdayInfo> result = new ArrayList<>();

        for (FamilyNode node : livingNodes) {
            try {
                LocalDate birthDate = node.getBirthDate();
                if (birthDate == null || node.isDeceased()) {
                    continue;
                }

                // 计算今年生日
                LocalDate thisYearBirthday = birthDate.withYear(today.getYear());
                if (thisYearBirthday.isBefore(today)) {
                    // 今年生日已过，计算明年的
                    thisYearBirthday = birthDate.withYear(today.getYear() + 1);
                }

                long daysUntil = ChronoUnit.DAYS.between(today, thisYearBirthday);
                if (daysUntil >= 0 && daysUntil <= days) {
                    int age = thisYearBirthday.getYear() - birthDate.getYear();
                    result.add(new BirthdayInfo(
                            node.getId(),
                            node.getName(),
                            birthDate,
                            age,
                            daysUntil));
                }
            } catch (Exception e) {
                // 2月29日等特殊日期 withYear 会抛异常，跳过该节点
                logger.warn("Failed to calculate birthday for node {}: {}", node.getId(), e.getMessage());
            }
        }

        result.sort(Comparator.comparingLong(BirthdayInfo::getDaysUntil));
        return result;
    }

    /**
     * 生日信息结果对象
     */
    @Getter
    public static class BirthdayInfo {

        private final Long nodeId;
        private final String nodeName;
        private final LocalDate birthDate;
        private final int age;
        private final long daysUntil;

        /**
         * 构造生日信息
         *
         * @param nodeId    节点ID
         * @param nodeName  节点名称
         * @param birthDate 出生日期
         * @param age       即将到来的岁数
         * @param daysUntil 距今天数
         */
        public BirthdayInfo(Long nodeId, String nodeName, LocalDate birthDate, int age, long daysUntil) {
            this.nodeId = nodeId;
            this.nodeName = nodeName;
            this.birthDate = birthDate;
            this.age = age;
            this.daysUntil = daysUntil;
        }
    }
}
