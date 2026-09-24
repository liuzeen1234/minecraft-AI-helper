package com.example.helloworld;

import java.util.Collections;
import java.util.List;

/**
 * 知识库中的单篇文档，对应 knowledge/ 目录下一个带 YAML front matter 的 Markdown 文件。
 *
 * <p>front matter 示例：
 * <pre>
 * ---
 * title: 苦力怕（Creeper）——生成、爆炸机制、闪电苦力怕
 * version: 1.20.4
 * category: 实体生物
 * keywords: [苦力怕, creeper, 爆炸, 闪电苦力怕, 敌对生物, 火药]
 * summary: 苦力怕的生成、掉落、追击与爆炸倒计时机制……
 * source: 中文 Minecraft Wiki（Fandom 镜像），2026-09-13 抓取
 * ---
 * </pre>
 *
 * <p>{@code name} 是用于 [KNOWLEDGE] 标签点名检索的唯一标识，取文件名（不含扩展名）。
 * 目录展示（提供给 AI 的知识库索引）只使用 title/category/keywords/summary，不含正文，
 * 正文 {@code body} 只在文档被点名检索后才会被读取和注入。
 */
public class KnowledgeDoc {

    private final String name;
    private final String relativePath;
    private final String title;
    private final String category;
    private final List<String> keywords;
    private final String summary;
    private final String body;

    public KnowledgeDoc(String name, String relativePath, String title, String category,
                         List<String> keywords, String summary, String body) {
        this.name = name;
        this.relativePath = relativePath;
        this.title = (title == null || title.isBlank()) ? name : title;
        this.category = category == null ? "" : category;
        this.keywords = keywords == null ? Collections.emptyList() : keywords;
        this.summary = summary == null ? "" : summary;
        this.body = body == null ? "" : body;
    }

    /** 文档唯一标识（文件名，不含扩展名），用于 [KNOWLEDGE] 标签点名检索。 */
    public String getName() { return name; }

    /** 相对知识库语言根目录的路径，仅用于日志/调试。 */
    public String getRelativePath() { return relativePath; }

    public String getTitle() { return title; }

    public String getCategory() { return category; }

    public List<String> getKeywords() { return keywords; }

    public String getSummary() { return summary; }

    /** 文档正文（不含 front matter），只在被点名检索后才会用到。 */
    public String getBody() { return body; }

    /**
     * 生成用于知识库目录展示的一行摘要，不含正文，供 system prompt 注入。
     * 格式：- name: title [category] 关键词: a, b, c —— summary
     */
    public String toDirectoryEntry() {
        StringBuilder sb = new StringBuilder();
        sb.append("- ").append(name).append(": ").append(title);
        if (!category.isBlank()) {
            sb.append(" [").append(category).append("]");
        }
        if (!keywords.isEmpty()) {
            sb.append(" 关键词: ").append(String.join(", ", keywords));
        }
        if (!summary.isBlank()) {
            sb.append(" —— ").append(summary);
        }
        return sb.toString();
    }
}
