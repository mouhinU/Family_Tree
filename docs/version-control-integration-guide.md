# 版本控制功能集成指南

## 概述

版本控制功能已实现完成,需要在现有的节点和关系操作中手动集成版本历史记录。

## 已完成的工作

### 1. 数据库层
- ✅ V22迁移脚本: `family_node_history`, `family_relation_history`, `family_snapshot` 三张表
- ✅ H2和MySQL两个版本的迁移脚本

### 2. 领域层 (Domain)
- ✅ 实体: `NodeHistory`, `RelationHistory`, `FamilySnapshot`
- ✅ 仓储接口: `HistoryRepository`, `SnapshotRepository`
- ✅ 领域服务: `VersionControlDomainService` (提供历史记录、版本对比、快照管理)

### 3. 基础设施层 (Infrastructure)
- ✅ DO实体: `NodeHistoryDO`, `RelationHistoryDO`, `FamilySnapshotDO`
- ✅ Mapper: `NodeHistoryMapper`, `RelationHistoryMapper`, `FamilySnapshotMapper`
- ✅ Converter: `NodeHistoryConverter`, `RelationHistoryConverter`, `FamilySnapshotConverter`
- ✅ Repository实现: `HistoryRepositoryImpl`, `SnapshotRepositoryImpl`

### 4. 应用层 (Application)
- ✅ `VersionControlApplicationService` 提供以下方法:
  - `getNodeHistory()` - 查询节点历史
  - `getRelationHistory()` - 查询关系历史
  - `compareNodeVersions()` - 对比节点版本
  - `rollbackNodeToVersion()` - 回滚节点版本
  - `createSnapshot()` - 创建快照
  - `listSnapshots()` - 列出快照
  - `restoreFromSnapshot()` - 从快照恢复

### 5. Web层 (Controller)
- ✅ `VersionControlController` 提供以下API:
  - `GET /api/version/node/{nodeId}/history` - 查询节点历史
  - `GET /api/version/relation/{relationId}/history` - 查询关系历史
  - `GET /api/version/node/{nodeId}/compare?v1={}&v2={}` - 对比版本
  - `POST /api/version/node/{nodeId}/rollback?versionId={}` - 回滚版本
  - `POST /api/version/snapshot` - 创建快照
  - `GET /api/version/snapshot/list` - 列出快照
  - `GET /api/version/snapshot/{snapshotId}` - 查询快照详情
  - `DELETE /api/version/snapshot/{snapshotId}` - 删除快照
  - `POST /api/version/snapshot/{snapshotId}/preview` - 预览快照恢复

## 需要手动集成的位置

### 在 FamilyNodeApplicationService 中集成

#### 1. 注入 VersionControlDomainService

```java
private final VersionControlDomainService versionControlDomainService;

public FamilyNodeApplicationService(...,
                                    VersionControlDomainService versionControlDomainService) {
    // ...
    this.versionControlDomainService = versionControlDomainService;
}
```

#### 2. 在 createNode 方法末尾添加

```java
@Transactional(rollbackFor = Exception.class)
public Long createNode(Long familyId, Long userId, NodeCreateDTO dto) {
    // ... 现有逻辑 ...

    familyNodeRepository.save(node);

    // 【新增】记录节点创建历史
    String operatorName = getOperatorName(userId); // 需要实现获取用户名的方法
    String ipAddress = getClientIpAddress(); // 需要实现获取IP的方法
    versionControlDomainService.recordNodeCreate(node, userId, operatorName, ipAddress);

    // ... 后续逻辑 ...
}
```

#### 3. 在 updateNode 方法中添加

```java
@Transactional(rollbackFor = Exception.class)
public void updateNode(Long familyId, FamilyNodeDTO dto) {
    FamilyNode existing = checkNodeBelongsToFamily(familyId, dto.getId());

    // 【新增】保存修改前的状态
    FamilyNode oldNode = cloneNode(existing); // 深拷贝

    // ... 现有更新逻辑 ...

    familyNodeRepository.update(existing);

    // 【新增】记录节点更新历史
    String operatorName = getOperatorName(userId);
    String ipAddress = getClientIpAddress();
    versionControlDomainService.recordNodeUpdate(oldNode, existing, userId, operatorName, ipAddress);
}
```

