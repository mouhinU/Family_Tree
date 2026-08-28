# 版本控制功能 - 集成检查清单

## ✅ 已完成的工作(无需再做)

### 数据库层
- [x] H2迁移脚本: `V22__add_version_control.sql`
- [x] MySQL迁移脚本: `V22__add_version_control.sql`
- [x] 三张表: `family_node_history`, `family_relation_history`, `family_snapshot`
- [x] 索引优化: nodeId, familyId, operatorId, createTime

### 领域层 (Domain)
- [x] 实体类: `NodeHistory`, `RelationHistory`, `FamilySnapshot`
- [x] 仓储接口: `HistoryRepository`, `SnapshotRepository`
- [x] 领域服务: `VersionControlDomainService`
  - 记录节点增删改历史
  - 记录关系增删改历史
  - 版本差异对比
  - 快照创建与恢复

### 基础设施层 (Infrastructure)
- [x] DO实体: `NodeHistoryDO`, `RelationHistoryDO`, `FamilySnapshotDO`
- [x] Mapper接口: `NodeHistoryMapper`, `RelationHistoryMapper`, `FamilySnapshotMapper`
- [x] Converter: `NodeHistoryConverter`, `RelationHistoryConverter`, `FamilySnapshotConverter`
- [x] Repository实现: `HistoryRepositoryImpl`, `SnapshotRepositoryImpl`

### 应用层 (Application)
- [x] `VersionControlApplicationService`
  - 10个公共方法(查询、对比、回滚、快照管理)

### Web层 (Controller)
- [x] `VersionControlController`
  - 10个RESTful API端点
  - 统一的权限控制
  - 标准的Result封装

### 文档
- [x] 功能总结文档
- [x] 集成指南
- [x] 快速启动指南
- [x] README索引
- [x] 自动化测试脚本

---

## ⏳ 需要手动集成的部分

### 优先级: 🔴 高 (必须完成)

#### 1. FamilyNodeApplicationService 集成

**位置**: `family-tree-application/src/main/java/com/mouhin/family/tree/application/service/FamilyNodeApplicationService.java`

##### Step 1: 添加依赖注入
```java
// 在构造函数参数中添加
private final VersionControlDomainService versionControlDomainService;

public FamilyNodeApplicationService(...,
                                    VersionControlDomainService versionControlDomainService) {
    // ... 其他赋值
    this.versionControlDomainService = versionControlDomainService;
}
```

##### Step 2: 添加辅助方法
```java
// 在类的末尾添加以下私有方法

private String getOperatorName(Long userId) {
    // TODO: 从UserRepository或Session获取
    return "User_" + userId;
}

private String getClientIpAddress() {
    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs != null) {
        HttpServletRequest request = attrs.getRequest();
        return request.getRemoteAddr();
    }
    return null;
}

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

##### Step 3: 在 createNode 方法中添加
**找到这一行**(约第145行):
```java
familyNodeRepository.save(node);
logger.info("Created family node id={} name={} for family={} by user={}",
        node.getId(), node.getName(), familyId, userId);
```

**在其后添加**:
```java
// 记录节点创建历史
String operatorName = getOperatorName(userId);
String ipAddress = getClientIpAddress();
versionControlDomainService.recordNodeCreate(node, userId, operatorName, ipAddress);
```

##### Step 4: 在 updateNode 方法中添加
**在方法开头**(获取existing节点后):
```java
FamilyNode existing = checkNodeBelongsToFamily(familyId, dto.getId());

// 【新增】保存修改前的状态
FamilyNode oldNode = cloneNode(existing);
```

**在方法末尾**(更新操作后):
```java
familyNodeRepository.update(existing);

// 【新增】记录节点更新历史
String operatorName = getOperatorName(userId);
String ipAddress = getClientIpAddress();
versionControlDomainService.recordNodeUpdate(oldNode, existing, userId, operatorName, ipAddress);
```

##### Step 5: 在 deleteNode 方法中添加
**在删除操作前**:
```java
FamilyNode node = checkNodeBelongsToFamily(familyId, nodeId);

