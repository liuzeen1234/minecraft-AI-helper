package com.example.helloworld;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 KnowledgeBase 的核心逻辑：
 * - front matter 解析（title/category/keywords/summary + 正文拆分）
 * - 目录生成（buildDirectoryText）不包含正文
 * - 按名称检索（retrieve），含多文档、大小写、空格、不存在名称的处理
 * - maxDocs / maxChars 截断
 * - [KNOWLEDGE] 标签提取（镜像 HelloWorldMod 中的私有逻辑）
 *
 * 使用 @TempDir 构造一个假的知识库根目录，绕过 ModPaths 的固定游戏目录路径，
 * 并通过包内可见的 langDirName 重载方法直接指定子目录名，不依赖 currentLangDirName() 的固定值，
 * 使测试不依赖 Minecraft 客户端环境。测试数据混用中英文内容，仅用于验证解析逻辑（如 UTF-8、
 * 中文文件名等边界情况），与知识库实际只维护英文版（basic_info_en）无关。
 */
class KnowledgeBaseTest {

    private static final String LANG_DIR = "test_docs";

    @TempDir
    Path tempRoot;

    private KnowledgeBase knowledgeBase;
    private Path langDir;

    @BeforeEach
    void setUp() throws IOException {
        knowledgeBase = new KnowledgeBase(tempRoot);
        langDir = tempRoot.resolve(LANG_DIR);
        Files.createDirectories(langDir);
    }

    private void writeDoc(String fileName, String content) throws IOException {
        Files.writeString(langDir.resolve(fileName), content);
    }

    // ========== front matter 解析 ==========

    @Test
    void testParseDoc_ExtractsFrontMatterAndBody() throws IOException {
        writeDoc("苦力怕.md",
                "---\n" +
                "title: 苦力怕（Creeper）\n" +
                "version: 1.20.4\n" +
                "category: 实体生物\n" +
                "keywords: [苦力怕, creeper, 爆炸]\n" +
                "summary: 苦力怕的生成与爆炸机制。\n" +
                "---\n" +
                "\n" +
                "# 苦力怕\n\n正文内容在这里。\n");

        List<KnowledgeDoc> docs = knowledgeBase.loadDocs(LANG_DIR);
        assertEquals(1, docs.size());
        KnowledgeDoc doc = docs.get(0);

        assertEquals("苦力怕", doc.getName(), "文档名应为文件名去掉扩展名");
        assertEquals("苦力怕（Creeper）", doc.getTitle());
        assertEquals("实体生物", doc.getCategory());
        assertEquals(List.of("苦力怕", "creeper", "爆炸"), doc.getKeywords());
        assertEquals("苦力怕的生成与爆炸机制。", doc.getSummary());
        assertTrue(doc.getBody().contains("正文内容在这里。"));
        assertFalse(doc.getBody().contains("title:"), "正文不应包含 front matter");
    }

    @Test
    void testParseDoc_WithoutFrontMatter_FallsBackToFileName() throws IOException {
        writeDoc("无front matter.md", "只有正文，没有 front matter。\n");

        List<KnowledgeDoc> docs = knowledgeBase.loadDocs(LANG_DIR);
        assertEquals(1, docs.size());
        KnowledgeDoc doc = docs.get(0);

        assertEquals("无front matter", doc.getName());
        assertEquals("无front matter", doc.getTitle(), "无 title 时应回退为文档名");
        assertEquals("", doc.getCategory());
        assertTrue(doc.getKeywords().isEmpty());
        assertTrue(doc.getBody().contains("只有正文"));
    }

    @Test
    void testLoadDocs_ScansNestedSubdirectories() throws IOException {
        Path subDir = langDir.resolve("方块特性");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("活塞.md"),
                "---\ntitle: 活塞\ncategory: 方块特性\n---\n正文\n");

