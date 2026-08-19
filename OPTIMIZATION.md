## Family_Tree 系统优化建议

基于对项目全部模块（前端 8 个 JS 文件 + 2619 行 CSS、后端 14 个 Controller + 10 个 Service、15 个 Flyway 迁移、3 个测试类）的完整审查，按优先级整理如下。

---

### 一、安全性增强

**1. ~~CSP 策略收紧~~ ✅ 已完成**

已从 `script-src` 中移除 `'unsafe-inline'`。所有弹框中的 inline `onclick` 处理器（共 21 处）替换为 `data-close-modal` 属性 + `addEventListener` 事件委托。CSP 策略现为：`script-src 'self' https://cdnjs.cloudflare.com`，`style-src` 保留 `'unsafe-inline'`（D3.js 动态设置 SVG 内联样式所需）。

**2. ~~写接口限流~~ ✅ 已完成**

新增 `RateLimitInterceptor`，基于 Caffeine 滑动窗口计数器，按用户 ID 限流。仅对 POST/PUT/DELETE 请求生效，窗口 60 秒内最多 30 次写操作，超限返回 HTTP 429 + `Retry-After` 响应头。已在 `WebMvcConfig` 中注册，位于登录认证拦截器之后。

**3. H2 Console 密码保护**

`application-dev.yml` 中 H2 Console 启用且数据库密码为空。虽然仅在 dev profile 下生效，但如果误部署到生产环境会直接暴露数据库。建议在 dev 配置中为 H2 Console 添加 `spring.h2.console.settings.web-allow-others: false`，并考虑设置一个访问密码。

---

### 二、性能优化

**4. ~~`syncDescendantGenerations` N+1 查询~~ ✅ 已完成**

重写 BFS 逻辑：在遍历前一次性加载该家族所有 PARENT_CHILD 和 SPOUSE 类型的关系到内存，构建两张邻接表（`childrenMap` 和 `spouseMap`），BFS 过程中直接查表，最后批量执行 UPDATE。原来 O(N) 次 SELECT 降为固定 2 次 SELECT。

**5. ~~时间线接口分页~~ ✅ 已完成**

`TimelineController` 新增 `page`（默认 1）和 `size`（默认 50）请求参数。事件列表在内存排序后通过 `subList` 分页返回，响应包含 `records`、`total`、`page`、`size`、`totalPages` 字段。`year` 过滤参数仍在分页前生效。使用已有的 `PageResult` DTO（已扩展 `totalPages` 字段）。

**6. 关系路径分析可缓存**

`RelationPathController` 每次调用都全量加载关系和节点数据做 BFS。同一个家族在短时间内多次分析不同节点对的关系路径时，图结构是相同的。可以给 `listAllRelations` 的结果加一个短 TTL 缓存（比如 30 秒），或者复用 `FamilyTreeService` 已有的 Caffeine 缓存中的树结构来构建邻接表。

**7. ~~搜索查询优化~~ ✅ 已完成**

搜索范围从仅 `name` 扩展至 `name`、`zi`（字）、`hao`（号）、`hui`（讳）四个字段的 OR 模糊匹配。新增 Flyway V16 迁移：H2 添加 `(family_id, name)` 复合索引；MySQL 额外添加 `FULLTEXT INDEX ft_node_search (name, zi, hao, hui) WITH PARSER ngram` 全文索引。前端搜索结果新增传统名字副标题展示（讳/字/号）。

**8. ~~PDF 导出内存控制~~ ✅ 已完成**

Canvas 最大尺寸从 8000px 降至 6000px。导出完成后显式释放中间对象引用（`img.src = ''`、`canvas.width = canvas.height = 0`）。

---

### 三、前端架构优化

**9. ~~modals.js 拆分~~ ✅ 已完成**

原 1709 行的 `modals.js` 已拆分为 5 个模块：

| 文件 | 行数 | 职责 |
|------|------|------|
| `modals.js` | 35 | 基座：`showModal` / `closeModal` + `data-close-modal` 事件委托 |
| `modal-node.js` | 906 | 节点 CRUD：新增/编辑/删除/排次/辈分/详情/颜色/关联配偶/过继 |
| `modal-family.js` | 347 | 家族管理：成员管理/个人信息/标记为我/切换家族 |
| `modal-offering.js` | 217 | 祭奠面板：敬献/动效/面板构建 |
| `modal-tools.js` | 266 | 工具弹框：操作日志/时间线/关系路径/忌日提醒 |

各模块通过 `FT` 命名空间通信，`showModal`/`closeModal` 由基座模块导出。

