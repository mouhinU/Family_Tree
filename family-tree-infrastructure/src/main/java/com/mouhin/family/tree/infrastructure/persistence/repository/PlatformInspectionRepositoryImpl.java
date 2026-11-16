package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.enums.InspectionCheckEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.InspectionFinding;
import com.mouhin.family.tree.domain.repository.PlatformInspectionRepository;
import com.mouhin.family.tree.infrastructure.converter.InspectionConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.InspectionSampleDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.PlatformInspectionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 平台数据巡检仓储实现：把巡检项映射到对应的只读探测语句。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Repository
public class PlatformInspectionRepositoryImpl implements PlatformInspectionRepository {

    /**
     * 自动账号标记列的"真"值（MySQL 为 tinyint 1，H2 为 boolean，统一以 boolean 传参）
     */
    private static final boolean AUTO_ACCOUNT_FLAG = true;

    private final PlatformInspectionMapper mapper;

    public PlatformInspectionRepositoryImpl(PlatformInspectionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public InspectionFinding findFinding(InspectionCheckEnum check, int sampleLimit) {
        if (check == null) {
            throw new BusinessException("缺少巡检项");
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
            case AUTO_ACCOUNT_PLACEHOLDER -> mapper.listAutoAccountPlaceholder(AUTO_ACCOUNT_FLAG, sampleLimit);
            default -> throw new BusinessException("未实现的巡检项：" + check.getCode());
        };
        return InspectionConverter.toFinding(rows);
    }
}
