package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.common.dto.FamilyNodeDTO;
import com.mouhin.family.tree.common.dto.FamilyRelationDTO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.service.FamilyNodeService;
import com.mouhin.family.tree.service.FamilyRelationService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private final FamilyNodeService familyNodeService;
    private final FamilyRelationService familyRelationService;

    public DataExportController(FamilyNodeService familyNodeService,
                                FamilyRelationService familyRelationService) {
        this.familyNodeService = familyNodeService;
        this.familyRelationService = familyRelationService;
    }

    /**
     * 导出当前家族全部节点与关系为 JSON
     */
    @GetMapping("/export")
    public Result<Map<String, Object>> exportData(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        List<FamilyNodeDTO> nodes = familyNodeService.listNodes(familyId);
        List<FamilyRelationDTO> relations = familyRelationService.listAllRelations(familyId);

        Map<String, Object> data = new HashMap<>(4);
        data.put("nodes", nodes);
        data.put("relations", relations);
        data.put("nodeCount", nodes.size());
        data.put("relationCount", relations.size());

        logger.info("Exported {} nodes and {} relations for family={}", nodes.size(), relations.size(), familyId);
        return Result.success(data);
    }
}
