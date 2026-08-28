# 版本控制功能 - 快速启动指南

## 1. 数据库迁移

### H2数据库(开发环境)
Flyway会自动执行 `V22__add_version_control.sql`,无需手动操作。

### MySQL数据库(生产环境)
确保MySQL用户有CREATE TABLE权限,Flyway会在应用启动时自动执行迁移。

**验证迁移成功:**
```sql
-- 检查表是否创建成功
SHOW TABLES LIKE '%history%';
SHOW TABLES LIKE '%snapshot%';

-- 应该看到:
-- family_node_history
-- family_relation_history
-- family_snapshot
```

## 2. 启动应用

```bash
# 编译项目
mvn clean install -DskipTests

# 启动应用
mvn spring-boot:run -pl family-tree-web

# 或使用IDE直接运行 FamilyTreeApplication
```

## 3. 测试API接口

### 3.1 查询节点历史(需要先有节点数据)

```bash
# 假设已有节点ID=1,家族ID=1
curl -X GET "http://localhost:8080/api/version/node/1/history?page=1&size=10" \
  -H "Cookie: JSESSIONID=your_session_id"
```

**预期响应:**
```json
{
  "code": 200,
  "data": {
    "list": [],
    "total": 0,
    "page": 1,
    "size": 10
  }
}
```

### 3.2 创建快照

```bash
curl -X POST "http://localhost:8080/api/version/snapshot?snapshotName=测试快照&description=第一次测试" \
  -H "Cookie: JSESSIONID=your_session_id"
```

**预期响应:**
```json
{
  "code": 200,
  "data": {
    "snapshotId": 1,
    "createTime": "2026-08-28T10:00:00"
  }
}
```

### 3.3 列出所有快照

```bash
curl -X GET "http://localhost:8080/api/version/snapshot/list" \
  -H "Cookie: JSESSIONID=your_session_id"
```

**预期响应:**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "familyId": 1,
      "snapshotName": "测试快照",
      "description": "第一次测试",
      "creatorName": "admin",
      "nodeCount": 5,
      "relationCount": 4,
      "createTime": "2026-08-28T10:00:00"
    }
  ]
}
```

## 4. 集成版本记录到现有代码

### 示例:在FamilyNodeApplicationService中添加历史记录

#### Step 1: 注入依赖

```java
@Service
public class FamilyNodeApplicationService {

    private final VersionControlDomainService versionControlDomainService;

    public FamilyNodeApplicationService(...,
                                        VersionControlDomainService versionControlDomainService) {
        // ... 其他赋值
        this.versionControlDomainService = versionControlDomainService;
    }

