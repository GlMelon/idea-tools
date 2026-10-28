# MyBatisCodeHelper-Pro 功能对比与迁移清单

> 对比基准：MyBatisCodeHelper-Pro `3.6.7+2321`（2023.3-2026.2 obfuscated 版）
> 本插件：Easy Tools `1.0.1+`（含 2026-10 新增的 MyBatis 结构引用、补全与一致性检测）
> 整理日期：2026-10-28

## 一、本插件已有的 MyBatis 能力（对比前提）

- Mapper 接口 ↔ XML 双向导航（行标记）
- 接口方法名 Ctrl+B / Find Usages 直接定位 XML 语句，重命名双向同步
- XML 结构属性引用与补全：`namespace`、语句 `id`、`resultMap`/`extends`、`include refid`（含 namespace 前缀跨文件）、`parameterType`/`resultType`/`type`（含内置别名）
- `#{}` / `${}` / `if test` / `foreach collection` / `bind` 参数引用跳转与链式字段补全（支持泛型、@Param、parameterType）
- 一致性检测：namespace 未解析（ERROR）、XML id 无对应方法（WARNING）、Mapper 方法缺 SQL 语句（WARNING，含生成标签 QuickFix）
- select/insert/update/delete 标签生成（意图 + 右键菜单，Velocity 模板）
- Java 侧 Mapper 增强（2026-10-28 新增）：一键添加 @Param、未使用方法检测、resultType 匹配检测、@MapperScan 识别与 Spring 注入报红抑制、MyBatis-Plus/Flex/tk-mapper 兼容（BaseMapper 免注解识别与语句豁免 + QueryWrapper 列名补全）、*Provider 注解跳转、注解 SQL 迁移 XML
- MyBatis XML 语法高亮、Mapper XML 缓存服务

## 二、差异清单（Pro 有、本插件没有）

### 1. SQL 编辑体验类（XML 内）

> 状态标记说明：✅ 已完成（2026-10-28，详见「五、SQL 编辑体验类（#1-11）迁移完成情况」）

| # | 状态 | 功能 | Pro 的实现方式 | 迁移价值 | 难度 |
|---|------|------|--------------|---------|------|
| 1 | ✅ | SQL 关键字/表名/列名补全与检测 | 依赖 IDE Database 数据源，向 SQL 注入语言注册补全 + SQL inspection 检查表/列是否存在、jdbcType 匹配 | ★★★★★（Pro 核心卖点） | 高（需可选依赖 `com.intellij.database`，仅 IU 可用） |
| 2 | ✅ | resultMap 的 property/column 补全与检测 | property 对照 Java 字段、column 对照数据库列，未映射列一键补充、重复列检测 | ★★★★ | 中（property 部分可复用现有字段解析；column 部分依赖数据源） |
| 3 | ✅ | OGNL 独立语言 | `if test` 等注册为 MybatisOgnl 语言：高亮、格式化、括号匹配、拼写检查、字符串比较检测（`'a'=='b'` 误用） | ★★★ | 高（自定义语言工作量大） |
| 4 | ✅ | `#{}` Param 独立语言 | `#{param, jdbcType=...}` 内的属性补全 + 参数检测 | ★★★ | 高（同上） |
| 5 | ✅ | XML 格式化兼容动态标签 | 自定义 formatter，格式化不破坏 where/if/foreach 缩进 | ★★★ | 中 |
| 6 | ✅ | XML 代码折叠 | foldingBuilder（长 SQL 折叠） | ★★ | 低 |
| 7 | ✅ | Live Templates | 5 组模板：SQL 语句、注释、SQL 导航、OGNL 快捷输入 | ★★★★ | **低（纯 XML 资源，最划算的迁移项）** |
| 8 | ✅ | XML 内直接运行 SQL | runLineMarkerContributor，行标记点击执行 | ★★★ | 中（依赖数据源） |
| 9 | ✅ | 提取 SQL 片段 | `Ctrl+Alt+C` 把选中文本抽成 `<sql>` + `<include>` | ★★★ | 低 |
| 10 | ✅ | Mapper XML 文件专属图标 | iconProvider | ★ | 低 |
| 11 | ✅ | 拼写检查抑制 / inspection 抑制 | spellchecker.support、inspectionSuppressor | ★ | 低 |

### 2. Java 侧 Mapper 增强

> 状态标记说明：✅ 已完成（2026-10-28，详见「六、Java 侧 Mapper 增强（#12-21）迁移完成情况」）；❌ 不迁移

