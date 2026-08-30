package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.*;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.gateway.AiChatGateway;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 应用服务
 * <p>
 * 提供四大 AI 能力：智能录入、自然语言查询、家族故事生成、OCR 解析。
 * 大模型调用统一委托给 {@link AiChatGateway}（基础设施层基于 Spring AI 实现）。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Service
public class AiApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(AiApplicationService.class);

    private static final String SYSTEM_PROMPT =
            "你是一个专业的族谱管理助手，精通中国家族文化和族谱编撰。请用中文回答。";

    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyTreeApplicationService familyTreeApplicationService;
    private final AiChatGateway aiChatGateway;

    public AiApplicationService(FamilyNodeRepository familyNodeRepository,
                                FamilyRelationRepository familyRelationRepository,
                                FamilyTreeApplicationService familyTreeApplicationService,
                                AiChatGateway aiChatGateway) {
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyTreeApplicationService = familyTreeApplicationService;
        this.aiChatGateway = aiChatGateway;
    }

    /**
     * 智能录入：自然语言描述 → 结构化节点 + 关系
     *
     * @param familyId 家族ID
     * @param dto      描述内容
     * @return 结构化节点和关系
     */
    public AiSmartEntryVO smartEntry(Long familyId, AiSmartEntryDTO dto) {
        String prompt = buildSmartEntryPrompt(dto.getDescription());
        return aiChatGateway.chatForEntity(SYSTEM_PROMPT, prompt, AiSmartEntryVO.class);
    }

    /**
     * 自然语言查询：基于族谱数据回答用户问题
     *
     * @param familyId 家族ID
     * @param dto      查询问题
     * @return AI 回答
     */
    public AiQueryVO query(Long familyId, AiQueryDTO dto) {
        List<TreeNodeVO> tree = familyTreeApplicationService.getFullTree(familyId);
        String treeSummary = buildTreeSummary(tree);
        String prompt = buildQueryPrompt(dto.getQuestion(), treeSummary);
        String response = aiChatGateway.chat(SYSTEM_PROMPT, prompt);
        AiQueryVO vo = new AiQueryVO();
        vo.setAnswer(response);
        return vo;
    }

    /**
     * 生成家族故事：基于指定节点及其家族关系生成人物传记
     *
     * @param familyId 家族ID
     * @param dto      节点ID
     * @return 生成的故事文本
     */
    public AiStoryVO generateStory(Long familyId, AiStoryDTO dto) {
        FamilyNode node = familyNodeRepository.findById(dto.getNodeId());
        if (node == null || !node.getFamilyId().equals(familyId)) {
            throw new BusinessException("节点不存在或无权访问");
        }

        List<FamilyNode> allNodes = familyNodeRepository.findByFamilyId(familyId);
        List<FamilyRelation> allRelations = familyRelationRepository.findByFamilyId(familyId);

        String nodeInfo = buildNodeDetailInfo(node, allNodes, allRelations);
        String prompt = buildStoryPrompt(node, nodeInfo);
        String response = aiChatGateway.chat(SYSTEM_PROMPT, prompt);

        AiStoryVO vo = new AiStoryVO();
        vo.setStory(response);
        return vo;
    }

    /**
     * OCR 解析：将 OCR 识别的文字解析为结构化节点 + 关系
     *
     * @param familyId 家族ID
     * @param dto      OCR 识别文本
     * @return 结构化节点和关系
     */
    public AiSmartEntryVO ocrParse(Long familyId, AiOcrParseDTO dto) {
        String prompt = buildOcrParsePrompt(dto.getRecognizedText());
        return aiChatGateway.chatForEntity(SYSTEM_PROMPT, prompt, AiSmartEntryVO.class);
    }

    // ==================== 提示词工程 ====================

    private String buildSmartEntryPrompt(String description) {
        return "请将以下族谱人物描述解析为结构化的 JSON 数据。\n\n"
                + "描述内容：\n" + description + "\n\n"
                + "要求：\n"
                + "1. 提取所有提到的人物，每个人物包含：name（姓名）、gender（性别：1=男,2=女,0=未知）、birthDate（出生日期，YYYY-MM-DD格式，如果只有年份则用YYYY-01-01）、deathDate（逝世日期，同上）、zi（字）、hao（号）、graveLocation（墓地位置）、remark（备注）\n"
                + "2. 提取人物之间的关系，关系类型：relationType（1=亲子关系from是to的父亲/母亲, 2=夫妻关系, 3=收养关系）\n"
                + "3. 只返回 JSON，不要其他文字说明\n\n"
                + "返回格式：\n"
                + "{\"nodes\":[{\"name\":\"张三\",\"gender\":1,\"birthDate\":\"1950-01-01\",\"deathDate\":null,\"zi\":\"\",\"hao\":\"\",\"graveLocation\":\"\",\"remark\":\"\"}],\"relations\":[{\"fromName\":\"张三\",\"toName\":\"张小五\",\"relationType\":1}]}\n\n"
                + "注意：\n"
                + "- 如果某个字段信息未提及，设为 null\n"
                + "- 亲子关系中 fromName 是父母，toName 是子女\n"
                + "- 夫妻关系中 fromName 和 toName 顺序不限\n"
                + "- 日期格式统一为 YYYY-MM-DD，如果只有年份用 YYYY-01-01";
    }

    private String buildQueryPrompt(String question, String treeSummary) {
        return "你是一个族谱查询助手。以下是当前家族的族谱数据摘要：\n\n"
                + treeSummary + "\n\n"
                + "用户问题：" + question + "\n\n"
                + "请根据以上族谱数据回答用户的问题。回答要简洁准确，直接给出答案。如果数据中没有相关信息，请如实说明。";
    }

    private String buildStoryPrompt(FamilyNode node, String nodeInfo) {
        return "请根据以下族谱人物信息，撰写一篇优美的人物传记/家族故事。\n\n"
                + nodeInfo + "\n\n"
                + "要求：\n"
                + "1. 用中文撰写，语言优美流畅，体现中国家族文化\n"
                + "2. 包含人物的生平事迹、家族关系、品德风范\n"
                + "3. 适当加入对中国传统家族文化的感悟\n"
                + "4. 字数在300-800字之间\n"
                + "5. 只返回故事正文，不要标题和额外说明";
    }

    private String buildOcrParsePrompt(String recognizedText) {
        return "以下是从老族谱图片中通过 OCR 识别出的文字内容。请将其解析为结构化的 JSON 数据。\n\n"
                + "OCR 识别文本：\n" + recognizedText + "\n\n"
                + "要求：\n"
                + "1. 从文字中提取所有人物信息，包括：name（姓名）、gender（性别：1=男,2=女,0=未知）、birthDate（出生日期）、deathDate（逝世日期）、zi（字）、hao（号）、graveLocation（墓地位置）、remark（备注）\n"
                + "2. 提取人物之间的关系：relationType（1=亲子关系, 2=夫妻关系, 3=收养关系）\n"
                + "3. 老族谱文字可能不完整或有识别错误，请尽量合理推断\n"
                + "4. 只返回 JSON，不要其他文字说明\n\n"
                + "返回格式：\n"
                + "{\"nodes\":[{\"name\":\"张三\",\"gender\":1,\"birthDate\":\"1950-01-01\",\"deathDate\":null,\"zi\":\"\",\"hao\":\"\",\"graveLocation\":\"\",\"remark\":\"\"}],\"relations\":[{\"fromName\":\"张三\",\"toName\":\"张小五\",\"relationType\":1}]}\n\n"
                + "注意：\n"
                + "- 如果某个字段信息未提及或无法识别，设为 null\n"
                + "- 亲子关系中 fromName 是父母，toName 是子女\n"
                + "- 日期格式统一为 YYYY-MM-DD，如果只有年份用 YYYY-01-01\n"
                + "- 对于无法确定的信息，宁可不填也不要编造";
    }

    // ==================== 数据构建 ====================

    private String buildTreeSummary(List<TreeNodeVO> tree) {
        if (tree == null || tree.isEmpty()) {
            return "（族谱数据为空）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("族谱共包含以下人物和关系：\n\n");
        for (TreeNodeVO root : tree) {
            appendNodeSummary(sb, root, 0);
        }
        return sb.toString();
    }

    private void appendNodeSummary(StringBuilder sb, TreeNodeVO node, int depth) {
        String indent = "  ".repeat(depth);
        String genderDesc = switch (node.getGender()) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
        sb.append(indent).append("- ").append(node.getName())
                .append("（").append(genderDesc);
        if (node.getBirthDate() != null) {
            sb.append("，生于").append(node.getBirthDate());
        }
        if (node.getDeathDate() != null) {
            sb.append("，卒于").append(node.getDeathDate());
        }
        sb.append("）\n");

        if (node.getChildren() != null) {
            for (TreeNodeVO child : node.getChildren()) {
                appendNodeSummary(sb, child, depth + 1);
            }
        }
    }

    private String buildNodeDetailInfo(FamilyNode node, List<FamilyNode> allNodes, List<FamilyRelation> allRelations) {
        StringBuilder sb = new StringBuilder();
        sb.append("人物基本信息：\n");
        sb.append("- 姓名：").append(node.getName()).append("\n");
        sb.append("- 性别：").append(switch (node.getGender()) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        }).append("\n");
        if (node.getBirthDate() != null) {
            sb.append("- 出生日期：").append(node.getBirthDate()).append("\n");
        }
        if (node.getDeathDate() != null) {
            sb.append("- 逝世日期：").append(node.getDeathDate()).append("\n");
        }
        if (node.getZi() != null && !node.getZi().isBlank()) {
            sb.append("- 字：").append(node.getZi()).append("\n");
        }
        if (node.getHao() != null && !node.getHao().isBlank()) {
            sb.append("- 号：").append(node.getHao()).append("\n");
        }
        if (node.getGraveLocation() != null && !node.getGraveLocation().isBlank()) {
            sb.append("- 墓地：").append(node.getGraveLocation()).append("\n");
        }
        if (node.getRemark() != null && !node.getRemark().isBlank()) {
            sb.append("- 备注：").append(node.getRemark()).append("\n");
        }

        // 查找关系
        sb.append("\n家族关系：\n");
        for (FamilyRelation rel : allRelations) {
            if (rel.getFromNodeId().equals(node.getId())) {
                FamilyNode other = findNodeById(allNodes, rel.getToNodeId());
                if (other != null) {
                    String relDesc = switch (rel.getRelationType()) {
                        case 1 -> "是...的父亲/母亲";
                        case 2 -> "是...的配偶";
                        case 3 -> "是...的养父/养母";
                        default -> "与...有关系";
                    };
                    sb.append("- ").append(node.getName()).append(relDesc).append("：").append(other.getName()).append("\n");
                }
            } else if (rel.getToNodeId().equals(node.getId())) {
                FamilyNode other = findNodeById(allNodes, rel.getFromNodeId());
                if (other != null) {
                    String relDesc = switch (rel.getRelationType()) {
                        case 1 -> "是...的子女";
                        case 2 -> "是...的配偶";
                        case 3 -> "是...的养子女";
                        default -> "与...有关系";
                    };
                    sb.append("- ").append(node.getName()).append(relDesc).append("：").append(other.getName()).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private FamilyNode findNodeById(List<FamilyNode> nodes, Long id) {
        for (FamilyNode n : nodes) {
            if (n.getId().equals(id)) {
                return n;
            }
        }
        return null;
    }
}
