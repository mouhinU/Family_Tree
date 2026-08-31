package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 纪念日创建/更新请求对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class AnniversaryDTO {

    /**
     * 关联族谱节点ID（可空）
     */
    private Long nodeId;

    /**
     * 纪念日标题
     */
    @NotBlank(message = "纪念日标题不能为空")
    @Size(max = 100, message = "纪念日标题不能超过100个字符")
    private String title;

    /**
     * 分类编码（wedding 结婚周年 / school 入学毕业 / memorial 纪念 / other 其他）
     */
    @NotBlank(message = "请选择纪念日分类")
    private String category;

    /**
     * 纪念日日期（格式：yyyy-MM-dd）
     */
    @NotBlank(message = "纪念日日期不能为空")
    private String anniversaryDate;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