| # | 状态 | 功能 | 说明 | 迁移价值 | 难度 |
|---|------|------|------|---------|------|
| 12 | ❌ 不迁移 | 方法名生成 SQL | `selectByNameAndAge` 这类 JPA 风格方法名自动生成单表 SQL（Pro 的招牌功能） | ★★★★★ | 高（解析方法名生成语句，规则多） |
| 13 | ❌ 不迁移 | Mapper 变量调用处补全 | 输入 `mapper.` 即提示方法名并可直接生成代码 | ★★★ | 中高 |
| 14 | ✅ | 一键添加 @Param | 意图操作为方法参数批量补 @Param | ★★★ | 低（可复用现有工具方法） |
| 15 | ❌ 不迁移 | 从方法生成测试用例 | 生成可运行的 mapper 测试（连库跑 SQL） | ★★★ | 高 |
| 16 | ✅ | 未使用方法检测 | unUsedMethod inspection | ★★ | 低 |
| 17 | ✅ | resultType 与返回值匹配检测 | MybatisResultTypeCheck | ★★★ | 中（复用现有解析） |
| 18 | ✅ | Spring 集成 | 识别 @MapperScan，mapper 注入不报红 | ★★★★ | 高（需可选依赖 `com.intellij.spring`，IU） |
| 19 | ❌ 不迁移 | Kotlin 支持 | kotlin 补全/检测 | ★ | 中 |
| 20 | ✅ | MyBatis-Plus / Flex / tk-mapper 兼容 | BaseMapper 方法识别、QueryWrapper 生成 | ★★★★ | 中（至少应做 BaseMapper 语句豁免，否则"方法缺 SQL"检测会对 MP 项目误报） |
| 21 | ✅ | 方法级 SQL 注解支持 | @SelectProvider 跳转、注解 SQL 迁移 XML | ★★ | 中 |

> 不迁移决策说明（2026-10-28）：
> - **#12 方法名生成 SQL / #13 调用处补全生成**：与本插件现有"select/insert/update/delete 标签生成（意图+模板）"及 AI 代码生成路线重叠，独立实现价值有限，且规则维护成本高；
> - **#15 测试用例生成**：需连库运行，依赖数据源与运行时环境，超出静态增强定位；
> - **#19 Kotlin 支持**：本插件 Java-only 定位，Kotlin 项目建议使用官方 IDE 插件。

### 3. 代码生成类（与现有 AI 路线重叠，按需取舍）

| # | 功能 | 说明 | 迁移价值 | 难度 |
|---|------|------|---------|------|
| 22 | 数据库表生成 CRUD（模板化、重跑合并） | 本插件已有 AI 生成路线（GenerateCodeByAiAction），路线不同 | ★★（视定位） | 高 |
| 23 | 多表 Join 生成 | 生成 join 语句 + 关联 resultMap + columnList | ★★ | 高 |
| 24 | SQL ⇄ XML/Java 互转 | SQL 粘贴转 XML+接口方法；XML 生成 Java 类+resultMap | ★★★★ | 中 |
| 25 | 从 Java 类生成建表语句（支持批量） | | ★★★ | 中 |
| 26 | Add All Column SQL / All Column Like | 快速生成列清单/模糊查询 | ★★ | 低 |
| 27 | Add MyBatis Support 脚手架 | 一键配依赖、mapper-locations、typeAliases、可运行测试 | ★★★ | 中 |
| 28 | 代码生成预览 / service 模板自定义 | | ★ | 中 |

### 4. 工具与生态类

| # | 功能 | 说明 | 迁移价值 | 难度 |
|---|------|------|---------|------|
| 29 | MyBatis SQL 日志还原（batslog） | 控制台 `? ` 参数日志还原成完整 SQL，可复制/执行 | ★★★★ | 中（独立功能，与现有代码不冲突） |
| 30 | MCP Server | `run_mybatis_sql`/`validate_mybatis_mapper` 等，AI 工具联动 | ★★★（本插件有 LLM 基础设施） | 高 |
| 31 | 自定义 AI Prompt 模板（表右键） | `{{tableName}}` 变量系统 | ★★ | 中 |
| 32 | TypeAlias 别名体系解析 | typeAliasesPackage 配置解析，resultType 支持项目别名 | ★★★ | 中 |
| 33 | 大量细节配置项 | useActualParam、忽略特定参数解析等 | ★★ | 低 |
| 34 | 数据源工具窗口 | | ★ | 中 |

