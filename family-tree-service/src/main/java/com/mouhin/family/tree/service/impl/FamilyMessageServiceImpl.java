package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.common.dto.MessageCreateDTO;
import com.mouhin.family.tree.common.dto.MessageVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.FamilyMessageDO;
import com.mouhin.family.tree.persistence.mapper.FamilyMessageMapper;
import com.mouhin.family.tree.service.FamilyMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 家族留言服务实现
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyMessageServiceImpl implements FamilyMessageService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyMessageServiceImpl.class);

    private static final int MAX_CONTENT_LENGTH = 500;

    private final FamilyMessageMapper messageMapper;

    public FamilyMessageServiceImpl(FamilyMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postMessage(Long familyId, Long userId, String username, MessageCreateDTO dto) {
        String content = dto.getContent();
        if (content == null || content.isBlank()) {
            throw new BusinessException("留言内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("留言内容不能超过" + MAX_CONTENT_LENGTH + "字");
        }

        FamilyMessageDO message = new FamilyMessageDO();
        message.setFamilyId(familyId);
        message.setUserId(userId);
        message.setUsername(username != null ? username : "匿名");
        message.setContent(content.trim());
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());

        messageMapper.insert(message);
        logger.info("用户 {} 在家族 {} 发布留言: id={}", userId, familyId, message.getId());
    }

    @Override
    public PageResult<MessageVO> listMessages(Long familyId, Long currentUserId, int page, int size) {
        // 查询总数
        LambdaQueryWrapper<FamilyMessageDO> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(FamilyMessageDO::getFamilyId, familyId);
        long total = messageMapper.selectCount(countWrapper);

        if (total == 0) {
            return new PageResult<>(List.of(), 0L, page, size);
        }

        // 分页查询（按创建时间倒序）
        LambdaQueryWrapper<FamilyMessageDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FamilyMessageDO::getFamilyId, familyId)
                .orderByDesc(FamilyMessageDO::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size);

        List<FamilyMessageDO> messages = messageMapper.selectList(queryWrapper);

        // DO -> VO
        List<MessageVO> voList = messages.stream().map(msg -> {
            MessageVO vo = new MessageVO();
            vo.setId(msg.getId());
            vo.setUserId(msg.getUserId());
            vo.setUsername(msg.getUsername());
            vo.setContent(msg.getContent());
            vo.setCreateTime(msg.getCreateTime());
            vo.setOwn(Objects.equals(msg.getUserId(), currentUserId));
            return vo;
        }).toList();

        return new PageResult<>(voList, total, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long messageId, Long userId) {
        FamilyMessageDO message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException("留言不存在");
        }
        if (!Objects.equals(message.getUserId(), userId)) {
            throw new BusinessException("只能删除自己的留言");
        }

        messageMapper.deleteById(messageId);
        logger.info("用户 {} 删除留言: id={}", userId, messageId);
    }
}
