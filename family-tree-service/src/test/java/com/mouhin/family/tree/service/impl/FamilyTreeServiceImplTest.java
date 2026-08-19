package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mouhin.family.tree.common.dto.TreeNodeVO;
import com.mouhin.family.tree.common.enums.ColorLabelEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.persistence.entity.FamilyNodeDO;
import com.mouhin.family.tree.persistence.entity.FamilyRelationDO;
import com.mouhin.family.tree.persistence.mapper.FamilyNodeMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyRelationMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 族谱树形结构服务核心逻辑单元测试。
 * 覆盖：普通父子结构、配偶挂载与互相回显、血亲配偶不重复挂载、
 * 同胞排次排序、已故节点保留与字段映射、离异丧偶改嫁引用、缓存命中与失效。
 *
 * @author Family-Tree
 * @date 2026-08-02
 */
@ExtendWith(MockitoExtension.class)
class FamilyTreeServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long FAMILY_ID = 100L;

    /** 关系ID发号器：断言配偶 VO 的 relationId 用 */
    private final AtomicLong relationIdSeq = new AtomicLong(100);

    @Mock
    private FamilyNodeMapper familyNodeMapper;

    @Mock
    private FamilyRelationMapper familyRelationMapper;

    private FamilyTreeServiceImpl familyTreeService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        familyTreeService = new FamilyTreeServiceImpl(familyNodeMapper, familyRelationMapper, new SimpleMeterRegistry());
    }

    // ========== 数据构造辅助 ==========

    private FamilyNodeDO node(long id, String name, int generation) {
        FamilyNodeDO n = new FamilyNodeDO();
        n.setId(id);
        n.setUserId(USER_ID);
        n.setFamilyId(FAMILY_ID);
        n.setName(name);
        n.setGender(1);
        n.setGeneration(generation);
        n.setColorLabel(ColorLabelEnum.DEFAULT.getCode());
        return n;
    }

    private FamilyRelationDO relation(long fromNodeId, long toNodeId, int type) {
        FamilyRelationDO rel = new FamilyRelationDO();
        rel.setId(relationIdSeq.incrementAndGet());
        rel.setUserId(USER_ID);
        rel.setFamilyId(FAMILY_ID);
        rel.setFromNodeId(fromNodeId);
        rel.setToNodeId(toNodeId);
        rel.setRelationType(type);
        return rel;
    }

    private FamilyRelationDO parentChild(long from, long to) {
        return relation(from, to, RelationTypeEnum.PARENT_CHILD.getCode());
    }

    private FamilyRelationDO spouse(long from, long to) {
        return relation(from, to, RelationTypeEnum.SPOUSE.getCode());
    }

    private void stubTree(List<FamilyNodeDO> nodes, List<FamilyRelationDO> relations) {
        when(familyNodeMapper.selectList(any(Wrapper.class))).thenReturn(nodes);
        when(familyRelationMapper.selectList(any(Wrapper.class))).thenReturn(relations);
    }

    /** 在树中递归查找指定 id 的节点（遍历 children 与 spouses，不含引用列表） */
    private TreeNodeVO findNode(List<TreeNodeVO> tree, long id) {
        for (TreeNodeVO vo : tree) {
            if (Objects.equals(vo.getId(), id)) {
                return vo;
            }
            TreeNodeVO inChildren = findNode(vo.getChildren(), id);
            if (inChildren != null) {
                return inChildren;
            }
            TreeNodeVO inSpouses = findNode(vo.getSpouses(), id);
            if (inSpouses != null) {
                return inSpouses;
            }
        }
        return null;
    }

    // ========== 测试用例 ==========

    @Test
    void emptyTreeReturnsEmptyList() {
        stubTree(new ArrayList<>(), new ArrayList<>());

        List<TreeNodeVO> tree = familyTreeService.getFullTree(FAMILY_ID);

        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    @Test
    void simpleParentChildTreeBuildsCorrectly() {
        FamilyNodeDO father = node(1L, "蒙父", 1);
        father.setDeathDate(LocalDate.of(2020, 5, 5));
        stubTree(List.of(father, node(2L, "蒙甲", 2), node(3L, "蒙乙", 2)),
                List.of(parentChild(1L, 2L), parentChild(1L, 3L)));

        List<TreeNodeVO> tree = familyTreeService.getFullTree(FAMILY_ID);

        assertEquals(1, tree.size(), "应只有一个根节点");
        TreeNodeVO root = tree.get(0);
        assertEquals(1L, root.getId());
        assertEquals(2, root.getChildren().size());
        assertEquals(2L, root.getChildren().get(0).getId());
        assertEquals(3L, root.getChildren().get(1).getId());
        assertEquals(2, root.getChildren().get(0).getGeneration());

        // 已故节点不参与过滤，仍保留在树中并携带卒日期（前端据此置灰渲染）
        assertEquals("2020-05-05", root.getDeathDate(), "已故节点的卒日期应完整映射到 VO");
    }

    @Test
    void satelliteSpouseAttachedWithMutualReference() {
        FamilyRelationDO marriage = spouse(1L, 2L);
        marriage.setMarriageDate(LocalDate.of(2000, 1, 1));
        stubTree(List.of(node(1L, "蒙夫", 1), node(2L, "李妻", 1)), List.of(marriage));

        List<TreeNodeVO> tree = familyTreeService.getFullTree(FAMILY_ID);

        // 配偶关系的 toNodeId 不作为独立根
        assertEquals(1, tree.size());
        TreeNodeVO husband = tree.get(0);
        assertEquals(1L, husband.getId());
        assertEquals(1, husband.getSpouses().size());

        TreeNodeVO wife = husband.getSpouses().get(0);
        assertEquals(2L, wife.getId());
        assertEquals(marriage.getId(), wife.getRelationId(), "配偶 VO 应携带关系ID供前端管理关系");
        assertEquals("2000-01-01", wife.getMarriageDate());

        // 配偶（妻子）的详情需能回显丈夫：其 spouses 列表含丈夫浅引用
        assertEquals(1, wife.getSpouses().size());
        assertEquals(1L, wife.getSpouses().get(0).getId());
    }

    @Test
    void bloodSpouseNotMountedTwice() {
        // 表兄妹结婚：祖父(1) → 父(2)/叔(3)；父→本人(4)，叔→表妹(5)；4 与 5 结为夫妻
        FamilyRelationDO cousinMarriage = spouse(4L, 5L);
        stubTree(
                List.of(node(1L, "祖父", 1), node(2L, "父", 2), node(3L, "叔", 2),
                        node(4L, "本人", 3), node(5L, "表妹", 3)),
                List.of(parentChild(1L, 2L), parentChild(1L, 3L),
                        parentChild(2L, 4L), parentChild(3L, 5L), cousinMarriage));

        List<TreeNodeVO> tree = familyTreeService.getFullTree(FAMILY_ID);

        assertEquals(1, tree.size(), "血亲配偶的 toNodeId 不应另立根");
        TreeNodeVO self = findNode(tree, 4L);
        assertNotNull(self);

        // 血亲配偶不嵌入为卫星节点，仅保留引用
        assertTrue(self.getSpouses().isEmpty(), "血亲配偶不得作为卫星节点挂载");
        assertEquals(1, self.getBloodSpouses().size());
        TreeNodeVO ref = self.getBloodSpouses().get(0);
        assertEquals(5L, ref.getId());
        assertEquals(cousinMarriage.getId(), ref.getRelationId());
        assertEquals("亲表兄妹", ref.getBloodRelationLabel(), "共享祖父母应标注为亲表兄妹");

        // 表妹仍保留在叔的原生分支，不被重复挂载
        TreeNodeVO uncle = findNode(tree, 3L);
        assertNotNull(uncle);
        assertEquals(1, uncle.getChildren().size());
        assertEquals(5L, uncle.getChildren().get(0).getId());

        // 引用是对称的：表妹分支也能看到本人的血亲配偶引用
        TreeNodeVO cousin = uncle.getChildren().get(0);
        assertEquals(1, cousin.getBloodSpouses().size());
        assertEquals(4L, cousin.getBloodSpouses().get(0).getId());
    }

    @Test
    void childrenSortedByBirthOrderWithNullsLast() {
        FamilyNodeDO c3 = node(21L, "老三", 2);
        c3.setBirthOrder(3);
        FamilyNodeDO c1 = node(22L, "老大", 2);
        c1.setBirthOrder(1);
        FamilyNodeDO c2 = node(23L, "老二", 2);
        c2.setBirthOrder(2);
        FamilyNodeDO cNull = node(24L, "未排次", 2);

        // 关系故意乱序插入，验证排序来自 birthOrder 而非插入顺序
        stubTree(List.of(node(20L, "父亲", 1), c3, c1, c2, cNull),
                List.of(parentChild(20L, 21L), parentChild(20L, 22L),
                        parentChild(20L, 23L), parentChild(20L, 24L)));

        List<TreeNodeVO> tree = familyTreeService.getFullTree(FAMILY_ID);

        TreeNodeVO father = tree.get(0);
        List<TreeNodeVO> children = father.getChildren();
        assertEquals(4, children.size());
        assertEquals(22L, children.get(0).getId(), "排次1应排最前");
        assertEquals(23L, children.get(1).getId());
        assertEquals(21L, children.get(2).getId());
        assertEquals(24L, children.get(3).getId(), "未设置排次的节点排在最后");
    }

    @Test
    void widowedSpouseRemarriedElsewhereKeptAsFormerReference() {
        // 蒙男(10) 之妻(11) 丧偶后改嫁 蒙男二(12)：
        // 关系50（10→11，丧偶），关系51（12→11，在婚）
        FamilyRelationDO firstMarriage = spouse(10L, 11L);
        firstMarriage.setWidowed(true);
        FamilyRelationDO secondMarriage = spouse(12L, 11L);
        stubTree(List.of(node(10L, "蒙男", 1), node(11L, "王氏", 1), node(12L, "蒙男二", 1)),
                List.of(firstMarriage, secondMarriage));

        List<TreeNodeVO> tree = familyTreeService.getFullTree(FAMILY_ID);

        assertEquals(2, tree.size(), "两位丈夫各为根，妻子不另立根");

        TreeNodeVO firstHusband = findNode(tree, 10L);
        assertNotNull(firstHusband);
        assertTrue(firstHusband.getSpouses().isEmpty(), "已改嫁的丧偶配偶不应再挂载为卫星节点");
        assertEquals(1, firstHusband.getFormerSpouses().size(), "前夫处应保留引用供详情展示");
        TreeNodeVO formerRef = firstHusband.getFormerSpouses().get(0);
        assertEquals(11L, formerRef.getId());
        assertEquals(firstMarriage.getId(), formerRef.getRelationId());
        assertEquals(Boolean.TRUE, formerRef.getWidowed());

        TreeNodeVO secondHusband = findNode(tree, 12L);
        assertNotNull(secondHusband);
        assertEquals(1, secondHusband.getSpouses().size(), "本人应归属当前配偶名下渲染");
        assertEquals(11L, secondHusband.getSpouses().get(0).getId());
    }

    @Test
    void getFullTreeUsesCacheUntilEvicted() {
        stubTree(List.of(node(1L, "蒙甲", 1)), new ArrayList<>());

        familyTreeService.getFullTree(FAMILY_ID);
        familyTreeService.getFullTree(FAMILY_ID);

        // 第二次读取应命中缓存，不再查库
        verify(familyNodeMapper, times(1)).selectList(any(Wrapper.class));
        verify(familyRelationMapper, times(1)).selectList(any(Wrapper.class));

        familyTreeService.evictFamilyTree(FAMILY_ID);
        familyTreeService.getFullTree(FAMILY_ID);

        // 失效后重新构建
        verify(familyNodeMapper, times(2)).selectList(any(Wrapper.class));
        verify(familyRelationMapper, times(2)).selectList(any(Wrapper.class));
    }
}