## 三、本插件独有、Pro 没有的（迁移时保留）

- AI JavaDoc/注释生成（多 LLM 提供商配置：OpenAI、Claude、Gemini、DeepSeek、Qwen、GLM、Ollama 等）
- Spring MVC API 扫描 / 搜索面板（Ctrl+\）
- 项目视图注释装饰器
- 属性命名转换（Ctrl+Shift+U）
- LLM 驱动的数据库表结构代码生成

## 四、建议迁移优先级

### 第一批（低成本高回报，1-2 天级别）

- ✅ #7 Live Templates（纯资源）——已完成
- ✅ #9 提取 SQL 片段——已完成
- ✅ #14 一键添加 @Param——已完成
- #26 Add All Column SQL
- ✅ #10 Mapper 图标——已完成
- ✅ #16 未使用方法检测、#17 resultType 匹配检测——已完成
- ✅ **#20 中的 MyBatis-Plus BaseMapper 豁免**——已完成（含免注解识别与 QueryWrapper 列名补全）

### 第二批（中成本）

- ✅ #2 resultMap property 补全与检测——已完成
- ✅ #6 XML 折叠——已完成
- #24 SQL ⇄ XML/Java 互转
- #29 SQL 日志还原
- #32 TypeAlias 别名解析

### 第三批（大工程，按产品定位选做）

- ❌ #12 方法名生成 SQL——不迁移（与现有标签生成/AI 生成路线重叠）
- ✅ #1 数据源 SQL 补全与检测——已完成（可选数据源模块 + 核心关键字补全）
- ✅ #18 Spring 集成——已完成（核心模块抑制器方案，无需可选依赖，IC 同样生效）
- ❌ #15 测试用例生成——不迁移（需连库运行，超出静态增强定位）

> 注意：#1/#8/#18 依赖 Ultimate 专属插件（database/spring），需做成可选依赖模块（`<depends optional="true" config-file="...">`），Community 版自动降级。

## 五、SQL 编辑体验类（#1-11）迁移完成情况（2026-10-28 实施完成）

> 全部 11 项已在 `2026.x` 分支实现并通过 `compileJava` 编译验证；
> 打包验证因构建机无法访问 jetbrains.com 下载 instrumentation 依赖（已知网络问题）暂未执行，代码层面无阻塞。

