package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 平台管理页单家族概况视图对象
 * <p>
 * 家族基础信息与规模计数复用列表行对象，另附内容规模、最近操作与生效配置，
 * 供概况抽屉一次性渲染，避免前端多次往返。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class FamilyAdminDetailVO {

    /**
     * 家族基础信息与主要规模计数
     */
    private FamilyAdminRowVO family;

    /**
     * 版本快照数
     */
    private Integer snapshotCount;

    /**
     * 相册照片数
     */
    private Integer photoCount;

    /**
     * 最近一次平台侧记录的操作时间，无操作时为 null
     */
    private LocalDateTime lastOperationTime;

    /**
     * 最近若干条操作日志（按时间倒序）
     */
    private List<OperationLogDTO> recentOperations = new ArrayList<>();

    /**
     * 全部已登记配置项的生效值（含未覆盖项的默认值）
     */
    private List<FamilySettingVO> settings = new ArrayList<>();
}