#### 4. 在 deleteNode 方法中添加

```java
@Transactional(rollbackFor = Exception.class)
public void deleteNode(Long familyId, Long nodeId) {
    FamilyNode node = checkNodeBelongsToFamily(familyId, nodeId);

    // 【新增】记录节点删除历史(在删除前)
    String operatorName = getOperatorName(userId);
    String ipAddress = getClientIpAddress();
    versionControlDomainService.recordNodeDelete(node, userId, operatorName, ipAddress);

    // ... 现有删除逻辑 ...
}
```

### 在 FamilyRelationApplicationService 中集成

类似地,在关系的增删改方法中添加:

```java
// 创建关系时
versionControlDomainService.recordRelationCreate(relation, userId, operatorName, ipAddress);

// 更新关系时
FamilyRelation oldRelation = cloneRelation(existing);
// ... 更新逻辑 ...
versionControlDomainService.recordRelationUpdate(oldRelation, newRelation, userId, operatorName, ipAddress);

// 删除关系时
versionControlDomainService.recordRelationDelete(relation, userId, operatorName, ipAddress);
```

## 辅助方法实现

### 获取操作员姓名

```java
private String getOperatorName(Long userId) {
    // 从 UserRepository 或 Session 中获取用户名
    User user = userRepository.findById(userId);
    return user != null ? user.getUsername() : "Unknown";
}
```

### 获取客户端IP

```java
private String getClientIpAddress() {
    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs != null) {
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
    return null;
}
```

### 深拷贝节点对象

```java
private FamilyNode cloneNode(FamilyNode source) {
    FamilyNode copy = new FamilyNode();
    copy.setId(source.getId());
    copy.setUserId(source.getUserId());
    copy.setFamilyId(source.getFamilyId());
    copy.setName(source.getName());
    copy.setGender(source.getGender());
    copy.setBirthDate(source.getBirthDate());
    copy.setDeathDate(source.getDeathDate());
    copy.setGeneration(source.getGeneration());
    copy.setBirthOrder(source.getBirthOrder());
    copy.setColorLabel(source.getColorLabel());
    copy.setAvatar(source.getAvatar());
    copy.setRemark(source.getRemark());
    copy.setLunarBirthDate(source.getLunarBirthDate());
    copy.setLunarDeathDate(source.getLunarDeathDate());
    copy.setZi(source.getZi());
    copy.setHao(source.getHao());
    copy.setHui(source.getHui());
    copy.setGraveLocation(source.getGraveLocation());
    copy.setSpouseName(source.getSpouseName());
    copy.setSpouseOriginFamily(source.getSpouseOriginFamily());
    copy.setCreateTime(source.getCreateTime());
    copy.setUpdateTime(source.getUpdateTime());
    return copy;
}
```

## 测试建议

1. **单元测试**: 测试 `VersionControlDomainService` 的各个方法
2. **集成测试**: 测试完整的API调用流程
3. **性能测试**: 确保版本记录不会显著影响系统性能

## 注意事项

1. **事务一致性**: 版本历史记录操作应在同一事务中,确保原子性
2. **性能考虑**: 大量写入时注意JSON序列化的性能开销
3. **数据清理**: 定期归档或删除过旧的版本记录,避免数据库膨胀
4. **权限控制**: 只有族长和管理员才能查看历史和恢复快照

## 前端集成提示

前端需要实现的UI组件:
1. 节点右键菜单 → "查看历史"
2. 历史时间线展示组件
3. 版本对比界面(高亮差异)
4. 快照管理页面
5. 恢复确认弹窗

后端API已全部就绪,可直接调用。
