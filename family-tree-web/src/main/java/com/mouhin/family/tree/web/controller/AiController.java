package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.AiApplicationService;
import com.mouhin.family.tree.common.dto.AiOcrParseDTO;
import com.mouhin.family.tree.common.dto.AiQueryDTO;
import com.mouhin.family.tree.common.dto.AiQueryVO;
import com.mouhin.family.tree.common.dto.AiSmartEntryDTO;
import com.mouhin.family.tree.common.dto.AiSmartEntryVO;
import com.mouhin.family.tree.common.dto.AiStoryDTO;
import com.mouhin.family.tree.common.dto.AiStoryVO;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 助手控制器
 * <p>
 * 提供四大 AI 能力：智能录入、自然语言查询、家族故事生成、OCR 解析。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@RestController
@RequestMapping("/api/ai")
public class AiController extends BaseController {

    private final AiApplicationService aiApplicationService;

    public AiController(AiApplicationService aiApplicationService) {
        this.aiApplicationService = aiApplicationService;
    }

    /**
     * 智能录入：自然语言描述 → 结构化节点 + 关系
     */
    @PostMapping("/smart-entry")
    public Result<AiSmartEntryVO> smartEntry(HttpSession session, @Valid @RequestBody AiSmartEntryDTO dto) {
        Long familyId = getCurrentFamilyId(session);
        AiSmartEntryVO vo = aiApplicationService.smartEntry(familyId, dto);
        return Result.success(vo);
    }

    /**
     * 自然语言查询：基于族谱数据回答问题
     */
    @PostMapping("/query")
    public Result<AiQueryVO> query(HttpSession session, @Valid @RequestBody AiQueryDTO dto) {
        Long familyId = getCurrentFamilyId(session);
        AiQueryVO vo = aiApplicationService.query(familyId, dto);
        return Result.success(vo);
    }

    /**
     * 生成家族故事：基于指定节点生成人物传记
     */
    @PostMapping("/story")
    public Result<AiStoryVO> generateStory(HttpSession session, @Valid @RequestBody AiStoryDTO dto) {
        Long familyId = getCurrentFamilyId(session);
        AiStoryVO vo = aiApplicationService.generateStory(familyId, dto);
        return Result.success(vo);
    }

    /**
     * OCR 解析：将 OCR 识别文字解析为结构化数据
     */
    @PostMapping("/ocr-parse")
    public Result<AiSmartEntryVO> ocrParse(HttpSession session, @Valid @RequestBody AiOcrParseDTO dto) {
        Long familyId = getCurrentFamilyId(session);
        AiSmartEntryVO vo = aiApplicationService.ocrParse(familyId, dto);
        return Result.success(vo);
    }
}
