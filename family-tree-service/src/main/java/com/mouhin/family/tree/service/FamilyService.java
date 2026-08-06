package com.mouhin.family.tree.service;

import com.mouhin.family.tree.common.dto.FamilyCreateDTO;
import com.mouhin.family.tree.common.dto.FamilyDTO;
import com.mouhin.family.tree.common.dto.FamilyJoinDTO;
import com.mouhin.family.tree.common.dto.FamilyMemberDTO;

import java.util.List;

/**
 * 家族管理服务接口
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
public interface FamilyService {

    /**
     * 创建家族（创建者自动成为族长，已有数据迁移至新家族）
     *
     * @param userId 当前用户ID
     * @param dto    创建请求
     * @return 新家族ID
     */
    Long createFamily(Long userId, FamilyCreateDTO dto);

    /**
     * 通过邀请码加入家族
     *
     * @param userId 当前用户ID
     * @param dto    加入请求
     */
    void joinFamily(Long userId, FamilyJoinDTO dto);

    /**
     * 获取当前用户所属家族信息
     *
     * @param userId 当前用户ID
     * @return 家族信息（含当前用户角色），未加入家族返回 null
     */
    FamilyDTO getCurrentFamily(Long userId);

    /**
     * 获取家族成员列表
     *
     * @param familyId 家族ID
     * @return 成员列表
     */
    List<FamilyMemberDTO> listMembers(Long familyId);

    /**
     * 移除家族成员（仅族长可操作）
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @param targetUserId   被移除的用户ID
     */
    void removeMember(Long familyId, Long operatorUserId, Long targetUserId);

    /**
     * 刷新邀请码（仅族长可操作）
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @return 新邀请码
     */
    String refreshInviteCode(Long familyId, Long operatorUserId);

    /**
     * 设置成员角色（仅族长可操作，可将成员设为管理员或取消管理员）
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @param targetUserId   目标用户ID
     * @param role           目标角色（ADMIN / MEMBER）
     */
    void setMemberRole(Long familyId, Long operatorUserId, Long targetUserId, String role);

    /**
     * 根据家族ID获取家族信息（不含角色信息，内部使用）
     *
     * @param familyId 家族ID
     * @return 家族信息
     */
    FamilyDTO getFamilyById(Long familyId);

    /**
     * 切换当前激活的家族
     *
     * @param userId   用户ID
     * @param familyId 目标家族ID
     */
    void switchFamily(Long userId, Long familyId);

    /**
     * 获取用户所属的所有家族列表
     *
     * @param userId 用户ID
     * @return 家族列表
     */
    List<FamilyDTO> listMyFamilies(Long userId);

    /**
     * 更新家族信息（堂号、籍贯等）
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @param hallName       堂号（可为null）
     * @param ancestralHome  籍贯（可为null）
     */
    void updateFamilyInfo(Long familyId, Long operatorUserId, String hallName, String ancestralHome);

    /**
     * 更新辈分管理行列布局
     *
     * @param familyId       家族ID
     * @param operatorUserId 操作者用户ID
     * @param cols           列数
     * @param rows           行数
     */
    void updateGenerationLayout(Long familyId, Long operatorUserId, Integer cols, Integer rows);
}
