package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.MemorialMessage;
import com.mouhin.family.tree.domain.repository.MemorialMessageRepository;
import com.mouhin.family.tree.infrastructure.converter.MemorialMessageConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.MemorialMessageDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.MemorialMessageMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MemorialMessage 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Repository
public class MemorialMessageRepositoryImpl implements MemorialMessageRepository {

    private final MemorialMessageMapper mapper;

    public MemorialMessageRepositoryImpl(MemorialMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MemorialMessage save(MemorialMessage message) {
        MemorialMessageDO doObj = MemorialMessageConverter.toDO(message);
        mapper.insert(doObj);
        message.setId(doObj.getId());
        return message;
    }

    @Override
    public MemorialMessage findById(Long id) {
        MemorialMessageDO doObj = mapper.selectById(id);
        return MemorialMessageConverter.toDomain(doObj);
    }

    @Override
    public List<MemorialMessage> findByNodeId(Long nodeId) {
        LambdaQueryWrapper<MemorialMessageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemorialMessageDO::getNodeId, nodeId)
                .orderByDesc(MemorialMessageDO::getCreateTime);
        List<MemorialMessageDO> doList = mapper.selectList(wrapper);
        return MemorialMessageConverter.toDomainList(doList);
    }

    @Override
    public void removeById(Long id) {
        mapper.deleteById(id);
    }
}
