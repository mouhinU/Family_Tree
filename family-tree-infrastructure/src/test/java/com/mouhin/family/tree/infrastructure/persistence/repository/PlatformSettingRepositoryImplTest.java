package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mouhin.family.tree.domain.entity.PlatformSetting;
import com.mouhin.family.tree.infrastructure.persistence.entity.PlatformSettingDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.PlatformSettingMapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台配置仓储实现单元测试：覆盖按键排序的全量读取、按键查询的谓词与限量、
 * 新增回填主键、按ID更新与按键删除的落点。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@ExtendWith(MockitoExtension.class)
class PlatformSettingRepositoryImplTest {

    private static final String SETTING_KEY = "REGISTER_ENABLED";

    @Mock
    private PlatformSettingMapper mapper;

    private PlatformSettingRepositoryImpl repository;

    @BeforeAll
    static void installLambdaCache() {
        // 纯单元测试不经过 MyBatis 容器，需手工登记表信息才能解析 Lambda 列名
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PlatformSettingDO.class);
    }

    @BeforeEach
    void setUp() {
        repository = new PlatformSettingRepositoryImpl(mapper);
    }

    private PlatformSettingDO row(Long id, String key, String value) {
        PlatformSettingDO doObj = new PlatformSettingDO();
        doObj.setId(id);
        doObj.setSettingKey(key);
        doObj.setSettingValue(value);
        doObj.setCreateTime(LocalDateTime.now());
        doObj.setUpdateTime(LocalDateTime.now());
        return doObj;
    }

    private PlatformSetting entity(String value) {
        PlatformSetting setting = new PlatformSetting();
        setting.setSettingKey(SETTING_KEY);
        setting.setSettingValue(value);
        return setting;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaQueryWrapper<PlatformSettingDO>> wrapperCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    @Test
    void findAllSortsBySettingKeyForStableRendering() {
        when(mapper.selectList(any())).thenReturn(List.of(
                row(1L, "AI_FEATURE_ENABLED", "false"), row(2L, SETTING_KEY, "false")));

        List<PlatformSetting> all = repository.findAll();

        assertEquals(2, all.size());
        assertEquals("AI_FEATURE_ENABLED", all.get(0).getSettingKey());
        ArgumentCaptor<LambdaQueryWrapper<PlatformSettingDO>> captor = wrapperCaptor();
        verify(mapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("ORDER BY setting_key"),
                captor.getValue().getSqlSegment());
    }

    @Test
    void findByKeyRestrictsToSingleRowAndBindsParameter() {
        when(mapper.selectOne(any())).thenReturn(row(7L, SETTING_KEY, "false"));

        PlatformSetting found = repository.findByKey(SETTING_KEY);

        assertEquals(7L, found.getId());
        assertEquals("false", found.getSettingValue());
        ArgumentCaptor<LambdaQueryWrapper<PlatformSettingDO>> captor = wrapperCaptor();
        verify(mapper).selectOne(captor.capture());
        LambdaQueryWrapper<PlatformSettingDO> wrapper = captor.getValue();
        assertTrue(wrapper.getTargetSql().contains("setting_key = ?"), wrapper.getTargetSql());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(SETTING_KEY));
        assertTrue(wrapper.getSqlSegment().endsWith("LIMIT 1"), wrapper.getSqlSegment());
    }

    @Test
    void findByKeyReturnsNullWhenNotOverridden() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertNull(repository.findByKey("MAX_FAMILY_COUNT"));
    }

    @Test
    void saveBackfillsGeneratedIdOntoDomainObject() {
        when(mapper.insert(any(PlatformSettingDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, PlatformSettingDO.class).setId(88L);
            return 1;
        });
        PlatformSetting setting = entity("false");

        PlatformSetting saved = repository.save(setting);

        assertEquals(88L, saved.getId());
        ArgumentCaptor<PlatformSettingDO> captor = ArgumentCaptor.forClass(PlatformSettingDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(SETTING_KEY, captor.getValue().getSettingKey());
    }

    @Test
    void updateWritesByIdWithCurrentValue() {
        PlatformSetting setting = entity("true");
        setting.setId(66L);

        repository.update(setting);

        ArgumentCaptor<PlatformSettingDO> captor = ArgumentCaptor.forClass(PlatformSettingDO.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(66L, captor.getValue().getId());
        assertEquals("true", captor.getValue().getSettingValue());
    }

    @Test
    void removeByKeyDeletesWithWrapperInsteadOfId() {
        repository.removeByKey(SETTING_KEY);

        ArgumentCaptor<LambdaQueryWrapper<PlatformSettingDO>> captor = wrapperCaptor();
        verify(mapper).delete(captor.capture());
        assertEquals("(setting_key = ?)", captor.getValue().getTargetSql());
    }
}
