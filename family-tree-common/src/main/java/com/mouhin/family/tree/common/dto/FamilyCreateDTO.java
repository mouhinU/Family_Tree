package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建家族请求 DTO
 *
 * <p>初始节点信息为选填：填写姓名时，创建家族的同时以该信息生成家族第一个族谱节点，并把创建者本人
 * 绑定到该节点；不填则保持原有的空树行为。</p>
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Getter
@Setter
public class FamilyCreateDTO {

    /**
     * 家族名称
     */
    private String name;

    /**
     * 初始节点姓名，为空时不创建初始节点
     */
    @Size(max = 50, message = "初始节点姓名长度不能超过50个字符")
    private String initialNodeName;

    /**
     * 初始节点性别（0 未知 / 1 男 / 2 女）
     */
    private Integer initialNodeGender;

    /**
     * 初始节点出生日期（阳历 yyyy-MM-dd）
     */
    private String initialNodeBirthDate;
}
