package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.infrastructure.converter.CrossFamilyLinkConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyNodeLinkMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨族链接仓储实现与转换器单元测试：覆盖 DO↔领域往返映射、
 * 节点对查询的规范化传参（小ID在前）与按节点/家族清理的谓词落点。
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@ExtendWith(MockitoExtension.class)
class CrossFamilyLinkRepositoryImplTest {

    private static final Long NODE_A = 3L;
    private static final Long NODE_B = 7L;

    @Mock
    private FamilyNodeLinkMapper mapper;

    private CrossFamilyLinkRepositoryImpl repository;

    @BeforeAll
    static void installLambdaCache() {
        // 纯单元测试不经过 MyBatis 容器，需手工登记表信息才能解析 Lambda 列名
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), FamilyNodeLinkDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new CrossFamilyLinkRepositoryImpl(mapper);
    }

    @Test
    void save_convertsAndBackfillsGeneratedId() {
        CrossFamilyLink entity = domain(null, 100L, 7L, 200L, 30L);
        doAnswer(invocation -> {
            ((FamilyNodeLinkDO) invocation.getArgument(0)).setId(55L);
            return 1;
        }).when(mapper).insert(any(FamilyNodeLinkDO.class));

        repository.save(entity);

        assertEquals(Long.valueOf(55L), entity.getId());
        ArgumentCaptor<FamilyNodeLinkDO> captor = ArgumentCaptor.forClass(FamilyNodeLinkDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(Long.valueOf(7L), captor.getValue().getSrcNodeId());
        assertEquals(Long.valueOf(200L), captor.getValue().getDstFamilyId());
        assertEquals(NodeLinkTypeEnum.BLOOD.getCode(), captor.getValue().getLinkType());
    }

    @Test
    void findById_mapsDomainObject() {
        when(mapper.selectById(9L)).thenReturn(doRow(9L, 100L, 3L, 200L, 7L));

        CrossFamilyLink link = repository.findById(9L);

        assertEquals(Long.valueOf(9L), link.getId());
        assertEquals(Long.valueOf(200L), link.getDstFamilyId());
        assertEquals(NodeLinkTypeEnum.BLOOD.getCode(), link.getLinkType());
        assertEquals(NodeLinkSourceEnum.ACCOUNT.getCode(), link.getSource());
    }

    @Test
    void findById_missing_returnsNull() {
        when(mapper.selectById(9L)).thenReturn(null);

        assertNull(repository.findById(9L));
    }

    @Test
    void findByNodeId_queriesBothEndpointColumns() {
        when(mapper.selectList(any())).thenReturn(List.of(doRow(9L, 100L, 3L, 200L, 7L)));

        List<CrossFamilyLink> result = repository.findByNodeId(NODE_A);

        assertEquals(1, result.size());
        ArgumentCaptor<LambdaQueryWrapper<FamilyNodeLinkDO>> captor = wrapperCaptor();
        verify(mapper).selectList(captor.capture());
        LambdaQueryWrapper<FamilyNodeLinkDO> wrapper = captor.getValue();
        // 先渲染 SQL 触发 MP 惰性填参，再断言两端列与参数值
        String sql = wrapper.getTargetSql();
        assertTrue(sql.contains("src_node_id"));
        assertTrue(sql.contains("dst_node_id"));
        assertTrue(sql.contains("OR"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(NODE_A));
    }

    @Test
    void findByNodePair_normalizesSmallerIdToSrcColumn() {
        when(mapper.selectOne(any())).thenReturn(doRow(9L, 100L, NODE_A, 200L, NODE_B));

        // 传参顺序颠倒也必须命中同一行：src 列查小ID
        CrossFamilyLink link = repository.findByNodePair(NODE_B, NODE_A);

        assertEquals(Long.valueOf(9L), link.getId());
        ArgumentCaptor<LambdaQueryWrapper<FamilyNodeLinkDO>> captor = wrapperCaptor();
        verify(mapper).selectOne(captor.capture());
        LambdaQueryWrapper<FamilyNodeLinkDO> wrapper = captor.getValue();
        String segment = wrapper.getSqlSegment();
        assertTrue(segment.indexOf("src_node_id") < segment.indexOf("dst_node_id"));
        // paramNameValuePairs 为 HashMap，按渲染占位符出现顺序取回实参值
        List<Object> orderedParams = new ArrayList<>(2);
        Matcher matcher = Pattern.compile("MPGENVAL(\\d+)").matcher(segment);
        while (matcher.find()) {
            orderedParams.add(wrapper.getParamNameValuePairs().get("MPGENVAL" + matcher.group(1)));
        }
        assertEquals(List.of(NODE_A, NODE_B), orderedParams);
    }

    @Test
    void findByNodePair_nullArgs_skipQuery() {
        assertNull(repository.findByNodePair(null, NODE_B));
        assertNull(repository.findByNodePair(NODE_A, null));
    }

    @Test
    void removeByNodeId_deletesEitherEndpointMatches() {
        repository.removeByNodeId(NODE_A);

        ArgumentCaptor<LambdaQueryWrapper<FamilyNodeLinkDO>> captor = wrapperCaptor();
        verify(mapper).delete(captor.capture());
        assertTrue(captor.getValue().getTargetSql().contains("src_node_id"));
        assertTrue(captor.getValue().getTargetSql().contains("dst_node_id"));
    }

    @Test
    void converter_roundTrip_keepsAllFields() {
        CrossFamilyLink source = domain(9L, 100L, 3L, 200L, 7L);
        source.setConfirmedBy(1L);
        LocalDateTime now = LocalDateTime.of(2026, 11, 20, 10, 0);
        source.setCreateTime(now);
        source.setUpdateTime(now);

        CrossFamilyLink restored = CrossFamilyLinkConverter.toDomain(CrossFamilyLinkConverter.toDO(source));

        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getSrcFamilyId(), restored.getSrcFamilyId());
        assertEquals(source.getSrcNodeId(), restored.getSrcNodeId());
        assertEquals(source.getDstFamilyId(), restored.getDstFamilyId());
        assertEquals(source.getDstNodeId(), restored.getDstNodeId());
        assertEquals(source.getLinkType(), restored.getLinkType());
        assertEquals(source.getSource(), restored.getSource());
        assertEquals(source.getConfirmedBy(), restored.getConfirmedBy());
        assertEquals(source.getCreateTime(), restored.getCreateTime());
        assertEquals(source.getUpdateTime(), restored.getUpdateTime());
        assertTrue(CrossFamilyLinkConverter.toDomainList(List.of(doRow(1L, 100L, 3L, 200L, 7L))).size() == 1);
    }

    // ========== 辅助方法 ==========

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaQueryWrapper<FamilyNodeLinkDO>> wrapperCaptor() {
        return (ArgumentCaptor<LambdaQueryWrapper<FamilyNodeLinkDO>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Object.class);
    }

    private CrossFamilyLink domain(Long id, Long srcFamilyId, Long srcNodeId, Long dstFamilyId, Long dstNodeId) {
        CrossFamilyLink link = new CrossFamilyLink();
        link.setId(id);
        link.setSrcFamilyId(srcFamilyId);
        link.setSrcNodeId(srcNodeId);
        link.setDstFamilyId(dstFamilyId);
        link.setDstNodeId(dstNodeId);
        link.setLinkType(NodeLinkTypeEnum.BLOOD.getCode());
        link.setSource(NodeLinkSourceEnum.ACCOUNT.getCode());
        return link;
    }

    private FamilyNodeLinkDO doRow(Long id, Long srcFamilyId, Long srcNodeId, Long dstFamilyId, Long dstNodeId) {
        FamilyNodeLinkDO row = new FamilyNodeLinkDO();
        row.setId(id);
        row.setSrcFamilyId(srcFamilyId);
        row.setSrcNodeId(srcNodeId);
        row.setDstFamilyId(dstFamilyId);
        row.setDstNodeId(dstNodeId);
        row.setLinkType(NodeLinkTypeEnum.BLOOD.getCode());
        row.setSource(NodeLinkSourceEnum.ACCOUNT.getCode());
        return row;
    }
}
