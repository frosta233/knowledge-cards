# 知识闪卡（KnowledgeCards）

安卓离线知识卡片应用：从 Markdown 批量导入"知识卡片"（每张约 150 字文本），
左右滑动浏览、多级目录分类、基础编辑，并带一个桌面微件直接查看当前卡片。

产品定位：免费、无广告、纯本地、离线可用、注重文本。
典型场景：中医药方剂学知识库（如 `方剂学/解表剂/辛温解表/麻黄汤`），但设计完全通用。

---

## 功能一览（全部已实现并真机验证）

| 模块 | 能力 |
|---|---|
| **闪卡浏览** | HorizontalPager 左右滑动；浏览顺序可选**目录顺序（默认，与目录页一致）/ 标题序 / 导入序**；重启恢复上次位置；顶部完整路径（超宽才省略）；"第 x / 共 y 张" |
| **闪卡集** | 目录页选中某个分类（如"祛湿剂"）后，闪卡页只浏览该集卡片；顶部「全部」按钮回到全库 |
| **导入** | SAF 选文件夹递归扫描 `.md`（**分类 = 文件相对根文件夹的层级**）；也支持 `.zip` / `.tar` 压缩包（自动剥离公共顶层目录；`.rar` 不支持并提示原因）；按「路径+标题」判重，重复导入只更新内容 |
| **导出** | 全部卡片按分类建文件夹结构导出为 .md 备份，可再导入 |
| **目录** | 多级树展开/收起；节点 **⋮ 菜单**：同级上移/下移（顺序持久化）、重命名（整组卡片路径批量迁移）、删除（卡片移入未分类或删除）；最低级目录也可展开查看卡片列表 |
| **编辑** | 卡片右上角编辑按钮；改标题/正文/分类（分类选择器，含「新建分类…」输入框） |
| **设置** | 主题（跟随系统/浅色/深色）、**强调色**（动态取色/绿/蓝/橙/紫/莫兰迪绿/莫兰迪蓝/莫兰迪陶土）、字号滑块、浏览顺序、导入/导出入口 |
| **桌面微件** | Glance；半透明亚克力圆角面板；路径栏（点击展开/收起完整路径）、标题、正文（LazyColumn 可滚动）、上一张/下一张按钮（控件颜色跟随强调色）；与 App 共享进度（DataStore），App 内翻页即联动更新 |

## 技术栈

- Kotlin + Jetpack Compose + Material 3（动态取色 + 预设强调色，浅/深主题）
- Room + SQLite（`cards`、`category_order` 两表）
- DataStore Preferences（进度 / 设置 / 微件状态共享）
- Jetpack Glance 1.1.1（桌面微件）
- SAF + documentfile（导入导出，零存储权限）
- commons-compress（tar 解析）
- minSdk = 36，targetSdk = 36，单模块 `app`

## 工程结构

```
app/src/main/java/com/example/knowledgecards/
├── data/
│   ├── Card.kt / CardDao.kt / AppDatabase.kt / CardRepository.kt / CategoryOrder.kt
│   ├── import/   MarkdownFile · MarkdownParser · CardImporter · SafTreeScanner · ArchiveImporter · ImportResult
│   └── export/   CardExporter
├── domain/
│   ├── CategoryTree.kt     （树构建、breadcrumb、展平，支持手动顺序）
│   └── ProgressStore.kt    （DataStore：进度 + 全部设置 + 微件状态）
├── ui/
│   ├── theme/              （动态取色 + 8 套强调色预设）
│   ├── browse/             （闪卡页：Pager、scope 过滤、进度保存、微件联动）
│   ├── directory/          （目录树 + 卡组管理菜单）
│   ├── editor/             （编辑页 + 分类选择器/新建分类）
│   └── settings/           （设置页 + 导入/导出入口）
├── widget/
│   ├── CardWidget.kt       （Glance 布局 + 渲染）
│   ├── CardWidgetReceiver.kt（翻卡/路径切换广播）
│   └── WidgetActions.kt    （action 定义 + WidgetUpdater 直接推送）
├── KnowledgeCardsApp.kt    （手动 DI 容器）
└── MainActivity.kt         （底部导航：闪卡/目录/设置 + 全屏编辑覆盖）
app/src/test/               （MarkdownParser / CategoryTree / CardImporter / ArchiveImporter 测试）
docs/转写提示词.md           （教材原文 → 卡片格式 的 AI 转写提示词）
```

## 构建

环境要求：JDK 17+（本机用 21）、Android SDK（`local.properties` 已指向 `/home/aliya/Android/Sdk`）、网络可达 google/mavenCentral。

