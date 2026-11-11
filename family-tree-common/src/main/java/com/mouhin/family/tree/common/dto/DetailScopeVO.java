package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 族谱节点详情可见范围视图对象
 *
 * <p>非管理员仅可查看"自身绑定节点 + 祖先链到根 + 后代子树到叶 + 上述节点的直系配偶"的详情。
 * 该对象用于告知前端哪些节点可打开详情弹窗：管理员 {@code unrestricted=true}，
 * 非管理员以 {@code nodeIds} 白名单为准（未绑定节点时为空列表）。</p>
 *
 * @author Family-Tree
 * @date 2026-11-11
 */
@Getter
@Setter
public class DetailScopeVO {

    /**
     * 是否不受限（族长/管理员为 true，可查看任意节点详情）
     */
    private Boolean unrestricted;

    /**
     * 可查看详情的节点ID列表（不受限时为 null）
     */
    private List<Long> nodeIds;
}
