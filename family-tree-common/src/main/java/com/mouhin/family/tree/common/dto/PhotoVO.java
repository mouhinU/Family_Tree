package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 家族相册照片展示对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class PhotoVO {

    /**
     * 照片ID
     */
    private Long id;

    /**
     * 上传用户ID
     */
    private Long userId;

    /**
     * 上传用户名
     */
    private String username;

    /**
     * 照片标题
     */
    private String title;

    /**
     * 照片描述
     */
    private String description;

    /**
     * 照片访问地址
     */
    private String photoUrl;

    /**
     * 照片中标记的人物列表
     */
    private List<PhotoTagVO> tags;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否为当前用户上传（用于前端判断删除权限）
     */
    private Boolean own;
}
