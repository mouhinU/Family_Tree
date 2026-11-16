package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    public User findByEmail(String email) {
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserDO::getEmail, email)
                .last("LIMIT 1");
        SysUserDO doObj = mapper.selectOne(wrapper);
        return UserConverter.toDomain(doObj);
    }

    @Override
    public User findByPhone(String phone) {
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserDO::getPhone, phone)
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
    public List<User> findByCurrentFamilyId(Long familyId) {
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserDO::getCurrentFamilyId, familyId)
                .orderByAsc(SysUserDO::getUsername);
        List<SysUserDO> doList = mapper.selectList(wrapper);
        return UserConverter.toDomainList(doList);
    }

    @Override
    public void update(User entity) {
        SysUserDO doObj = UserConverter.toDO(entity);
        mapper.updateById(doObj);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public List<User> listPaged(String keyword, Boolean autoAccount, int offset, int size) {
        LambdaQueryWrapper<SysUserDO> wrapper = filterWrapper(keyword, autoAccount)
                .orderByDesc(SysUserDO::getCreateTime)
                .orderByDesc(SysUserDO::getId)
                .last("LIMIT " + size + " OFFSET " + offset);
        List<SysUserDO> doList = mapper.selectList(wrapper);
        return UserConverter.toDomainList(doList);
    }

    @Override
    public long countByFilter(String keyword, Boolean autoAccount) {
        // H2 严格模式下 selectCount 不允许携带 ORDER BY，计数使用无条件包装器
        return mapper.selectCount(filterWrapper(keyword, autoAccount));
    }

    @Override
    public long countAll() {
        return mapper.selectCount(new LambdaQueryWrapper<>());
    }

    @Override
    public long countAutoAccounts() {
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserDO::getAutoAccount, Boolean.TRUE);
        return mapper.selectCount(wrapper);
    }

    /**
     * 组装平台用户列表的筛选条件：文本走右模糊，联系方式走精确匹配，避免按号码逐位试探。
     */
    private LambdaQueryWrapper<SysUserDO> filterWrapper(String keyword, Boolean autoAccount) {
        LambdaQueryWrapper<SysUserDO> wrapper = new LambdaQueryWrapper<>();
        if (autoAccount != null) {
            if (autoAccount) {
                wrapper.eq(SysUserDO::getAutoAccount, Boolean.TRUE);
            } else {
                // 存量真人账号可能尚未回写标记，FALSE 需把 NULL 一并视为真人账号
                wrapper.and(w -> w.isNull(SysUserDO::getAutoAccount).or().eq(SysUserDO::getAutoAccount, Boolean.FALSE));
            }
        }
        if (keyword == null || keyword.isBlank()) {
            return wrapper;
        }
        String trimmed = keyword.trim();
        wrapper.and(w -> w.likeRight(SysUserDO::getUsername, trimmed)
                .or().likeRight(SysUserDO::getNickname, trimmed)
                .or().eq(SysUserDO::getPhone, trimmed)
                .or().eq(SysUserDO::getEmail, trimmed));
        return wrapper;
    }
}
