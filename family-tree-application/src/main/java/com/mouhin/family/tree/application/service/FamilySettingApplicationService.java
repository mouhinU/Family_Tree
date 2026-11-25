package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilySetting;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilySettingRepository;
import com.mouhin.family.tree.domain.service.FamilySettingDomainService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 家族级配置读取应用服务：供业务入口判定功能开关与配额。
 *
 * <p>写入只发生在平台管理页，业务侧仅读取。开关关闭时抛出业务异常，
 * 由全局异常处理器转化为统一错误返回，前端无需额外适配。</p>
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@Service
public class FamilySettingApplicationService {

    /**
     * 功能被关闭时的统一提示
     */
    private static final String DISABLED_MESSAGE_SUFFIX = "已被平台管理员关闭";

    private final FamilySettingRepository familySettingRepository;
    private final FamilySettingDomainService settingDomainService;
    private final FamilyNodeRepository familyNodeRepository;
    private final PlatformSettingApplicationService platformSettingService;

    public FamilySettingApplicationService(FamilySettingRepository familySettingRepository,
                                           FamilySettingDomainService settingDomainService,
                                           FamilyNodeRepository familyNodeRepository,
                                           PlatformSettingApplicationService platformSettingService) {
        this.familySettingRepository = familySettingRepository;
        this.settingDomainService = settingDomainService;
        this.familyNodeRepository = familyNodeRepository;
        this.platformSettingService = platformSettingService;
    }

    /**
     * 读取指定家族的生效配置（家族覆盖 → 平台覆盖 → 代码默认）
     *
     * @param familyId 家族ID
     * @return 配置键编码 → 生效值
     */
    public Map<String, String> effectiveSettings(Long familyId) {
        List<FamilySetting> overrides = familySettingRepository.findByFamilyId(familyId);
        Map<String, String> overrideMap = new LinkedHashMap<>(overrides.size() * 2);
        for (FamilySetting setting : overrides) {
            overrideMap.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return settingDomainService.mergeWithDefaults(overrideMap, platformSettingService.familyScopedDefaults());
    }

    /**
     * 断言指定开关型配置处于开启状态，关闭时拒绝访问
     *
     * @param familyId 家族ID
     * @param key      开关型配置键
     */
    public void assertEnabled(Long familyId, FamilySettingKeyEnum key) {
        Map<String, String> effective = effectiveSettings(familyId);
        if (!settingDomainService.isEnabled(effective, key)) {
            throw new BusinessException(key.getLabel() + DISABLED_MESSAGE_SUFFIX);
        }
    }

    /**
     * 判断指定家族的开关型配置生效值是否为开启（不抛异常，供入口显隐等分支判定）
     *
     * @param familyId 家族ID
     * @param key      开关型配置键
     * @return 开启返回 true
     */
    public boolean isEnabled(Long familyId, FamilySettingKeyEnum key) {
        return settingDomainService.isEnabled(effectiveSettings(familyId), key);
    }

    /**
     * 断言家族节点配额按预计总数尚未超限：覆盖式导入按导入量、追加式按存量加导入量口径调用，
     * 与 {@link #assertNodeQuotaAvailable(Long)} 的单节点新增口径互补
     *
     * @param familyId      家族ID
     * @param projectedTotal 预计节点总数
     */
    public void assertNodeQuotaWithin(Long familyId, long projectedTotal) {
        int maxNodeCount = settingDomainService.getInt(effectiveSettings(familyId),
                FamilySettingKeyEnum.MAX_NODE_COUNT);
        if (projectedTotal > maxNodeCount) {
            throw new BusinessException("族谱节点总数将超过上限" + maxNodeCount + "，请联系平台管理员调整");
        }
    }

    /**
     * 断言家族节点总数尚未达到平台配置的上限
     *
     * @param familyId 家族ID
     */
    public void assertNodeQuotaAvailable(Long familyId) {
        int maxNodeCount = settingDomainService.getInt(effectiveSettings(familyId),
                FamilySettingKeyEnum.MAX_NODE_COUNT);
        long currentCount = familyNodeRepository.countByFamilyId(familyId);
        if (currentCount >= maxNodeCount) {
            throw new BusinessException("族谱节点数量已达上限" + maxNodeCount + "，请联系平台管理员调整");
        }
    }
}