// 【新增】记录节点删除历史(在删除前)
String operatorName = getOperatorName(userId);
String ipAddress = getClientIpAddress();
versionControlDomainService.recordNodeDelete(node, userId, operatorName, ipAddress);

// ... 现有删除逻辑
```

---

#### 2. FamilyRelationApplicationService 集成

**位置**: `family-tree-application/src/main/java/com/mouhin/family/tree/application/service/FamilyRelationApplicationService.java`

类似地,在关系的增删改方法中添加:

```java
// 注入依赖
private final VersionControlDomainService versionControlDomainService;

// 辅助方法
private String getOperatorName(Long userId) { /* 同上 */ }
private String getClientIpAddress() { /* 同上 */ }
private FamilyRelation cloneRelation(FamilyRelation source) { /* 深拷贝 */ }

// 在createRelation方法末尾
versionControlDomainService.recordRelationCreate(relation, userId, operatorName, ipAddress);

// 在updateRelation方法中
FamilyRelation oldRelation = cloneRelation(existing);
// ... 更新逻辑 ...
versionControlDomainService.recordRelationUpdate(oldRelation, newRelation, userId, operatorName, ipAddress);

// 在deleteRelation方法中(删除前)
versionControlDomainService.recordRelationDelete(relation, userId, operatorName, ipAddress);
```

---

### 优先级: 🟡 中 (建议完成)

#### 3. 增强OperationLog记录

**可选**: 在operation_log表中增加before_data和after_data字段,存储JSON格式的修改前后数据。

**好处**: 操作日志和版本历史可以互相印证,提供更完整的审计追踪。

---

### 优先级: 🟢 低 (锦上添花)

#### 4. 前端UI实现

参考 `version-control-feature-summary.md` 中的"前端实现建议"章节。

**核心组件**:
- 历史时间线查看器
- 版本对比界面
- 快照管理页面

---

## 🧪 验证清单

集成完成后,逐项测试:

### 基础功能测试
- [ ] 启动应用无报错
- [ ] 数据库表自动创建成功
- [ ] 调用 `/api/version/snapshot/list` 返回空列表(无报错)

### 节点历史测试
- [ ] 创建一个新节点
- [ ] 查询该节点历史,看到CREATE记录
- [ ] 更新节点信息
- [ ] 再次查询历史,看到UPDATE记录
- [ ] 删除节点
- [ ] 查询历史,看到DELETE记录

### 版本对比测试
- [ ] 对比两个不同版本,返回字段差异
- [ ] 对比不存在的版本,返回错误提示

### 快照测试
- [ ] 创建快照,返回snapshotId
- [ ] 列出快照,看到刚创建的快照
- [ ] 查询快照详情,数据完整
- [ ] 预览快照恢复,返回节点和关系数据
- [ ] 删除快照,再次列表查询为空

### 性能测试
- [ ] 100个节点的家族,创建快照耗时 < 2秒
- [ ] 连续创建10个版本记录,无明显延迟
- [ ] 查询历史记录(分页),响应时间 < 500ms

---

## 📝 集成进度跟踪

| 任务 | 负责人 | 状态 | 完成日期 | 备注 |
|------|--------|------|----------|------|
| FamilyNodeApplicationService集成 | | ⏳ 待开始 | | |
| FamilyRelationApplicationService集成 | | ⏳ 待开始 | | |
| 单元测试编写 | | ⏳ 待开始 | | |
| 前端历史UI | | ⏳ 待开始 | | |
| 前端版本对比UI | | ⏳ 待开始 | | |
| 前端快照管理UI | | ⏳ 待开始 | | |
| 集成测试 | | ⏳ 待开始 | | |
| 性能测试 | | ⏳ 待开始 | | |

---

## 🆘 遇到问题?

1. **编译错误**: 检查import语句,确保引入了VersionControlDomainService
2. **运行时错误**: 查看日志,确认Flyway迁移是否成功执行
3. **API返回空**: 确认是否正确调用了recordXXX方法
4. **性能问题**: 参考quickstart.md中的性能优化建议

**详细排查步骤**: `version-control-quickstart.md` → "常见问题"章节

---

**最后更新**: 2026-08-28
**维护者**: Family-Tree Team
