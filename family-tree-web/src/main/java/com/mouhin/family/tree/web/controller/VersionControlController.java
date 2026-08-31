package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.VersionControlApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.domain.entity.FamilySnapshot;
import com.mouhin.family.tree.domain.entity.NodeHistory;
import com.mouhin.family.tree.domain.entity.RelationHistory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本控制控制器
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@RestController
@RequestMapping("/api/version")
public class VersionControlController extends BaseController {

    private final VersionControlApplicationService versionControlService;

    public VersionControlController(VersionControlApplicationService versionControlService) {
        this.versionControlService = versionControlService;
    }

    /**
     * 查询节点修改历史
     *
     * @param nodeId 节点ID
     * @param page   页码（从1开始）
     * @param size   每页大小
     * @param session HTTP会话
     * @return 历史记录列表
     */
    @GetMapping("/node/{nodeId}/history")
    public Result<Map<String, Object>> getNodeHistory(@PathVariable Long nodeId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        List<NodeHistory> historyList = versionControlService.getNodeHistory(nodeId, familyId, page, size);
        long total = versionControlService.countNodeHistory(nodeId, familyId);

        Map<String, Object> data = new HashMap<>(4);
        data.put("list", historyList);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);

        return Result.success(data);
    }

    /**
     * 查询关系修改历史
     *
     * @param relationId 关系ID
     * @param page       页码（从1开始）
     * @param size       每页大小
     * @param session    HTTP会话
     * @return 历史记录列表
     */
    @GetMapping("/relation/{relationId}/history")
    public Result<Map<String, Object>> getRelationHistory(@PathVariable Long relationId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        List<RelationHistory> historyList = versionControlService.getRelationHistory(relationId, familyId, page, size);
        long total = versionControlService.countRelationHistory(relationId, familyId);

        Map<String, Object> data = new HashMap<>(4);
        data.put("list", historyList);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);

        return Result.success(data);
    }

    /**
     * 对比节点两个版本的差异
     *
     * @param nodeId 节点ID
     * @param v1     版本号1
     * @param v2     版本号2
     * @return 差异Map
     */
    @GetMapping("/node/{nodeId}/compare")
    public Result<Map<String, Object[]>> compareNodeVersions(@PathVariable Long nodeId,
                                                              @RequestParam Integer v1,
                                                              @RequestParam Integer v2) {
        Map<String, Object[]> differences = versionControlService.compareNodeVersions(nodeId, v1, v2);
        return Result.success(differences);
    }

    /**
     * 对比关系两个版本的差异
     *
     * @param relationId 关系ID
     * @param v1         版本号1
     * @param v2         版本号2
     * @return 差异Map
     */
    @GetMapping("/relation/{relationId}/compare")
    public Result<Map<String, Object[]>> compareRelationVersions(@PathVariable Long relationId,
                                                                  @RequestParam Integer v1,
                                                                  @RequestParam Integer v2) {
        Map<String, Object[]> differences = versionControlService.compareRelationVersions(relationId, v1, v2);
        return Result.success(differences);
    }

    /**
     * 回滚节点到指定版本
     *
     * @param nodeId    节点ID
     * @param versionId 目标版本号
     * @param session   HTTP会话
     * @return 回滚后的节点数据
     */
    @PostMapping("/node/{nodeId}/rollback")
    public Result<Map<String, Object>> rollbackNode(@PathVariable Long nodeId,
                                                     @RequestParam Integer versionId,
                                                     HttpSession session,
                                                     HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        String nodeData = versionControlService.rollbackNodeToVersion(nodeId, versionId, familyId,
                getCurrentUserId(session),
                (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME),
                getClientIp(request));

        Map<String, Object> data = new HashMap<>(2);
        data.put("nodeData", nodeData);
        data.put("versionId", versionId);

        return Result.success(data);
    }

    /**
     * 创建家族快照
     *
     * @param snapshotName 快照名称
     * @param description  快照描述
     * @param session      HTTP会话
     * @param request      HTTP请求
     * @return 创建的快照ID
     */
    @PostMapping("/snapshot")
    public Result<Map<String, Object>> createSnapshot(@RequestParam String snapshotName,
                                                       @RequestParam(required = false) String description,
                                                       HttpSession session,
                                                       HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);

        FamilySnapshot snapshot = versionControlService.createSnapshot(
                familyId,
                snapshotName,
                description != null ? description : "",
                userId,
                username != null ? username : "",
                getClientIp(request)
        );

        Map<String, Object> data = new HashMap<>(2);
        data.put("snapshotId", snapshot.getId());
        data.put("createTime", snapshot.getCreateTime());

        return Result.success(data);
    }

    /**
     * 列出家族的所有快照
     *
     * @param session HTTP会话
     * @return 快照列表
     */
    @GetMapping("/snapshot/list")
    public Result<List<FamilySnapshot>> listSnapshots(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        List<FamilySnapshot> snapshots = versionControlService.listSnapshots(familyId);
        return Result.success(snapshots);
    }

    /**
     * 查询快照详情
     *
     * @param snapshotId 快照ID
     * @return 快照详情
     */
    @GetMapping("/snapshot/{snapshotId}")
    public Result<FamilySnapshot> getSnapshot(@PathVariable Long snapshotId) {
        FamilySnapshot snapshot = versionControlService.getSnapshot(snapshotId);
        return Result.success(snapshot);
    }

    /**
     * 删除快照
     *
     * @param snapshotId 快照ID
     * @param session    HTTP会话
     * @return 操作结果
     */
    @DeleteMapping("/snapshot/{snapshotId}")
    public Result<Void> deleteSnapshot(@PathVariable Long snapshotId, HttpSession session,
                                       HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        versionControlService.deleteSnapshot(snapshotId, familyId, getCurrentUserId(session),
                (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME), getClientIp(request));
        return Result.success();
    }

    /**
     * 从快照恢复数据
     * <p>
     * 注意：此接口仅返回快照数据供前端预览，实际恢复需要二次确认
     *
     * @param snapshotId 快照ID
     * @return 快照中的节点和关系数据
     */
    @PostMapping("/snapshot/{snapshotId}/preview")
    public Result<Map<String, Object>> previewSnapshotRestore(@PathVariable Long snapshotId) {
        Map<String, Object> snapshotData = versionControlService.restoreFromSnapshot(snapshotId);
        return Result.success(snapshotData);
    }
}