```bash
./gradlew :app:assembleDebug            # 构建 debug APK
./gradlew :app:testDebugUnitTest        # 单元测试（25+ 用例）
./gradlew :app:installDebug             # 安装到已连接设备
```

依赖版本集中在 `gradle/libs.versions.toml`：
AGP 8.13.2 · Kotlin 2.2.21 · KSP 2.2.21-2.0.5 · Room 2.8.4 · Compose BOM 2026.05.01 ·
Glance 1.1.1 · DataStore 1.1.7 · commons-compress 1.28.0
Gradle wrapper 8.14.3，`gradle.properties` 里 `org.gradle.java.home=/usr/lib/jvm/java-21-openjdk`。

## 数据与导入规范

- **卡片**：`Card(id, title, content, path, sortOrder, createdAt, updatedAt)`，`path` 为 `/` 分隔的分类路径，空串 = 未分类；`(path, title)` 唯一（判重依据）。
- **卡组顺序**：`CategoryOrder(path, position)`，同级节点有记录的按 position 排前，未记录的按名称排后。
- **导入格式**（`.md` = 一张卡）：
  - 标题：文件名（不含扩展名）；文件内第一个非空行为 `# 标题` 时以文件内为准。
  - 分类：**文件相对导入根文件夹的目录层级**；仅在根目录下的文件回退使用首行 `路径:` / `Path:` 声明（兼容旧版平铺备份）；声明行会从正文剥离。
  - 正文：其余所有行。
- **导出格式**：按 path 建文件夹，文件内 `# 标题` + 正文（无路径声明），导入导出互逆。
- **转写流程**：教材原文 → `docs/转写提示词.md` 交给 AI → `方名.md` 按分类放文件夹 → 导入。

## 开发注意事项（踩过的坑，务必先读）

1. **Glance 1.1 的 Int 尺寸重载是 `@DpRes`**：`Modifier.height(4)` / `width(6)` / `padding(12)` 会把数字当资源 ID，运行时 `Resources$NotFoundException` 导致整个微件渲染失败（只显示第一个元素）。所有尺寸必须写 `.dp`。
2. **Glance 的 `updateAll()` 从回调/广播触发时不可靠**（session 机制会跳过重新渲染）：`WidgetUpdater.update()` 用 `GlanceRemoteViews.compose()` 同步生成 RemoteViews 再 `AppWidgetManager.updateAppWidget()` 直接推送，秒级生效。新增微件更新入口请走这条路。
3. **LazyColumn（ListView）高度**：不能 `fillMaxHeight()`（会把底部按钮挤出微件），用 `defaultWeight()`（layout_weight 分配剩余空间）或显式高度。
4. **DropdownMenu 定位**：菜单必须与触发按钮放在同一个 `Box` 内，否则 Popup 锚定到外层 Row，菜单会出现在屏幕边缘。
5. **数据库迁移**：加表/改表时在 `AppDatabase` 补 `Migration`（已有 v1→v2），否则老用户升级直接崩溃。
6. **嵌套 Scaffold 双重 inset**：主界面是 `Column + NavigationBar`，各页面用裸 `Column + TopAppBar`（TopAppBar 自带状态栏 inset），不要再套 Scaffold。
7. **动态取色在 ColorOS 上不可靠**（取色可能与壁纸不符），所以提供 8 套强调色预设；微件不读 `GlanceTheme.colors` 的 primary（那是系统色），而是从 DataStore 读强调色自己映射。
8. **真机测试**：设备是 ColorOS（OnePlus），`adb shell pm clear` 被禁用；`adb input text` 受当前 IME 影响（切到 `com.google.android.inputmethod.latin` 后稳定）；uiautomator dump 有缓存延迟（等 2 秒重 dump）；文件选择器 UI 自动化比较绕（用「文件→全部文件」路径浏览）。
9. **微件与 App 联动**：双方共用 `ProgressStore`（DataStore）；App 在浏览页 settle/onPause 时保存进度并调用 `WidgetUpdater.update()`。

## 当前状态

- P1（数据模型/导入/浏览/目录）、P2（编辑/设置）、P3（微件/导出）全部完成，单元测试通过。
- 已在 Android 16 真机（OnePlus, 1264×2780）完成全流程验证：导入 235 张方剂卡、目录管理、微件翻卡/路径展开/滚动、进度联动。
- 遗留可优化点：微件正文滚动依赖 Glance LazyColumn（已知在个别 launcher 上 a11y 报告高度 0，但渲染正常）；微件半透明面板透明度可调；`.rar` 未支持（专利格式）。
