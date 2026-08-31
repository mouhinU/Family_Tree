package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.EventSignup;
import com.mouhin.family.tree.domain.entity.FamilyEvent;
import com.mouhin.family.tree.domain.repository.FamilyEventRepository;
import com.mouhin.family.tree.infrastructure.converter.EventSignupConverter;
import com.mouhin.family.tree.infrastructure.converter.FamilyEventConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.EventSignupDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyEventDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.EventSignupMapper;
import com.mouhin.family.tree.infrastructure.persistence.mapper.FamilyEventMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * FamilyEvent 仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Repository
public class FamilyEventRepositoryImpl implements FamilyEventRepository {

    private final FamilyEventMapper eventMapper;
    private final EventSignupMapper signupMapper;

    public FamilyEventRepositoryImpl(FamilyEventMapper eventMapper, EventSignupMapper signupMapper) {
        this.eventMapper = eventMapper;
        this.signupMapper = signupMapper;
    }

    @Override
    public FamilyEvent save(FamilyEvent event) {
        FamilyEventDO doObj = FamilyEventConverter.toDO(event);
        eventMapper.insert(doObj);
        event.setId(doObj.getId());
        return event;
    }

    @Override
    public FamilyEvent findById(Long id) {
        FamilyEventDO doObj = eventMapper.selectById(id);
        return FamilyEventConverter.toDomain(doObj);
    }

    @Override
    public List<FamilyEvent> findByFamilyId(Long familyId) {
        LambdaQueryWrapper<FamilyEventDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FamilyEventDO::getFamilyId, familyId)
                .orderByDesc(FamilyEventDO::getCreateTime);
        List<FamilyEventDO> doList = eventMapper.selectList(wrapper);
        return FamilyEventConverter.toDomainList(doList);
    }

    @Override
    public void update(FamilyEvent event) {
        FamilyEventDO doObj = FamilyEventConverter.toDO(event);
        eventMapper.updateById(doObj);
    }

    @Override
    public void removeById(Long id) {
        eventMapper.deleteById(id);
    }

    @Override
    public EventSignup saveSignup(EventSignup signup) {
        EventSignupDO doObj = EventSignupConverter.toDO(signup);
        signupMapper.insert(doObj);
        signup.setId(doObj.getId());
        return signup;
    }

    @Override
    public List<EventSignup> findSignupsByEventId(Long eventId) {
        LambdaQueryWrapper<EventSignupDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventSignupDO::getEventId, eventId)
                .orderByAsc(EventSignupDO::getCreateTime);
        List<EventSignupDO> doList = signupMapper.selectList(wrapper);
        return EventSignupConverter.toDomainList(doList);
    }

    @Override
    public EventSignup findSignup(Long eventId, Long userId) {
        LambdaQueryWrapper<EventSignupDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventSignupDO::getEventId, eventId)
                .eq(EventSignupDO::getUserId, userId);
        EventSignupDO doObj = signupMapper.selectOne(wrapper);
        return EventSignupConverter.toDomain(doObj);
    }

    @Override
    public void removeSignupById(Long signupId) {
        signupMapper.deleteById(signupId);
    }

    @Override
    public void removeSignupsByEventId(Long eventId) {
        LambdaQueryWrapper<EventSignupDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventSignupDO::getEventId, eventId);
        signupMapper.delete(wrapper);
    }
}
