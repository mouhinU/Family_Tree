package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.AiApplicationService;
import com.mouhin.family.tree.application.service.FamilySettingApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.*;
import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.security.NodeAccessActionEnum;
import com.mouhin.family.tree.web.security.RequireFamilyManager;
import com.mouhin.family.tree.web.security.RequireNodeAccess;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

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
    private final FamilySettingApplicationService settingService;

    /**
     * AI 功能总开关（env AI_LLM_ENABLED），未开启即视为未配置
     */
    @Value("${ai.llm.enabled:false}")
    private boolean llmEnabled;

    /**
     * 大模型接入密钥（env AI_API_KEY），空值视为未配置
     */
    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    public AiController(AiApplicationService aiApplicationService,
                        FamilySettingApplicationService settingService) {
        this.aiApplicationService = aiApplicationService;
        this.settingService = settingService;
    }

    /**
     * AI 配置状态：yml 前提成立且当前家族 AI 开关生效为开才算已配置，
     * 前端据此决定 AI 助手入口显隐，与功能端点的 {@link #assertAiEnabled(Long)} 同一口径
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(HttpSession session) {
        Map<String, Object> data = new HashMap<>(2);
        data.put("configured", llmEnabled && StringUtils.hasText(openaiApiKey) && aiEnabledForSession(session));
        return Result.success(data);
    }

    /**
     * 智能录入：自然语言描述 → 结构化节点 + 关系
     */
    @PostMapping("/smart-entry")
    @RequireFamilyManager
    public Result<AiSmartEntryVO> smartEntry(HttpSession session, @Valid @RequestBody AiSmartEntryDTO dto) {
        Long familyId = getCurrentFamilyId(session);
        assertAiEnabled(familyId);
        AiSmartEntryVO vo = aiApplicationService.smartEntry(familyId, dto);
        return Result.success(vo);
    }

    /**
     * 自然语言查询：基于族谱数据回答问题
     */
    @PostMapping("/query")
    @RequireFamilyManager
    public Result<AiQueryVO> query(HttpSession session, @Valid @RequestBody AiQueryDTO dto) {
        Long familyId = getCurrentFamilyId(session);
        assertAiEnabled(familyId);
        AiQueryVO vo = aiApplicationService.query(familyId, dto);
        return Result.success(vo);
    }

    /**
     * 生成家族故事：基于指定节点生成人物传记
     */
    @PostMapping("/story")
    @RequireNodeAccess(node = "#dto.nodeId", action = NodeAccessActionEnum.OPERATE)
    public Result<AiStoryVO> generateStory(HttpSession session, @Valid @RequestBody AiStoryDTO dto) {
        Long familyId = getCurrentFamilyId(session);
        assertAiEnabled(familyId);
        AiStoryVO vo = aiApplicationService.generateStory(familyId, dto);
        return Result.success(vo);
    }

    /**
     * OCR 解析：将 OCR 识别文字解析为结构化数据
     */
    @PostMapping("/ocr-parse")
    @RequireFamilyManager
    public Result<AiSmartEntryVO> ocrParse(HttpSession session, @Valid @RequestBody AiOcrParseDTO dto) {
        Long familyId = getCurrentFamilyId(session);
        assertAiEnabled(familyId);
        AiSmartEntryVO vo = aiApplicationService.ocrParse(familyId, dto);
        return Result.success(vo);
    }

    /**
     * 当前会话家族的 AI 开关是否生效为开；未选择家族时视为不可用（功能端点同样要求家族上下文）
     */
    private boolean aiEnabledForSession(HttpSession session) {
        Long familyId = (Long) session.getAttribute(FamilyTreeConsts.SESSION_FAMILY_ID);
        return familyId != null && settingService.isEnabled(familyId, FamilySettingKeyEnum.AI_FEATURE_ENABLED);
    }

    /**
     * 平台侧关闭该家族 AI 能力时拒绝访问
     */
    private void assertAiEnabled(Long familyId) {
        settingService.assertEnabled(familyId, FamilySettingKeyEnum.AI_FEATURE_ENABLED);
    }
}