**10. ~~前端错误处理增强~~ ✅ 已完成**

`api.js` 的 `api()` 函数已增强：
- 网络异常：`fetch` 包裹 try/catch，提示"网络连接失败"
- 401：提示"登录已过期"后跳转登录页
- 403：提示"安全校验失败"后跳转登录页
- 429：读取 `Retry-After` 头，提示"操作过于频繁"
- 5xx/其他：解析 JSON 错误消息或显示通用提示，`FT.toast` 展示

**11. CSS 变量主题化**

`style.css` 已经使用了 CSS 变量（`--paper`、`--ink`、`--cinnabar` 等），但部分颜色值仍然硬编码在动画 `@keyframes` 和渐变中。建议将所有颜色值统一收敛到变量，这样未来如果要支持暗色主题或节日皮肤，只需覆盖一组变量即可。

---

### 四、测试覆盖

**12. ~~核心 Service 单元测试~~ ✅ 已完成**

新增 `FamilyNodeServiceImplTest`（8 个测试用例），覆盖：
- 节点创建（含亲子关联、辈分计算）
- 名称超长校验
- 生卒日期校验
- 辈分同步（BFS 后代更新）
- 节点删除（含关系清理）
- 搜索（空关键字、有结果）
- 批量颜色更新（验证非 N+1）

**13. Controller 层集成测试**

目前没有任何 Controller 测试。建议至少为关键接口编写 `@WebMvcTest` 切片测试，验证参数校验、权限拦截、CSRF 校验等行为。这也能作为接口契约文档，防止后续改动破坏 API 兼容性。

---

### 五、可观测性

**14. 操作日志增强**

当前 `OperationLogService` 仅记录基本的操作类型和操作人。建议增加：请求耗时（方便定位慢接口）、客户端 IP（已有 `getClientIp` 工具但未在日志中使用）、操作前后的关键数据快照（比如修改节点时记录修改前后的名字）。这些信息对问题排查和安全审计很有价值。

**15. ~~Actuator 指标暴露~~ ✅ 已完成**

已暴露 `metrics` 和 `prometheus` 端点，配合 Prometheus + Grafana 可监控 JVM 内存、HTTP 请求延迟、数据库连接池等。Caffeine 缓存已启用 `.recordStats()`，可通过 `/actuator/metrics/cache` 查看命中率。新增自定义指标 `family.tree.build`（族谱树构建次数计数器），缓存未命中时自增。

---

### 六、用户体验细节

**16. 搜索体验优化**

当前搜索有 300ms 防抖和下拉列表，但搜索结果仅显示名字和辈分。建议增加：显示世代数（"第 X 世"）帮助区分同名族人；搜索无结果时给出友好提示而非空列表；支持按世代或颜色标签筛选搜索结果。

**17. ~~导出进度反馈~~ ✅ 已完成**

新增 `createProgressOverlay()` 函数，导出时显示全屏半透明蒙层 + 居中卡片，按阶段更新提示文案（"正在渲染族谱…" → "正在生成图片…" → "正在生成 PDF…"），含 CSS 脉冲动效。导出完成或失败后自动移除蒙层。

**18. 右键菜单可访问性**

当前右键菜单通过鼠标触发，键盘用户无法使用。建议为节点增加 `tabindex` 属性，支持 Tab 键聚焦后按 Enter/Space 弹出操作菜单，同时添加适当的 ARIA 角色标注。

---

### 优先级建议

| 优先级 | 编号 | 改动量 | 收益 | 状态 |
|--------|------|--------|------|------|
| P0 紧急 | #2 写接口限流 | 小 | 防滥用 | ✅ 已完成 |
| P0 紧急 | #10 前端错误处理 | 小 | 用户体验 | ✅ 已完成 |
| P1 重要 | #1 CSP unsafe-inline 移除 | 中 | 安全性 | ✅ 已完成 |
| P1 重要 | #4 N+1 查询修复 | 中 | 性能 | ✅ 已完成 |
| P1 重要 | #12 核心测试补充 | 中 | 质量保障 | ✅ 已完成 |
| P2 改善 | #5 时间线分页 | 中 | 性能 | ✅ 已完成 |
| P2 改善 | #9 modals.js 拆分 | 大 | 可维护性 | ✅ 已完成 |
| P2 改善 | #17 导出进度 | 小 | 用户体验 | ✅ 已完成 |
| P3 远期 | #7 全文搜索 | 大 | 搜索质量 | ✅ 已完成 |
| P3 远期 | #15 监控指标 | 中 | 运维能力 | ✅ 已完成 |
