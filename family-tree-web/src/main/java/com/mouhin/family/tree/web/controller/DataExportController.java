package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyNodeApplicationService;
import com.mouhin.family.tree.application.service.FamilyRelationApplicationService;
import com.mouhin.family.tree.application.service.GedcomApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.FamilyRelationDTO;
import com.mouhin.family.tree.common.dto.GedcomImportResultVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据导入导出控制器
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
@RestController
@RequestMapping("/api/data")
public class DataExportController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(DataExportController.class);

    private final FamilyNodeApplicationService familyNodeService;
    private final FamilyRelationApplicationService familyRelationService;
    private final GedcomApplicationService gedcomApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    public DataExportController(FamilyNodeApplicationService familyNodeService,
                                FamilyRelationApplicationService familyRelationService,
                                GedcomApplicationService gedcomApplicationService,
                                ApplicationEventPublisher eventPublisher) {
        this.familyNodeService = familyNodeService;
        this.familyRelationService = familyRelationService;
        this.gedcomApplicationService = gedcomApplicationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 导出当前家族全部节点与关系为 JSON
     */
    @GetMapping("/export")
    public Result<Map<String, Object>> exportData(HttpSession session, HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        List<FamilyNodeDTO> nodes = familyNodeService.listNodes(familyId);
        List<FamilyRelationDTO> relations = familyRelationService.listAllRelations(familyId);

        Map<String, Object> data = new HashMap<>(4);
        data.put("nodes", nodes);
        data.put("relations", relations);
        data.put("nodeCount", nodes.size());
        data.put("relationCount", relations.size());

        eventPublisher.publishEvent(OperationPerformedEvent.of(getCurrentUserId(session),
                (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME),
                "DATA_EXPORT", "导出族谱数据(JSON): " + nodes.size() + " 个节点, "
                        + relations.size() + " 条关系",
                "export", null, familyId, getClientIp(request)));
        return Result.success(data);
    }

    /**
     * 导出族谱数据为 GEDCOM 格式文件
     *
     * @param session  HTTP会话
     * @param response HTTP响应
     */
    @GetMapping("/export/gedcom")
    public void exportGedcom(HttpSession session, HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        Long familyId = getCurrentFamilyId(session);
        String gedcomContent = gedcomApplicationService.exportGedcom(familyId, getCurrentUserId(session),
                (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME), getClientIp(request));

        String fileName = "family-tree.ged";
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.getWriter().write(gedcomContent);

        logger.info("Exported GEDCOM for family={}", familyId);
    }

    /**
     * 导入 GEDCOM 文件（覆盖模式：清空现有数据后导入）
     *
     * @param file    GEDCOM 文件
     * @param session HTTP会话
     * @return 导入结果
     */
    @PostMapping("/import/gedcom")
    public Result<GedcomImportResultVO> importGedcom(
            @RequestParam("file") MultipartFile file,
            HttpSession session, HttpServletRequest request) throws IOException {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        String content = readGedcomFile(file);

        GedcomImportResultVO result = gedcomApplicationService.importGedcom(
                familyId, userId, username, getClientIp(request), content);
        logger.info("GEDCOM import completed for family={}: {} nodes imported",
                familyId, result.getImportedNodeCount());
        return Result.success(result);
    }

    /**
     * 追加导入 GEDCOM 文件（保留现有数据）
     *
     * @param file    GEDCOM 文件
     * @param session HTTP会话
     * @return 导入结果
     */
    @PostMapping("/import/gedcom/append")
    public Result<GedcomImportResultVO> appendImportGedcom(
            @RequestParam("file") MultipartFile file,
            HttpSession session, HttpServletRequest request) throws IOException {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);
        String username = (String) session.getAttribute(FamilyTreeConsts.SESSION_USERNAME);
        String content = readGedcomFile(file);

        GedcomImportResultVO result = gedcomApplicationService.appendImportGedcom(
                familyId, userId, username, getClientIp(request), content);
        logger.info("GEDCOM append import completed for family={}: {} nodes imported",
                familyId, result.getImportedNodeCount());
        return Result.success(result);
    }

    /**
     * 读取上传的 GEDCOM 文件内容
     *
     * @param file 上传文件
     * @return 文件文本内容
     */
    private String readGedcomFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null
                && !originalFilename.toLowerCase().endsWith(".ged")
                && !originalFilename.toLowerCase().endsWith(".gedcom")) {
            throw new IllegalArgumentException("仅支持 .ged 或 .gedcom 格式的文件");
        }
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