| # | 状态 | 实现说明 |
|---|------|---------|
| 1 | ✅ | **核心**: `SqlKeywordCompletionStrategy` 在语句文本中提供 SQL 关键字补全（无数据源依赖，参数补全优先级更高，SQL 已注入时自动让位）。**可选模块**（`easy-tools-database.xml`，依赖 `com.intellij.database`）: `MyBatisSqlInjectionContributor` 以 MultiHostInjector 向语句文本注入 SQL 语言，获得 IDE 原生表/列补全与 SQL inspection；`SqlLanguageHolder` 反射兼容 `com.intellij.sql.SqlLanguage`（241-25x）/`com.intellij.sql.psi.SqlLanguage`（261+）的类路径迁移。IC 环境自动降级 |
| 2 | ✅ | `MyBatisResultPropertyReference`: result/id/collection/association 的 `property` 属性补全（Java 字段+getter）、Ctrl+B 跳转、重命名同步（含嵌套属性路径）；`MyBatisResultMapInspection`: property 未解析警告 + column 重复映射检测。column 数据库侧补全随 #1 可选模块的 SQL 注入获得 |
| 3 | ✅ | `MyBatisOgnlLanguage` 独立语言: 手写 Lexer（关键字/字符串/数字/运算符/括号）、扁平 Parser（永不产生红色错误标记）、语法高亮、() [] 括号匹配；`MyBatisOgnlMultiHostInjector` 注入 if/when test 属性；`MyBatisOgnlInspection` 检测 `'a'=='b'` 误用（多字符单引号字面量、String 参数与字符字面量比较）并提供"替换双引号"快速修复。OGNL 格式化保持原样（不注册格式化器，Ctrl+Alt+L 不破坏表达式） |
| 4 | ✅ | `PlaceholderPropertyCompletionStrategy`: `#{param, |` 处补全 javaType/jdbcType/mode/numericScale/resultMap/typeHandler 属性 key 及取值（JdbcType 枚举、IN/OUT/INOUT、常用 java 类型、本文件 resultMap id）；`MyBatisParamInspection`: 参数未定义/属性链不可解析警告 + jdbcType/mode 非法取值 + 未知属性 key 检测 |
| 5 | ✅ | `MyBatisXmlFormattingModelBuilder`（`lang.formatter XML`，order first）: Mapper XML 使用保守格式化模型——标签层级正常缩进、SQL 文本块保持原换行/空行/列对齐、不强制断行合并；非 Mapper XML 委托平台 `XmlFormattingModelBuilder` 行为不变 |
| 6 | ✅ | `MapperXmlFoldingBuilder`（foldingBuilder 是叠加型 EP，平台默认 XML 折叠不受影响）: 语句/sql/resultMap 标签整体折叠（占位符 "select id ..." 形式）+ 多行长 SQL 文本折叠（占位符显示首行） |
| 7 | ✅ | 5 组 Live Templates（`liveTemplates/*.xml` + `MyBatisLiveTemplatesProvider`）: MyBatis-SQL（sel/selr/ins/upd/del/sqlf/inc/incp/rm/rmc）、MyBatis-Comment（c./sqlc/sqlcb/todo）、MyBatis-SqlNav（whr/ifn/ifns/feach/feachi/choo/sett/trimw/bind）、MyBatis-OGNL（tn/tns/nul/ems/andn/orn/sz/eqs）、MyBatis-Param（pp/ppj/dd/like/likem） |
| 8 | ✅ | `RunSqlLineMarkerProvider` 行标记 + `MyBatisSqlAssembler`（include 递归内联含跨 namespace、where/set/trim 前缀处理、foreach open/close 展开、#{}→?）：点击组装完整 SQL，生成 SQL 控制台临时文件（Ultimate 可 Ctrl+Enter 执行，方言由 `MyBatisSqlDialectResolver` 扩展点+可选模块提供）并复制到剪贴板；IC 降级为纯文本文件+剪贴板 |
| 9 | ✅ | `ExtractSqlFragmentAction`（Ctrl+Alt+C + MyBatisActionGroup 菜单）: 选中语句内文本提取为 `<sql>` 片段（追加到 mapper 根末尾，id 唯一性校验），原位置替换为 `<include refid="..."/>`；支持含动态标签的选区 |
| 10 | ✅ | `MapperXmlIconProvider` + `icons/mybatisMapperXml.svg`: mapper 根标签的 XML 文件在项目视图显示专属图标 |
| 11 | ✅ | `MapperXmlInspectionSuppressor`: 抑制 `SqlDialectInspection`/`SqlNoDataSourceInspection`/`SqlCurrentSchemaInspection`（SQL 方言/数据源噪音）与 `SpellCheckingInspection`（OGNL test 属性、SQL 文本不参与拼写检查） |

### 实施要点（跨版本兼容 2024.1+）

- 注入统一使用 `MultiHostInjector`（`com.intellij.multiHostInjector`）稳定 API —— 经查 2026.1 已移除经典 `com.intellij.psi.LanguageInjectionContributor`/`psi.languageInjectionContributor` 接口
- `DefaultLiveTemplatesProvider` 位于 `com.intellij.codeInsight.template.impl`（注意带 impl）
- `Indent.getNone()` 在 261 改名为 `getNoneIndent()`，Formatter 中以 `null` 缩进替代保证双版本兼容
- `LineMarkerProvider2` 已在 261 移除，运行标记使用 `LineMarkerProvider` 接口
- SqlLanguage 通过反射双路径查找（`com.intellij.sql.SqlLanguage` / `com.intellij.sql.psi.SqlLanguage`）
- `PlainTextLanguage` 包路径有迁移，运行 SQL 临时文件降级时使用 `Language.findLanguageByID("TEXT")`

## 六、Java 侧 Mapper 增强（#12-21）迁移完成情况（2026-10-28 实施完成）

> #14/#16/#17/#18/#20/#21 已在 `2026.x` 分支实现并通过 `compileJava` 编译验证；
> #12/#13/#15/#19 决策不迁移（理由见「二、差异清单」表格下方说明）。

