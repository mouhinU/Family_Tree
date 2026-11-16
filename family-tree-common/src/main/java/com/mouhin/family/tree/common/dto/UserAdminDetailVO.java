package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台管理页单用户概况视图对象
 *
 * <p>账号基础信息复用列表行对象，另附其加入的家族与节点标记情况，供详情抽屉一次性渲染。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class UserAdminDetailVO {

    /**
     * 账号基础信息与规模计数
     */
    private UserAdminRowVO user;

    /**
     * 该用户加入的家族列表（按加入时间正序）
     */
    private List<UserAdminFamilyVO> families = new ArrayList<>();

    @Override
    public String toString() {
        return "UserAdminDetailVO{"
                + "user=" + user
                + ", families=" + families.size()
                + '}';
    }
}
