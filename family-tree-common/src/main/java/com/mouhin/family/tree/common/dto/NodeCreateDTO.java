package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增节点请求对象（可附带父节点或配偶关系）
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
public class NodeCreateDTO {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50个字符")
    private String name;
    private Integer gender;
    private String birthDate;
    private String deathDate;
    @Size(max = 20, message = "颜色标签长度不能超过20个字符")
    private String colorLabel;
    private String avatar;
    private String remark;

    /**
     * 同胞排次（选填，未填时新增子节点自动追加为末位）
     */
    private Integer birthOrder;

    /**
     * 农历出生日期
     */
    @Size(max = 50, message = "农历出生日期长度不能超过50个字符")
    private String lunarBirthDate;

    /**
     * 农历逝世日期
     */
    @Size(max = 50, message = "农历逝世日期长度不能超过50个字符")
    private String lunarDeathDate;

    /**
     * 字
     */
    @Size(max = 20, message = "字长度不能超过20个字符")
    private String zi;

    /**
     * 号
     */
    @Size(max = 20, message = "号长度不能超过20个字符")
    private String hao;

    /**
     * 讳
     */
    @Size(max = 20, message = "讳长度不能超过20个字符")
    private String hui;

    /**
     * 墓地位置
     */
    @Size(max = 200, message = "墓地位置长度不能超过200个字符")
    private String graveLocation;

    /**
     * 配偶姓名（外嫁女记录）
     */
    @Size(max = 50, message = "配偶姓名长度不能超过50个字符")
    private String spouseName;

    /**
     * 配偶原家族（外嫁女记录）
     */
    @Size(max = 100, message = "配偶原家族长度不能超过100个字符")
    private String spouseOriginFamily;

    /**
     * 父节点ID（建立亲子关系）
     */
    private Long parentNodeId;

    /**
     * 配偶节点ID（建立夫妻关系）
     */
    private Long spouseNodeId;

    /**
     * 子节点ID（新增父节点时使用，建立亲子关系）
     */
    private Long childNodeId;
}
