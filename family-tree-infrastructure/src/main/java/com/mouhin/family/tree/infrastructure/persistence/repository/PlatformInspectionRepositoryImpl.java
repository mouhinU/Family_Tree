package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.enums.GenderEnum;
import com.mouhin.family.tree.common.enums.InspectionCheckEnum;
import com.mouhin.family.tree.common.enums.PlatformSettingKeyEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.util.LunarSolarUtil;
import com.mouhin.family.tree.domain.entity.InspectionFinding;
import com.mouhin.family.tree.domain.entity.InspectionQuota;
import com.mouhin.family.tree.domain.repository.PlatformInspectionRepository;
import com.mouhin.family.tree.infrastructure.converter.InspectionConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.InspectionSampleDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.NodeLunarRowDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.SettingRowDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.PlatformInspectionMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 平台数据巡检仓储实现：把巡检项映射到对应的只读探测语句。
 *
 * <p>农历换算与配置键白名单两项无法用双方言 SQL 表达，改为取回小表全量行后在
 * Java 侧逐行比对；其余巡检项由 Mapper 探测语句直接返回异常样本。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Repository
public class PlatformInspectionRepositoryImpl implements PlatformInspectionRepository {

    /**
     * 布尔标记列的"真"值（MySQL 为 tinyint 1，H2 为 boolean，统一以 boolean 传参）
     */
    private static final boolean BOOLEAN_TRUE = true;

    /**
     * 亲子环递归探测的深度上限，防御脏数据导致的无限展开
     */
    private static final int PARENT_CYCLE_MAX_DEPTH = 30;

    /**
     * 配置行作用域判别：家族覆盖层
     */
    private static final String SETTING_SCOPE_FAMILY = "FAMILY";

    private final PlatformInspectionMapper mapper;

