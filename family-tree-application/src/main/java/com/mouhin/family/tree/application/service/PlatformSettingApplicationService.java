package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.PlatformSettingSaveDTO;
import com.mouhin.family.tree.common.dto.PlatformSettingVO;
import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.PlatformSetting;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.repository.PlatformSettingRepository;
import com.mouhin.family.tree.domain.service.PlatformSettingDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台级配置应用服务：平台管理页读写全站配置，并向业务入口提供生效值与断言。
 *
 * <p>platform_setting 只存被显式覆盖的值；提交值与代码默认值一致时删除覆盖行，
 * 使配置回到"未覆盖"状态，避免默认值随代码演进后遗留陈旧覆盖。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Service
public class PlatformSettingApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(PlatformSettingApplicationService.class);

    /**
     * 平台操作日志类型：平台全局配置更新
     */
    private static final String OPERATION_TYPE_GLOBAL_SETTING_UPDATE = "PLATFORM_GLOBAL_SETTING_UPDATE";

    /**
     * 平台操作日志的对象类型：平台配置
     */
    private static final String TARGET_TYPE_PLATFORM_SETTING = "platform_setting";

    /**
     * 平台独有开关关闭时的提示后缀
     */
    private static final String DISABLED_SUFFIX = "，请联系平台管理员";

    private final PlatformSettingRepository platformSettingRepository;
    private final PlatformSettingDomainService settingDomainService;
    private final FamilyRepository familyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PlatformSettingApplicationService(PlatformSettingRepository platformSettingRepository,
                                             PlatformSettingDomainService settingDomainService,
                                             FamilyRepository familyRepository,
                                             ApplicationEventPublisher eventPublisher) {
        this.platformSettingRepository = platformSettingRepository;
        this.settingDomainService = settingDomainService;
        this.familyRepository = familyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 查询全部已登记平台配置项的生效值（含 schema 描述，供平台管理页渲染）
     *
     * @return 配置项视图列表，按枚举声明顺序排列
     */
    public List<PlatformSettingVO> listPlatformSettings() {
        Map<String, String> overrides = loadOverrides();
        Map<String, String> effective = settingDomainService.mergeWithDefaults(overrides);
        List<PlatformSettingKeyEnum> keys = PlatformSettingKeyEnum.listAll();
        List<PlatformSettingVO> voList = new ArrayList<>(keys.size());
        for (PlatformSettingKeyEnum key : keys) {
            PlatformSettingVO vo = new PlatformSettingVO();
            vo.setKey(key.getCode());
            vo.setLabel(key.getLabel());
            vo.setValueType(key.getValueType().getCode());
            vo.setValue(effective.get(key.getCode()));
            vo.setDefaultValue(key.getDefaultValue());
            vo.setOverridden(overrides.containsKey(key.getCode()));
            vo.setFamilyScoped(key.isFamilyScoped());
            vo.setMinValue(key.getMinValue());
            vo.setMaxValue(key.getMaxValue());
            vo.setMaxLength(key.getMaxLength());
            vo.setDescription(key.getDescription());
            voList.add(vo);
        }
        return voList;
    }

    /**
     * 保存平台级配置覆盖值，未提交的配置项保持原状。
     *
     * <p>取值与代码默认值一致时不写覆盖行，并删除已有覆盖行，等同于恢复默认。</p>
     *
     * @param dto          配置提交对象
     * @param operatorId   操作人用户ID
     * @param operatorName 操作人名称
     * @param ipAddress    操作来源IP
     */
    @Transactional(rollbackFor = Exception.class)
    public void savePlatformSettings(PlatformSettingSaveDTO dto, Long operatorId,
                                     String operatorName, String ipAddress) {
        List<PlatformSetting> validated = settingDomainService.toValidatedEntities(
                dto == null ? null : dto.getSettings());
        int written = 0;
        int resetToDefault = 0;
        for (PlatformSetting setting : validated) {
            PlatformSetting existing = platformSettingRepository.findByKey(setting.getSettingKey());
            if (settingDomainService.isDefaultValue(setting)) {
                if (existing != null) {
                    platformSettingRepository.removeByKey(setting.getSettingKey());
                    resetToDefault++;
                }
                continue;
            }
            if (existing == null) {
                platformSettingRepository.save(setting);
            } else {
                existing.setSettingValue(setting.getSettingValue());
                platformSettingRepository.update(existing);
            }
            written++;
        }
        logger.info("Platform global settings saved, written: {}, resetToDefault: {}", written, resetToDefault);
        eventPublisher.publishEvent(OperationPerformedEvent.of(operatorId, operatorName,
                OPERATION_TYPE_GLOBAL_SETTING_UPDATE,
                "更新平台全局配置" + validated.size() + "项（覆盖" + written + "项，恢复默认" + resetToDefault + "项）",
                TARGET_TYPE_PLATFORM_SETTING, null, null, ipAddress));
    }

    /**
     * 读取平台层生效配置（覆盖值合并代码默认值）
     *
     * @return 配置键编码 → 生效值
     */
    public Map<String, String> effectiveSettings() {
        return settingDomainService.mergeWithDefaults(loadOverrides());
    }

    /**
     * 读取家族配置三级生效链中的平台层取值
     *
     * @return 家族级配置键编码 → 平台层生效值
     */
    public Map<String, String> familyScopedDefaults() {
        return settingDomainService.familyScopedDefaults(effectiveSettings());
    }

    /**
     * 断言平台独有开关处于开启状态，关闭时拒绝访问
     *
     * @param key 平台独有开关键
     */
    public void assertEnabled(PlatformSettingKeyEnum key) {
        if (!settingDomainService.isEnabled(effectiveSettings(), key)) {
            throw new BusinessException(settingDomainService.disabledMessage(key) + DISABLED_SUFFIX);
        }
    }

    /**
     * 断言全平台家族总数尚未达到配置的上限
     */
    public void assertFamilyQuotaAvailable() {
        int maxFamilyCount = settingDomainService.getInt(effectiveSettings(),
                PlatformSettingKeyEnum.MAX_FAMILY_COUNT);
        long currentCount = familyRepository.countAll();
        if (currentCount >= maxFamilyCount) {
            throw new BusinessException("平台家族总数已达上限" + maxFamilyCount + "，暂时无法新建家族");
        }
    }

    private Map<String, String> loadOverrides() {
        List<PlatformSetting> settings = platformSettingRepository.findAll();
        Map<String, String> overrides = new LinkedHashMap<>(settings.size() * 2);
        for (PlatformSetting setting : settings) {
            overrides.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return overrides;
    }
}
