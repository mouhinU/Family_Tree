package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mouhin.family.tree.domain.entity.OperationLog;
import com.mouhin.family.tree.infrastructure.persistence.entity.OperationLogDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.OperationLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作日志仓储实现单元测试：覆盖平台检索条件的谓词拼装、右模糊传参、
 * 分页与排序落点，以及计数查询必须无序（H2 严格模式拒绝带排序的 count）。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@ExtendWith(MockitoExtension.class)
class OperationLogRepositoryImplTest {

    private static final Long FAMILY_ID = 100L;
    private static final String OPERATION_TYPE = "LOGIN";
    private static final String KEYWORD = "meng";
    private static final LocalDateTime START_TIME = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 11, 16, 23, 59, 59);

    @Mock
    private OperationLogMapper mapper;

    private OperationLogRepositoryImpl repository;

    @BeforeAll
    static void installLambdaCache() {
        // 纯单元测试不经过 MyBatis 容器，需手工登记表信息才能解析 Lambda 列名
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), OperationLogDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new OperationLogRepositoryImpl(mapper);
    }

    @Test
    void listByFilterAssemblesEveryPredicateAndPagingTail() {
        when(mapper.selectList(any())).thenReturn(List.of(row(1L, FAMILY_ID, OPERATION_TYPE)));

        List<OperationLog> logs = repository.listByFilter(
                FAMILY_ID, OPERATION_TYPE, KEYWORD, START_TIME, END_TIME, 40, 20);

        assertEquals(1, logs.size());
        assertEquals(FAMILY_ID, logs.get(0).getFamilyId());
        String sql = capturedListSql();
        assertTrue(sql.contains("family_id = ?"), sql);
        assertTrue(sql.contains("operation_type = ?"), sql);
        assertTrue(sql.contains("(username LIKE ? OR operation_desc LIKE ?)"), sql);
        assertTrue(sql.contains("create_time >= ?"), sql);
        assertTrue(sql.contains("create_time <= ?"), sql);
        assertTrue(sql.endsWith("ORDER BY create_time DESC,id DESC LIMIT 20 OFFSET 40"), sql);
    }

    @Test
    void listByFilterUsesRightFuzzySoIndexPrefixStillApplies() {
        when(mapper.selectList(any())).thenReturn(List.of());

        repository.listByFilter(null, null, KEYWORD, null, null, 0, 20);

        LambdaQueryWrapper<OperationLogDO> wrapper = captureListWrapper();
        String sql = wrapper.getTargetSql();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(KEYWORD + "%"),
                wrapper.getParamNameValuePairs().toString());
        assertTrue(sql.contains("(username LIKE ? OR operation_desc LIKE ?)"), sql);
        assertFalse(sql.contains("family_id = ?"), sql);
        assertFalse(sql.contains("operation_type = ?"), sql);
        assertFalse(sql.contains("create_time >= ?"), sql);
        assertFalse(sql.contains("create_time <= ?"), sql);
    }

    @Test
    void listByFilterTreatsBlankTextAsNoFilter() {
        when(mapper.selectList(any())).thenReturn(List.of());

        repository.listByFilter(FAMILY_ID, "  ", "", null, null, 0, 20);

        String sql = capturedListSql();
        assertFalse(sql.contains("operation_type"), sql);
        assertFalse(sql.contains("LIKE"), sql);
        assertTrue(sql.endsWith("ORDER BY create_time DESC,id DESC LIMIT 20 OFFSET 0"), sql);
    }

    @Test
    void countByFilterKeepsTheWrapperUnordered() {
        when(mapper.selectCount(any())).thenReturn(7L);

        long total = repository.countByFilter(FAMILY_ID, OPERATION_TYPE, KEYWORD, START_TIME, END_TIME);

        assertEquals(7L, total);
        ArgumentCaptor<LambdaQueryWrapper<OperationLogDO>> captor = wrapperCaptor();
        verify(mapper).selectCount(captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertFalse(sql.contains("ORDER BY"), sql);
        assertFalse(sql.contains("LIMIT"), sql);
        assertTrue(sql.contains("family_id = ?"), sql);
    }

    @Test
    void listDistinctOperationTypesProjectsOnlyTheGroupedColumn() {
        when(mapper.selectList(any())).thenReturn(List.of(
                row(1L, FAMILY_ID, "NODE_CREATE"), row(2L, FAMILY_ID, "LOGIN"), blankTypeRow()));

        List<String> types = repository.listDistinctOperationTypes();

        assertEquals(List.of("NODE_CREATE", "LOGIN"), types);
        ArgumentCaptor<LambdaQueryWrapper<OperationLogDO>> captor = wrapperCaptor();
        verify(mapper).selectList(captor.capture());
        LambdaQueryWrapper<OperationLogDO> wrapper = captor.getValue();
        assertEquals("operation_type", wrapper.getSqlSelect());
        assertTrue(wrapper.getSqlSegment().contains("GROUP BY operation_type"), wrapper.getSqlSegment());
    }

    @Test
    void saveBackfillsGeneratedIdOntoDomainObject() {
        when(mapper.insert(any(OperationLogDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, OperationLogDO.class).setId(88L);
            return 1;
        });
        OperationLog entity = new OperationLog();
        entity.setOperationType(OPERATION_TYPE);

        repository.save(entity);

        assertEquals(88L, entity.getId());
    }

    private LambdaQueryWrapper<OperationLogDO> captureListWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<OperationLogDO>> captor = wrapperCaptor();
        verify(mapper).selectList(captor.capture());
        return captor.getValue();
    }

    private String capturedListSql() {
        return captureListWrapper().getTargetSql();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaQueryWrapper<OperationLogDO>> wrapperCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    private OperationLogDO row(Long id, Long familyId, String operationType) {
        OperationLogDO doObj = new OperationLogDO();
        doObj.setId(id);
        doObj.setUserId(7L);
        doObj.setUsername(KEYWORD + "ziyu");
        doObj.setOperationType(operationType);
        doObj.setOperationDesc("登录系统");
        doObj.setTargetType("user");
        doObj.setTargetId(7L);
        doObj.setFamilyId(familyId);
        doObj.setIpAddress("127.0.0.1");
        doObj.setCreateTime(START_TIME);
        return doObj;
    }

    private OperationLogDO blankTypeRow() {
        OperationLogDO doObj = new OperationLogDO();
        doObj.setId(3L);
        doObj.setOperationType(null);
        return doObj;
    }
}
