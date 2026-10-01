package com.example.helloworld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 {@link HelloWorldMod#extractLastTagContent(String, String, String)} 的解析逻辑。
 *
 * <p>重点覆盖历史 bug：AI 在回复正文里解释性地写出标签名（通常未闭合），
 * 后面才是真正的工具调用。提取时若从第一个开标签一路截到真正调用处的闭标签，
 * 会把中间大段说明文字误当成参数（导致 SEARCH/KNOWLEDGE/KNOWLEDGE_FILE 等反复失败）。
 * 正确行为是取“闭标签之前最后一个开标签”到该闭标签之间的内容。
 */
class TagExtractionTest {

    @Test
    void testCleanTag() {
        String r = "帮你搜索一下：[SEARCH]minecraft pistons[/SEARCH]";
        assertEquals("minecraft pistons",
                HelloWorldMod.extractLastTagContent(r, "[SEARCH]", "[/SEARCH]"));
    }

    @Test
    void testMentionedTagNameInProse_DoesNotContaminate() {
        // 正文里先出现一个未闭合的 [SEARCH]（解释用），末尾才是真正的调用
        String r = "我准备用 [SEARCH] 这个工具搜一下，注意关键词要精简，避免噪音：\n\n"
                + "[SEARCH]observer clock tutorial[/SEARCH]";
        assertEquals("observer clock tutorial",
                HelloWorldMod.extractLastTagContent(r, "[SEARCH]", "[/SEARCH]"));
    }

    @Test
    void testKnowledgeFile_MentionedTagNameInProse() {
        String r = "我用 [KNOWLEDGE_FILE] 读取活塞那篇的正文：\n"
                + "[KNOWLEDGE_FILE]basic_info_en/blocks/pistons.md[/KNOWLEDGE_FILE]";
        assertEquals("basic_info_en/blocks/pistons.md",
                HelloWorldMod.extractLastTagContent(r, "[KNOWLEDGE_FILE]", "[/KNOWLEDGE_FILE]"));
    }

    @Test
    void testKnowledgeOpenTagNotConfusedWithKnowledgeFile() {
        // [KNOWLEDGE] 的提取不应把 [KNOWLEDGE_FILE] 误当成开标签
        String r = "先读文件 [KNOWLEDGE_FILE]a/b.md[/KNOWLEDGE_FILE]，再取知识库文档 [KNOWLEDGE]pistons[/KNOWLEDGE]";
        assertEquals("pistons",
                HelloWorldMod.extractLastTagContent(r, "[KNOWLEDGE]", "[/KNOWLEDGE]"));
    }

    @Test
    void testNoCloseTag_ReturnsNull() {
        String r = "你可以用 [SEARCH] 来联网搜索，需要我搜什么？";
        assertNull(HelloWorldMod.extractLastTagContent(r, "[SEARCH]", "[/SEARCH]"));
    }

    @Test
    void testNoTagAtAll_ReturnsNull() {
        assertNull(HelloWorldMod.extractLastTagContent("普通聊天，没有任何标签。", "[SEARCH]", "[/SEARCH]"));
    }

    @Test
    void testNullResponse_ReturnsNull() {
        assertNull(HelloWorldMod.extractLastTagContent(null, "[SEARCH]", "[/SEARCH]"));
    }
}
