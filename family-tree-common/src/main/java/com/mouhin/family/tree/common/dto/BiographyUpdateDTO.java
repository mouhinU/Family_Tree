package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 人物传记更新请求对象（biography 为富文本 HTML，服务端白名单清洗）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class BiographyUpdateDTO {

    /**
     * 富文本传记内容（空串表示清空传记）
     */
    private String biography;
}