        List<KnowledgeDoc> docs = knowledgeBase.loadDocs(LANG_DIR);
        assertEquals(1, docs.size());
        assertEquals("活塞", docs.get(0).getName());
    }

    @Test
    void testLoadDocs_EmptyDirectory_ReturnsEmptyList() {
        List<KnowledgeDoc> docs = knowledgeBase.loadDocs(LANG_DIR);
        assertTrue(docs.isEmpty());
    }

    @Test
    void testLoadDocs_NonExistentLangDir_ReturnsEmptyList() {
        List<KnowledgeDoc> docs = knowledgeBase.loadDocs("basic_info_en"); // 未创建
        assertTrue(docs.isEmpty());
    }

    // ========== 目录生成不含正文 ==========

    @Test
    void testBuildDirectoryText_ExcludesBody() throws IOException {
        writeDoc("苦力怕.md",
                "---\ntitle: 苦力怕\ncategory: 实体生物\nkeywords: [creeper]\nsummary: 摘要文本\n---\n" +
                "这段正文绝对不应该出现在目录里。\n");

        String directory = knowledgeBase.buildDirectoryText(LANG_DIR);
        assertNotNull(directory);
        assertTrue(directory.contains("苦力怕"));
        assertTrue(directory.contains("摘要文本"));
        assertFalse(directory.contains("这段正文绝对不应该出现在目录里。"));
    }

    @Test
    void testBuildDirectoryText_EmptyKnowledgeBase_ReturnsNull() {
        assertNull(knowledgeBase.buildDirectoryText(LANG_DIR));
    }

    // ========== 按名称检索 ==========

    @Test
    void testRetrieve_SingleDocByExactName() throws IOException {
        writeDoc("苦力怕.md", "---\ntitle: 苦力怕\n---\n苦力怕正文\n");

        String result = knowledgeBase.retrieve(List.of("苦力怕"), 8, 20000, LANG_DIR);
        assertNotNull(result);
        assertTrue(result.contains("苦力怕正文"));
    }

    @Test
    void testRetrieve_CaseInsensitiveAndTrimmed() throws IOException {
        writeDoc("creeper.md", "---\ntitle: Creeper\n---\nCreeper body text\n");

        String result = knowledgeBase.retrieve(List.of("  CREEPER  "), 8, 20000, LANG_DIR);
        assertNotNull(result);
        assertTrue(result.contains("Creeper body text"), "应忽略大小写和首尾空格命中文档");
    }

    @Test
    void testRetrieve_MultipleDocs() throws IOException {
        writeDoc("苦力怕.md", "---\ntitle: 苦力怕\n---\n苦力怕正文\n");
        writeDoc("活塞.md", "---\ntitle: 活塞\n---\n活塞正文\n");

        String result = knowledgeBase.retrieve(List.of("苦力怕", "活塞"), 8, 20000, LANG_DIR);
        assertNotNull(result);
        assertTrue(result.contains("苦力怕正文"));
        assertTrue(result.contains("活塞正文"));
    }

    @Test
    void testRetrieve_NonExistentName_ReportedAsMissed() throws IOException {
        writeDoc("苦力怕.md", "---\ntitle: 苦力怕\n---\n苦力怕正文\n");

        String result = knowledgeBase.retrieve(List.of("苦力怕", "不存在的文档"), 8, 20000, LANG_DIR);
        assertNotNull(result);
        assertTrue(result.contains("苦力怕正文"));
        assertTrue(result.contains("不存在的文档"), "未命中的文档名应出现在提示里");
    }

    @Test
    void testRetrieve_AllNamesMissing_StillReturnsNoticeText() throws IOException {
        writeDoc("苦力怕.md", "---\ntitle: 苦力怕\n---\n苦力怕正文\n");

        String result = knowledgeBase.retrieve(List.of("完全不存在"), 8, 20000, LANG_DIR);
        assertNotNull(result, "即使全部未命中，也应返回带提示的文本而不是 null");
        assertTrue(result.contains("完全不存在"));
    }

    @Test
    void testRetrieve_EmptyRequestList_ReturnsNull() throws IOException {
        writeDoc("苦力怕.md", "---\ntitle: 苦力怕\n---\n苦力怕正文\n");

        assertNull(knowledgeBase.retrieve(Collections.emptyList(), 8, 20000, LANG_DIR));
        assertNull(knowledgeBase.retrieve(null, 8, 20000, LANG_DIR));
    }

    @Test
    void testRetrieve_EmptyKnowledgeBase_ReturnsNull() {
        assertNull(knowledgeBase.retrieve(List.of("任意文档"), 8, 20000, LANG_DIR));
    }

    // ========== maxDocs / maxChars 截断 ==========

    @Test
    void testRetrieve_MaxDocsLimit_TruncatesExtraRequests() throws IOException {
        writeDoc("doc1.md", "---\ntitle: doc1\n---\n内容1\n");
        writeDoc("doc2.md", "---\ntitle: doc2\n---\n内容2\n");
        writeDoc("doc3.md", "---\ntitle: doc3\n---\n内容3\n");

        String result = knowledgeBase.retrieve(List.of("doc1", "doc2", "doc3"), 2, 20000, LANG_DIR);
        assertNotNull(result);
        assertTrue(result.contains("内容1"));
        assertTrue(result.contains("内容2"));
        assertFalse(result.contains("内容3"), "超出 maxDocs 的文档不应被注入");
    }

    @Test
    void testRetrieve_MaxCharsLimit_SkipsOversizedDocsWithoutSlicing() throws IOException {
        String longBody = "A".repeat(100);
        String shortBody = "B".repeat(10);
        writeDoc("long.md", "---\ntitle: long\n---\n" + longBody + "\n");
        writeDoc("short.md", "---\ntitle: short\n---\n" + shortBody + "\n");

        // maxChars 只够放下第一篇（long, 100 字符），第二篇会被整篇跳过而不是被切碎
        String result = knowledgeBase.retrieve(List.of("long", "short"), 8, 100, LANG_DIR);
        assertNotNull(result);
        assertTrue(result.contains(longBody), "第一篇未超限，应完整注入");
        assertFalse(result.contains(shortBody), "第二篇会导致超限，应整篇跳过而不是截断内容");
        assertTrue(result.contains("short"), "被跳过的文档名应出现在提示里");
    }

    @Test
    void testRetrieve_FirstDocAlwaysInjected_EvenIfExceedsMaxChars() throws IOException {
        String hugeBody = "X".repeat(500);
        writeDoc("huge.md", "---\ntitle: huge\n---\n" + hugeBody + "\n");

        // maxChars 远小于单篇正文长度，但至少保证点名的第一篇能被注入，避免完全拿不到内容
        String result = knowledgeBase.retrieve(List.of("huge"), 8, 50, LANG_DIR);
        assertNotNull(result);
        assertTrue(result.contains(hugeBody));
    }

    // ========== [KNOWLEDGE] 标签提取（镜像 HelloWorldMod 私有逻辑） ==========

    @Test
    void testExtractKnowledgeDocNames_SingleName() {
        String response = "让我查一下知识库 [KNOWLEDGE]苦力怕[/KNOWLEDGE]";
        List<String> names = extractKnowledgeDocNames(response);
        assertEquals(List.of("苦力怕"), names);
    }

    @Test
    void testExtractKnowledgeDocNames_MultipleNamesCommaSeparated() {
        String response = "[KNOWLEDGE]苦力怕, 活塞 ,红石方块与红石元件[/KNOWLEDGE]";
        List<String> names = extractKnowledgeDocNames(response);
        assertEquals(List.of("苦力怕", "活塞", "红石方块与红石元件"), names);
    }

    @Test
    void testExtractKnowledgeDocNames_NotPresent_ReturnsEmptyList() {
        String response = "这是一个普通回复，没有知识库请求";
        assertTrue(extractKnowledgeDocNames(response).isEmpty());
    }

    @Test
    void testExtractKnowledgeDocNames_EmptyTag_ReturnsEmptyList() {
        String response = "[KNOWLEDGE][/KNOWLEDGE]";
        assertTrue(extractKnowledgeDocNames(response).isEmpty());
    }

    @Test
    void testExtractKnowledgeDocNames_IgnoresBlankEntries() {
        String response = "[KNOWLEDGE]苦力怕,,活塞,[/KNOWLEDGE]";
        assertEquals(List.of("苦力怕", "活塞"), extractKnowledgeDocNames(response));
    }

    /**
     * 镜像 HelloWorldMod.extractKnowledgeDocNames 的逻辑，用于独立验证标签解析行为。
     */
    private List<String> extractKnowledgeDocNames(String response) {
        int start = response.indexOf("[KNOWLEDGE]");
        int end = response.indexOf("[/KNOWLEDGE]");
        if (start == -1 || end == -1 || end <= start) {
            return Collections.emptyList();
        }
        String raw = response.substring(start + "[KNOWLEDGE]".length(), end).trim();
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }
}