    public PlatformInspectionRepositoryImpl(PlatformInspectionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public InspectionFinding findFinding(InspectionCheckEnum check, int sampleLimit) {
        return findFinding(check, sampleLimit, null);
    }

    @Override
    public InspectionFinding findFinding(InspectionCheckEnum check, int sampleLimit, InspectionQuota quota) {
        if (check == null) {
            throw new BusinessException("缺少巡检项");
        }
        if (check == InspectionCheckEnum.LUNAR_SOLAR_MISMATCH) {
            return findLunarSolarMismatch(sampleLimit);
        }
        if (check == InspectionCheckEnum.SETTING_UNKNOWN_KEY) {
            return findUnknownSettingKey(sampleLimit);
        }
        List<InspectionSampleDO> rows = switch (check) {
            case ACCOUNT_FAMILY_MISMATCH -> mapper.listAccountFamilyMismatch(sampleLimit);
            case FAMILY_WITHOUT_OWNER -> mapper.listFamilyWithoutOwner(FamilyMemberRoleEnum.OWNER.getCode(), sampleLimit);
            case MEMBER_ORPHAN_FAMILY -> mapper.listMemberOrphanFamily(sampleLimit);
            case MEMBER_ORPHAN_USER -> mapper.listMemberOrphanUser(sampleLimit);
            case NODE_WITHOUT_FAMILY -> mapper.listNodeWithoutFamily(sampleLimit);
            case RELATION_ORPHAN_NODE -> mapper.listRelationOrphanNode(sampleLimit);
            case NODE_BINDING_MISMATCH -> mapper.listNodeBindingMismatch(sampleLimit);
            case RELATION_SELF_LOOP -> mapper.listRelationSelfLoop(sampleLimit);
            case GENERATION_INVERTED -> mapper.listGenerationInverted(RelationTypeEnum.PARENT_CHILD.getCode(), sampleLimit);
            case SPOUSE_CROSS_FAMILY -> mapper.listSpouseCrossFamily(RelationTypeEnum.SPOUSE.getCode(), sampleLimit);
            case DEATH_BEFORE_BIRTH -> mapper.listDeathBeforeBirth(sampleLimit);
            case AUTO_ACCOUNT_PLACEHOLDER -> mapper.listAutoAccountPlaceholder(BOOLEAN_TRUE, sampleLimit);
            case LINK_ORPHAN -> mapper.listLinkOrphan(sampleLimit);
            case RELATION_DUPLICATE_ACROSS_CREATOR -> mapper.listRelationDuplicateAcrossCreator(sampleLimit);
            case PARENT_CYCLE -> mapper.listParentCycle(RelationTypeEnum.PARENT_CHILD.getCode(),
                    PARENT_CYCLE_MAX_DEPTH, sampleLimit);
            case RELATION_FAMILY_MISMATCH -> mapper.listRelationFamilyMismatch(sampleLimit);
            case NODE_FIELD_INVALID -> mapper.listNodeFieldInvalid(GenderEnum.UNKNOWN.getCode(),
                    GenderEnum.FEMALE.getCode(), sampleLimit);
            case QUOTA_EXCEEDED -> {
                if (quota == null) {
                    throw new BusinessException("配额巡检缺少平台生效配额上下文");
                }
                yield mapper.listQuotaExceeded(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(),
                        quota.getMaxNodeCount(), quota.getMaxFamilyMembers(), sampleLimit);
            }
            case AUTO_ACCOUNT_ORPHAN -> mapper.listAutoAccountOrphan(BOOLEAN_TRUE, sampleLimit);
            case RELATION_FLAG_DATE_MISMATCH -> mapper.listRelationFlagDateMismatch(RelationTypeEnum.SPOUSE.getCode(),
                    BOOLEAN_TRUE, sampleLimit);
            case ATTACHED_ORPHAN -> mapper.listAttachedOrphan(sampleLimit);
            case LUNAR_SOLAR_MISMATCH, SETTING_UNKNOWN_KEY -> throw new BusinessException("巡检项分派异常：" + check.getCode());
        };
        return InspectionConverter.toFinding(rows);
    }

    /**
     * 农历一致性探测：阳历为唯一真值，逐行按阳历重算农历串并比对登记值
     */
    private InspectionFinding findLunarSolarMismatch(int sampleLimit) {
        List<InspectionSampleDO> mismatched = new ArrayList<>();
        for (NodeLunarRowDO row : mapper.listBirthDateNodes()) {
            String expected;
            try {
                expected = LunarSolarUtil.solarToLunar(row.getBirthDate());
            } catch (RuntimeException e) {
                continue;
            }
            String registered = row.getLunarBirthDate().trim();
            if (!Objects.equals(expected, registered)) {
                InspectionSampleDO sample = newSample(row.getId(),
                        row.getName() == null || row.getName().isEmpty() ? "node#" + row.getId() : row.getName(),
                        "登记农历 " + registered + "，按阳历 " + row.getBirthDate() + " 应为 " + expected);
                mismatched.add(sample);
            }
        }
        return toJavaFinding(mismatched, sampleLimit);
    }

    /**
     * 未知配置键探测：平台与家族两级配置行按枚举键白名单过滤
     */
    private InspectionFinding findUnknownSettingKey(int sampleLimit) {
        Set<String> knownKeys = new HashSet<>(32);
        for (PlatformSettingKeyEnum key : PlatformSettingKeyEnum.values()) {
            knownKeys.add(key.getCode());
        }
        for (FamilySettingKeyEnum key : FamilySettingKeyEnum.values()) {
            knownKeys.add(key.getCode());
        }
        List<InspectionSampleDO> unknown = new ArrayList<>();
        for (SettingRowDO row : mapper.listSettingRows()) {
            if (knownKeys.contains(row.getSettingKey())) {
                continue;
            }
            String subject = SETTING_SCOPE_FAMILY.equals(row.getScope())
                    ? "family#" + row.getFamilyId() : "平台配置";
            unknown.add(newSample(row.getId(), subject, "未知配置键 " + row.getSettingKey()));
        }
        return toJavaFinding(unknown, sampleLimit);
    }

    /**
     * Java 侧探测结果统一转为巡检结论：总数取命中行数，样本按上限截断
     */
    private InspectionFinding toJavaFinding(List<InspectionSampleDO> rows, int sampleLimit) {
        if (rows.isEmpty()) {
            return InspectionFinding.empty();
        }
        long total = rows.size();
        for (InspectionSampleDO row : rows) {
            row.setTotal(total);
        }
        List<InspectionSampleDO> samples = rows.size() > sampleLimit ? rows.subList(0, sampleLimit) : rows;
        return InspectionConverter.toFinding(samples);
    }

    /**
     * 构造一条 Java 侧探测的异常样本
     */
    private InspectionSampleDO newSample(Long refId, String subject, String detail) {
        InspectionSampleDO sample = new InspectionSampleDO();
        sample.setRefId(refId);
        sample.setSubject(subject);
        sample.setDetail(detail);
        return sample;
    }
}
