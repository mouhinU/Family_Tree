package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 族谱节点数据传输对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
public class FamilyNodeDTO {

    private Long id;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50个字符")
    private String name;
    private Integer gender;
    private String birthDate;
    private String deathDate;
    private Integer generation;
    private Integer birthOrder;
    private String colorLabel;
    private String avatar;
    private String remark;

    /** 农历出生日期 */
    private String lunarBirthDate;

    /** 农历去世日期 */
    private String lunarDeathDate;

    /** 字 */
    @Size(max = 50, message = "字长度不能超过50个字符")
    private String zi;

    /** 号 */
    @Size(max = 50, message = "号长度不能超过50个字符")
    private String hao;

    /** 讳 */
    @Size(max = 50, message = "讳长度不能超过50个字符")
    private String hui;

    /** 坟茔位置 */
    @Size(max = 200, message = "坟茔位置长度不能超过200个字符")
    private String graveLocation;

    /** 配偶姓名（外嫁女婚配记录） */
    @Size(max = 50, message = "配偶姓名长度不能超过50个字符")
    private String spouseName;

    /** 婚配方家族（外嫁女婚配记录） */
    @Size(max = 100, message = "婚配方家族长度不能超过100个字符")
    private String spouseOriginFamily;
}
