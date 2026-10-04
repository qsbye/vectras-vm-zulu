---
name: docs-html-to-markdown
description: Convert saved HTML articles in docs/ to tidy Markdown with markitdown installed via uv tool, then update docs/README.md index. Use when asked to 转换 or 整理 a docs/*.html web archive (cnblogs, Agora, Reddit, Xiph) into a reference md. Do not use for project code.
---

# docs/ 网页归档 HTML 转 Markdown

把 `docs/` 下本地保存的网页 HTML（文件名常带 `(YYYY_M_D HH：MM：SS).html` 时间戳）转换为干净、可直接阅读的 Markdown 参考资料，并登记到 `docs/README.md` 索引。最近一次范例：`docs/anc-algorithm-research-cnblogs.md`（2026-10-03）。

## 1. 工具：uv tool 安装的 markitdown

markitdown 通过 uv 以工具方式安装，可执行文件在 `~/.local/bin/markitdown`。非交互 shell 的 PATH 可能不含 `~/.local/bin`，**始终用绝对路径调用**。

```bash
# 未安装时（只需一次）
uv tool install markitdown
# 已安装时确认版本
~/.local/bin/markitdown --version
```

注意：`pip install markitdown` 在旧环境可能装到 0.0.1a1 占位包（无 CLI、无 MarkItDown 类），不要使用；以 uv tool 的 0.1.x 为准。

## 2. 转换

```bash
~/.local/bin/markitdown "docs/<原始文件名>.html" -o /tmp/<slug>_raw.md
```

先通读 raw 输出再整理。网页另存为的 HTML 体积可能很大（含内联 base64 图标），但转换后的 md 通常只有正文文本加少量图片链接。

## 3. 界定正文，剔除站点框架

博客园等页面转换后会混入大量框架噪声，必须删除，只保留文章本体：

- 开头：站点导航（会员/新闻/博问/搜索）、登录注册、博主主页头部、随笔计数等，一直删到文章标题链接式 H1。
- 结尾：从 `分类:`/`免责声明`/`好文要顶`/`关注我`/`上一篇/下一篇`/`posted @`/编辑推荐/公告/日历/阅读排行榜/备案信息起，全部删除。
- 正文中重复出现的文章大标题（链接式 H1 + 纯文本 H1）只保留一个，且统一由文首标题块给出。

## 4. 整理排版规则

### 文首来源块（固定格式）

```markdown
# <中文标题>（译文/整理）

> **来源作者**：<作者/译者>（<站点>）
> **原文链接**：<URL>
> **发布时间**：<YYYY-MM-DD，可从 H1 链接 title 或页脚 posted @ 取>
> **归档整理**：<今天日期>（由本地保存的网页 HTML 经 markitdown 转换并人工整理排版）

---
```

### 图片

- markitdown 会把正文配图转成**单栏或多栏 Markdown 表格**（表头是图注、单元格是 `![](url)`）。逐列改为：
  ```markdown
  ![图注](图片URL)

  *图注*
  ```
  多栏表格拆成多张图，图之间空一行。
- 远程图片 URL（如 `https://img2024.cnblogs.com/...`）原样保留；内联 base64 图片统一替换为占位 `![](data:image/png;base64...)`（与 `aec-principle-and-implementation-cnblogs.md` 一致）。
- 英文原文部分常见只有图注 bullet（无图片 URL），转为斜体段落 `*Fig. N. ...*`，相邻图注之间空一行。

### 公式与符号

- 独立成行的编号公式 `(1)H(z)=...` 整体转为引用块独占行：`> (1)H(z)=...`。
- 未编号的独立公式同样用 `> ` 引用块。
- 公式是网页纯文本被压平的结果（分式/上下标会丢失），不臆造还原；只清理明显残留的 LaTeX 片段（如 `\(...\)`、`$\hat{S\_2}`）。

### 段落与列表

- 删除 markitdown 产生的伪嵌套 bullet（图注重复行 + 缩进长段落），长段落恢复为普通段落。
- 有序列表项编号风格统一（`1.`/`2.`/`3.`），列表与相邻段落间留空行；续行缩进保持与列表标记对齐。
- 参考文献：合并被折行的条目，转为从 1 开始的有序列表，`[n]` 标记去掉。
- 折叠 3 个以上连续空行为 1 个空行。

### 双语内容

译文类文章若正文后附完整外文原文，保留为附录章节（如 `## 附录：英文原文（...）`），对其施加同样的图注/公式/列表清理；不要删除原文。

## 5. 文件命名与登记

- 文件名用英文 kebab-case slug + 来源后缀，参照既有约定：
  `aec-principle-and-implementation-cnblogs.md`、`rnnoise-learning-noise-suppression.md`。
- 产物直接写入 `docs/<slug>.md`。
- 在 `docs/README.md` 的目录表中新增一行（文档链接、主题、来源链接），必要时更新开头说明与“与本项目的关系”小节。

## 6. 交付前验证

- 文首来源块字段齐全；标题层级正确（一个 H1，章节 H2，子节 H3/H4）。
- grep 确认无框架残留词：`免责声明`、`好文要顶`、`上一篇`、`阅读排行榜`、`公网安备`、`分类:`。
- 确认不存在以 `|` 开头的图片表格行；每张配图都是图片 + 斜体图注形式。
- 通读文件开头、章节交界处、参考文献与文末，确认无空 bullet、无连续空行、无未闭合结构。
- 转换只是为了沉淀参考资料，不要改动 `app/`、`desktop/` 代码。
