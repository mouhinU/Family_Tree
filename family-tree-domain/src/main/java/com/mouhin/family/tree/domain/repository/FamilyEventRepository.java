package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.EventSignup;
import com.mouhin.family.tree.domain.entity.FamilyEvent;

import java.util.List;

/**
 * 家族活动仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public interface FamilyEventRepository {

    /**
     * 保存活动
     *
     * @param event 活动领域对象
     * @return 保存后的活动（含ID）
     */
    FamilyEvent save(FamilyEvent event);

    /**
     * 根据ID查询活动
     *
     * @param id 活动ID
     * @return 活动领域对象，不存在返回null
     */
    FamilyEvent findById(Long id);

    /**
     * 查询家族活动列表（按创建时间倒序）
     *
     * @param familyId 家族ID
     * @return 活动列表
     */
    List<FamilyEvent> findByFamilyId(Long familyId);

    /**
     * 更新活动
     *
     * @param event 活动领域对象
     */
    void update(FamilyEvent event);

    /**
     * 删除活动
     *
     * @param id 活动ID
     */
    void removeById(Long id);

    /**
     * 保存报名记录
     *
     * @param signup 报名领域对象
     * @return 保存后的报名（含ID）
     */
    EventSignup saveSignup(EventSignup signup);

    /**
     * 查询活动的报名列表（按报名时间正序）
     *
     * @param eventId 活动ID
     * @return 报名列表
     */
    List<EventSignup> findSignupsByEventId(Long eventId);

    /**
     * 查询用户在活动中的报名记录
     *
     * @param eventId 活动ID
     * @param userId  用户ID
     * @return 报名记录，不存在返回null
     */
    EventSignup findSignup(Long eventId, Long userId);

    /**
     * 删除报名记录
     *
     * @param signupId 报名ID
     */
    void removeSignupById(Long signupId);

    /**
     * 删除活动的全部报名记录
     *
     * @param eventId 活动ID
     */
    void removeSignupsByEventId(Long eventId);
}
