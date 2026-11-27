package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 家族仓储实现单元测试
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@ExtendWith(MockitoExtension.class)
class FamilyRepositoryImplTest {

    @Mock
    private FamilyMapper mapper;

    @BeforeAll
    static void installLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), FamilyDO.class);
    }

    @Test
    void listAll_ordersByIdAndMapsRows() {
        FamilyDO first = row(1L, "甲族", "NORMAL");
        FamilyDO second = row(2L, "乙族", "FROZEN");
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        FamilyRepositoryImpl repository = new FamilyRepositoryImpl(mapper);

        List<Family> result = repository.listAll();

        assertEquals(2, result.size());
        assertEquals(Long.valueOf(1L), result.get(0).getId());
        assertEquals("甲族", result.get(0).getName());
        assertEquals("NORMAL", result.get(0).getStatus());
        ArgumentCaptor<LambdaQueryWrapper<FamilyDO>> captor = familyWrapperCaptor();
        verify(mapper).selectList(captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertTrue(sql.contains("ORDER BY"));
        assertTrue(sql.contains("id"));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaQueryWrapper<FamilyDO>> familyWrapperCaptor() {
        return (ArgumentCaptor<LambdaQueryWrapper<FamilyDO>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Object.class);
    }

    private FamilyDO row(Long id, String name, String status) {
        FamilyDO row = new FamilyDO();
        row.setId(id);
        row.setName(name);
        row.setStatus(status);
        return row;
    }
}
