package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.enums.FamilyStatusEnum;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import org.springframework.stereotype.Service;

/**
 * 家族状态守卫应用服务：为请求入口提供当前家族的治理状态。
 *
 * <p>状态由平台管理员维护，成员侧只读。家族不存在或历史数据未落状态时一律按正常处理，
 * 避免治理标记反过来打断正常家族的读写。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Service
public class FamilyStatusGuardApplicationService {

    private final FamilyRepository familyRepository;

    public FamilyStatusGuardApplicationService(FamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    /**
     * 读取家族状态
     *
     * @param familyId 家族ID，可为空
     * @return 家族状态，未知时返回 NORMAL
     */
    public FamilyStatusEnum statusOf(Long familyId) {
        if (familyId == null) {
            return FamilyStatusEnum.NORMAL;
        }
        Family family = familyRepository.findById(familyId);
        return family == null ? FamilyStatusEnum.NORMAL : family.statusOrDefault();
    }
}
