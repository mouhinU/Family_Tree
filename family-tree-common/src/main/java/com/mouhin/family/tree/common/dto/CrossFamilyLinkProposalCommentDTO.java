package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 跨族链接提案审核意见请求体
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
public class CrossFamilyLinkProposalCommentDTO {

    /**
     * 审核意见或备注
     */
    @Size(max = 1000, message = "审核意见不能超过1000个字符")
    private String comment;
}
