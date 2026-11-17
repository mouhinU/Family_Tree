package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.NodeHistory;
import com.mouhin.family.tree.domain.repository.HistoryRepository;
import com.mouhin.family.tree.domain.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 版本控制领域服务单元测试。
 * <p>
 * 覆盖回滚依赖的两条口径：历史 JSON 与节点对象的双向可解析（含 deceased 派生字段容忍），
 * 以及回滚历史的版本号、操作类型与变更摘要。
 *
 * @author Family-Tree
 * @date 2026-11-17
 */
@ExtendWith(MockitoExtension.class)
class VersionControlDomainServiceTest {

    private static final Long NODE_ID = 7L;
    private static final Long FAMILY_ID = 100L;
    private static final Long USER_ID = 3L;
    private static final String USERNAME = "蒙斌";
    private static final String IP = "127.0.0.1";

    @Mock
    private HistoryRepository historyRepository;

    @Mock
    private SnapshotRepository snapshotRepository;

    private VersionControlDomainService service;

    @BeforeEach
    void setUp() {
        service = new VersionControlDomainService(historyRepository, snapshotRepository);
    }

    @Test
    void parseNodeVersionReadsBackRecordedNodeData() {
        FamilyNode node = fullNode();
        when(historyRepository.getNextNodeVersion(NODE_ID)).thenReturn(1);

        service.recordNodeCreate(node, USER_ID, USERNAME, IP);

        ArgumentCaptor<NodeHistory> saved = ArgumentCaptor.forClass(NodeHistory.class);
        verify(historyRepository).saveNodeHistory(saved.capture());
        String afterData = saved.getValue().getAfterData();
        // 序列化带 isDeceased() 派生的 deceased 字段，回滚解析必须容忍这类只读属性
        assertTrue(afterData.contains("\"deceased\""));

        FamilyNode parsed = service.parseNodeVersion(afterData);

        assertEquals(node.getId(), parsed.getId());
        assertEquals(node.getFamilyId(), parsed.getFamilyId());
        assertEquals(node.getName(), parsed.getName());
        assertEquals(node.getGender(), parsed.getGender());
        assertEquals(node.getBirthDate(), parsed.getBirthDate());
        assertEquals(node.getDeathDate(), parsed.getDeathDate());
        assertEquals(node.getGeneration(), parsed.getGeneration());
        assertEquals(node.getBirthOrder(), parsed.getBirthOrder());
        assertEquals(node.getColorLabel(), parsed.getColorLabel());
        assertEquals(node.getLunarBirthDate(), parsed.getLunarBirthDate());
        assertEquals(node.getSpouseName(), parsed.getSpouseName());
        assertEquals(node.getGraveLocation(), parsed.getGraveLocation());
        assertEquals(node.getBiography(), parsed.getBiography());
        assertEquals(node.getCreateTime(), parsed.getCreateTime());
        assertEquals(node.getUpdateTime(), parsed.getUpdateTime());
    }

    @Test
    void recordNodeRollbackWritesRollbackVersionWithSummary() {
        when(historyRepository.getNextNodeVersion(NODE_ID)).thenReturn(4);
        FamilyNode beforeNode = fullNode();
        beforeNode.setName("蒙二");
        beforeNode.setGeneration(3);
        FamilyNode restoredNode = fullNode();

        service.recordNodeRollback(beforeNode, restoredNode, 2, USER_ID, USERNAME, IP);

        ArgumentCaptor<NodeHistory> saved = ArgumentCaptor.forClass(NodeHistory.class);
        verify(historyRepository).saveNodeHistory(saved.capture());
        NodeHistory history = saved.getValue();
        assertEquals("ROLLBACK", history.getOperationType());
        assertEquals(4, history.getVersionNumber().intValue());
        assertNotNull(history.getBeforeData());
        assertNotNull(history.getAfterData());
        assertTrue(history.getChangeSummary().startsWith("回滚到版本2："));
        assertTrue(history.getChangeSummary().contains("姓名: 蒙二 → 蒙一"));
        assertTrue(history.getChangeSummary().contains("世代变更: 3 → 1"));
    }

    @Test
    void parseNodeVersionRejectsBlankData() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parseNodeVersion("  "));

        assertEquals("版本数据为空，无法回滚", ex.getMessage());
    }

    @Test
    void parseNodeVersionWrapsBrokenJsonAsBusinessError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.parseNodeVersion("{oops"));

        assertEquals("版本数据无法解析，无法回滚", ex.getMessage());
    }

    @Test
    void restoreContentFromClearsFieldsMissingInVersion() {
        FamilyNode current = fullNode();
        current.setName("蒙二");
        current.setRemark("后来补的备注");
        FamilyNode version = fullNode();
        version.setRemark(null);
        version.setBiography(null);

        current.restoreContentFrom(version);

        assertEquals("蒙一", current.getName());
        assertNull(current.getRemark());
        assertNull(current.getBiography());
        assertEquals(NODE_ID, current.getId());
        assertEquals(FAMILY_ID, current.getFamilyId());
        assertEquals(USER_ID, current.getUserId());
    }

    private FamilyNode fullNode() {
        FamilyNode node = new FamilyNode();
        node.setId(NODE_ID);
        node.setUserId(USER_ID);
        node.setFamilyId(FAMILY_ID);
        node.setName("蒙一");
        node.setGender(1);
        node.setBirthDate(LocalDate.of(1980, 3, 4));
        node.setDeathDate(LocalDate.of(2020, 5, 6));
        node.setGeneration(1);
        node.setBirthOrder(2);
        node.setColorLabel("red");
        node.setAvatar("/photo/1/a.jpg");
        node.setRemark("享年四十");
        node.setLunarBirthDate("一九八〇年二月十八");
        node.setLunarDeathDate("二〇二〇年四月十四");
        node.setZi("子明");
        node.setHao("静庵");
        node.setHui("德");
        node.setGraveLocation("东山祖坟");
        node.setSpouseName("李氏");
        node.setSpouseOriginFamily("李家村");
        node.setBiography("生平事迹");
        node.setCreateTime(LocalDateTime.of(2026, 8, 28, 10, 0, 0));
        node.setUpdateTime(LocalDateTime.of(2026, 9, 1, 11, 0, 0));
        return node;
    }
}
