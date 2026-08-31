package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.BirthdayReminderVO;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.service.BirthdayReminderDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 生日提醒应用服务
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Service
public class BirthdayReminderApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(BirthdayReminderApplicationService.class);

    private final FamilyNodeRepository familyNodeRepository;
    private final BirthdayReminderDomainService birthdayReminderDomainService;

    public BirthdayReminderApplicationService(FamilyNodeRepository familyNodeRepository,
                                              BirthdayReminderDomainService birthdayReminderDomainService) {
        this.familyNodeRepository = familyNodeRepository;
        this.birthdayReminderDomainService = birthdayReminderDomainService;
    }

    /**
     * 获取未来 N 天内过生日的在世成员列表
     *
     * @param familyId 家族ID
     * @param days     提前天数（含今天）
     * @return 生日提醒列表
     */
    public List<BirthdayReminderVO> getUpcoming(Long familyId, int days) {
        List<FamilyNode> nodes = familyNodeRepository.findByFamilyId(familyId);
        List<BirthdayReminderDomainService.BirthdayInfo> infos =
                birthdayReminderDomainService.calculateUpcoming(nodes, days);

        logger.debug("家族 {} 未来 {} 天内生日成员数: {}", familyId, days, infos.size());
        return infos.stream().map(this::toVO).toList();
    }

    private BirthdayReminderVO toVO(BirthdayReminderDomainService.BirthdayInfo info) {
        BirthdayReminderVO vo = new BirthdayReminderVO();
        vo.setNodeId(info.getNodeId());
        vo.setName(info.getNodeName());
        vo.setBirthDate(info.getBirthDate() != null ? info.getBirthDate().toString() : null);
        vo.setAge(info.getAge());
        vo.setDaysUntil((int) info.getDaysUntil());
        return vo;
    }
}
