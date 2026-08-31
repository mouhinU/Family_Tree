package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 私信联系人展示对象（同家族用户）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class UserContactVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 关联的族谱节点ID（可能为空）
     */
    private Long nodeId;
}