| # | 状态 | 实现说明 |
|---|------|---------|
| 14 | ✅ | `AddParamAnnotationIntention`（意图操作，MyBatis 分类）：光标位于 Mapper 接口方法时，为所有尚未标注 @Param 的参数批量添加 `@Param("参数名")`，创建后自动缩短类引用（可生成 import） |
| 16 | ✅ | `MyBatisUnusedMapperMethodInspection`（MyBatisUnusedMapperMethod）：Mapper 接口声明的抽象方法在 Java 侧无任何引用（ReferencesSearch 全项目）时按未使用告警；XML 语句 id 与方法的关联不算使用；dumb mode 跳过 |
| 17 | ✅ | `MyBatisResultTypeInspection`（MyBatisResultType）：① select 的 `resultType` 与方法返回"单行类型"（List/Collection/IPage 泛型解包）互不兼容时告警（兼容规则：双向继承、同数值族、Object 宽容、Map 按可赋值性判定；带泛型的自定义返回类型跳过避免误报）；② select 既无 resultMap 也无 resultType 时告警。类型别名复用 `MyBatisJavaTypeReference.resolveTypeText` |
| 18 | ✅ | 核心模块方案（无 `com.intellij.spring` 依赖，IC/IU 均生效）：`MyBatisMapperScanService` 解析 @MapperScan/@MapperScans 的 value/basePackages/basePackageClasses 及 annotationClass/markerInterface 过滤（按 PSI 修改计数缓存）；`MapperSpringInjectionSuppressor`（JAVA inspectionSuppressor）在注入点（@Autowired/@Resource 字段、构造器/Setter 参数）类型为 Mapper 接口时抑制 Spring 插件 `SpringJavaInjectionPointsAutowiringInspection`（含旧 ID `SpringJavaAutowiringInspection`）的 "Could not autowire" 报红——抑制机制经反编译验证：平台 `InspectionEngine` 对所有本地 inspection 统一走 `SuppressionUtil.inspectionResultSuppressed` → 按问题元素语言查 `InspectionSuppressor`，与注册方插件无关。注：2023.3+ IU 的 Spring 插件已内置 @Mapper Bean 支持，本方案为其兜底（Community/未配置 Spring 模型/旧版 Ultimate） |
| 20 | ✅ | ① 免注解识别：`MyBatisUtils.isMapperInterface` 扩展为 @Mapper ∨ 继承框架 BaseMapper（MyBatis-Plus `com.baomidou.mybatisplus.core.mapper.BaseMapper`、MyBatis-Flex `com.mybatisflex.core.BaseMapper`、tk-mapper `tk.mybatis.mapper.common.*`）∨ @MapperScan 扫描包覆盖，导航/检测/意图全线生效；② 语句豁免：`MyBatisMethodNotInXmlInspection` 跳过框架 BaseMapper 声明的方法，XML id 补全（`MyBatisStatementIdReference`）过滤框架方法；③ QueryWrapper 支持：`MyBatisPlusWrapperCompletionContributor` 在 AbstractWrapper/AbstractChainWrapper 链式条件方法（eq/like/in/orderBy…）首参数处按包装器泛型实体补全列名（@TableField/@TableId 显式值优先，默认驼峰转下划线），MP 不在类路径时零候选 |
| 21 | ✅ | ① `MyBatisProviderReferenceContributor`：@SelectProvider/@InsertProvider/@UpdateProvider/@DeleteProvider 的 `method = "xxx"` 字符串 → Provider 类方法（type/value 类字面量解析，未指定回退 Mapper 接口）的 Ctrl+B 跳转、方法名补全、重命名同步；② `MigrateSqlAnnotationIntention`：@Select/@Insert/@Update/@Delete 注解 SQL 一键迁移为 Mapper XML 语句标签，支持 `<script>` 动态 SQL 原样迁移、普通 SQL 特殊字符 CDATA 包裹、select 自动带 resultType（List 泛型解包+装箱）、XML 缺失时按模块 resources 源根（回退接口目录）创建并注册缓存，迁移后删除原注解 |

### 实施要点（跨版本兼容 2024.1+）

- `AnnotatedElementsSearch` 在 2024.3+ 改为 `searchPsiClasses(PsiClass, SearchScope)`，旧的泛型 `search(Class, PsiClass, SearchScope)` 已删除 —— 服务内反射双路径调用（与 `SqlLanguageHolder` 同一套路）
- `PsiClassObjectAccessExpression.getOperand()` 返回 `PsiTypeElement`（非 PsiJavaCodeReferenceElement），经 `.getType() instanceof PsiClassType` 解析类字面量
- `PsiClass` 无 `isAssignableFrom`，类型兼容判定用 `getQualifiedName()` 相等 + `isInheritor(cls, true)` 双向组合
- `PsiModificationTracker.getModificationCount()` 作为 @MapperScan 缓存失效戳（Java PSI 任意变更即失效），计算包裹 `ReadAction.compute`（可重入）
- 本批功能全部位于核心模块，未新增可选依赖（Spring 集成走抑制器而非 Spring 插件扩展点）