    // 辅助方法
    private String getOperatorName(Long userId) {
        // TODO: 从UserRepository或Session获取用户名
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
}
```

#### Step 2: 在createNode方法末尾添加

找到 `createNode` 方法的这一行:
```java
familyNodeRepository.save(node);
logger.info("Created family node id={} name={} for family={} by user={}",
        node.getId(), node.getName(), familyId, userId);
```

在其后添加:
```java
// 记录节点创建历史
String operatorName = getOperatorName(userId);
String ipAddress = getClientIpAddress();
versionControlDomainService.recordNodeCreate(node, userId, operatorName, ipAddress);
```

#### Step 3: 在updateNode方法中添加

在方法开头保存旧状态:
```java
@Transactional(rollbackFor = Exception.class)
public void updateNode(Long familyId, FamilyNodeDTO dto) {
    FamilyNode existing = checkNodeBelongsToFamily(familyId, dto.getId());

    // 保存修改前的状态(深拷贝)
    FamilyNode oldNode = cloneNode(existing);

    // ... 现有更新逻辑 ...

    familyNodeRepository.update(existing);

    // 记录节点更新历史
    Long userId = getCurrentUserId(); // 需要实现
    String operatorName = getOperatorName(userId);
    String ipAddress = getClientIpAddress();
    versionControlDomainService.recordNodeUpdate(oldNode, existing, userId, operatorName, ipAddress);
}
```

#### Step 4: 在deleteNode方法中添加

```java
@Transactional(rollbackFor = Exception.class)
public void deleteNode(Long familyId, Long nodeId) {
    FamilyNode node = checkNodeBelongsToFamily(familyId, nodeId);

    // 记录节点删除历史(在删除前)
    Long userId = getCurrentUserId();
    String operatorName = getOperatorName(userId);
    String ipAddress = getClientIpAddress();
    versionControlDomainService.recordNodeDelete(node, userId, operatorName, ipAddress);

    // ... 现有删除逻辑 ...
}
```

#### Step 5: 实现cloneNode辅助方法

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

## 5. 验证版本记录生效

### 测试流程:

1. **创建一个节点**
   ```bash
   curl -X POST "http://localhost:8080/api/node" \
     -H "Content-Type: application/json" \
     -H "Cookie: JSESSIONID=your_session_id" \
     -d '{
       "name": "张三",
       "gender": 1,
       "birthDate": "1990-01-01"
     }'
   ```

2. **查询该节点的历史记录**
   ```bash
   curl -X GET "http://localhost:8080/api/version/node/{nodeId}/history?page=1&size=10" \
     -H "Cookie: JSESSIONID=your_session_id"
   ```

   **应该看到:**
   ```json
   {
     "list": [
       {
         "operationType": "CREATE",
         "operatorName": "admin",
         "changeSummary": "创建节点: 张三",
         "versionNumber": 1,
         "createTime": "2026-08-28T10:00:00"
       }
     ],
     "total": 1
   }
   ```

3. **更新节点**
   ```bash
   curl -X PUT "http://localhost:8080/api/node" \
     -H "Content-Type: application/json" \
     -H "Cookie: JSESSIONID=your_session_id" \
     -d '{
       "id": {nodeId},
       "name": "李四",
       "birthDate": "1990-02-02"
     }'
   ```

4. **再次查询历史**
   应该看到两条记录(CREATE和UPDATE)。

5. **对比两个版本**
   ```bash
   curl -X GET "http://localhost:8080/api/version/node/{nodeId}/compare?v1=1&v2=2" \
     -H "Cookie: JSESSIONID=your_session_id"
   ```

   **应该看到差异:**
   ```json
   {
     "name": ["张三", "李四"],
     "birthDate": ["1990-01-01", "1990-02-02"]
   }
   ```

## 6. 常见问题

### Q1: 启动时报错 "Table 'family_node_history' doesn't exist"
**A**: Flyway迁移未执行。检查:
- `application.yml` 中Flyway配置是否正确
- 数据库用户是否有CREATE TABLE权限
- 手动执行V22迁移脚本

### Q2: 查询历史返回空列表
**A**: 可能原因:
- 未在代码中集成版本记录调用
- 节点ID或家族ID不正确
- 检查日志是否有异常

### Q3: 版本对比失败
**A**: 检查:
- beforeData和afterData是否为NULL
- JSON格式是否正确
- 查看日志中的详细错误信息

### Q4: 快照数据太大导致插入失败
**A**: 
- MySQL: 确保字段类型为MEDIUMTEXT(最大16MB)
- 考虑压缩JSON数据后再存储
- 限制单个家族的节点数量

## 7. 性能优化建议

如果写入性能受影响:

### 方案1: 异步记录历史
```java
@EventListener
@Async
public void onNodeCreated(NodeCreatedEvent event) {
    // 异步记录历史
}
```

### 方案2: 批量写入
使用Spring Batch定期批量插入历史记录。

### 方案3: 定期清理
```java
@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
public void cleanupOldHistory() {
    // 保留最近100个版本,删除更早的记录
}
```

## 8. 下一步

✅ 后端核心功能已完成
⏳ 集成到现有业务代码(参考上述Step 1-5)
⏳ 前端UI实现(参考feature-summary.md中的建议)
⏳ 编写单元测试
⏳ 性能测试与优化

---

**文档索引:**
- 完整功能说明: `docs/version-control-feature-summary.md`
- 集成详细指南: `docs/version-control-integration-guide.md`
- 本快速启动: `docs/version-control-quickstart.md`
