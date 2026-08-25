package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.UserRepository;
import com.mouhin.family.tree.infrastructure.converter.UserConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.SysUserDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.SysUserMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * User 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final SysUserMapper mapper;

    public UserRepositoryImpl(SysUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public User save(User entity) {
        SysUserDO doObj = UserConverter.toDO(entity);
        mapper.insert(doObj);
        entity.setId(doObj.getId());
        return entity;
    }

    @Override
    public User findById(Long id) {
        SysUserDO doObj = mapper.selectById(id);
        return UserConverter.toDomain(doObj);
    }

    @Override
    public User findByUsername(String username) {
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserDO::getUsername, username)
                .last("LIMIT 1");
        SysUserDO doObj = mapper.selectOne(wrapper);
        return UserConverter.toDomain(doObj);
    }

    @Override
    public List<User> findByIds(List<Long> ids) {
        List<SysUserDO> doList = mapper.selectBatchIds(ids);
        return UserConverter.toDomainList(doList);
    }

    @Override
    public void update(User entity) {
        SysUserDO doObj = UserConverter.toDO(entity);
        mapper.updateById(doObj);
    }

    @Override
    public void updateNodeId(Long userId, Long nodeId) {
        LambdaUpdateWrapper<SysUserDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUserDO::getId, userId)
                .set(SysUserDO::getNodeId, nodeId);
        mapper.update(null, wrapper);
    }
}
