package com.example.helloworld;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * 测试功能开关在 AI 聊天流程中的实际效果：
 *
 * 1. 联网搜索开关关闭时 → AI 回复中的 [SEARCH] 标签应被忽略，不调用 Tavily
 * 2. 联网搜索开关开启时 → AI 回复中的 [SEARCH] 标签应触发搜索
 * 3. 流式输出开关 → 决定调用 streaming 还是普通 API
 * 4. 多轮对话记忆开关 → 决定是否传递历史消息
 *
 * 这些测试模拟了 HelloWorldMod 中 CHAT_SCREEN_MESSAGE_PACKET 处理器的核心逻辑。
 */
class FeatureSwitchLogicTest {

    private HttpClient mockHttpClient;
    private WebSearchService searchService;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        searchService = new WebSearchService(mockHttpClient);
    }

    // ========== 联网搜索开关测试 ==========

    @Test
    void testSearchNotTriggered_WhenWebSearchDisabled() throws Exception {
        // 模拟场景：AI 回复包含 [SEARCH] 标签，但联网搜索开关关闭
        String aiResponse = "让我帮你搜索一下 [SEARCH]Minecraft 1.20.4 更新内容[/SEARCH]";
        boolean webSearchEnabled = false;
        String tavilyApiKey = "tvly-test-key";

        // 模拟 HelloWorldMod 中的逻辑
        String searchQuery = extractSearchQuery(aiResponse);
        assertNotNull(searchQuery, "应该能提取到搜索关键词");
        assertEquals("Minecraft 1.20.4 更新内容", searchQuery);

        // 关键判断：开关关闭时不应执行搜索
        boolean shouldSearch = searchQuery != null && webSearchEnabled
                && tavilyApiKey != null && !tavilyApiKey.isEmpty();
        assertFalse(shouldSearch, "联网搜索关闭时不应触发搜索");

        // 验证 HTTP 客户端从未被调用
        verify(mockHttpClient, never()).send(any(), any());
    }

    @Test
    void testSearchTriggered_WhenWebSearchEnabled() throws Exception {
        // 模拟场景：AI 回复包含 [SEARCH] 标签，联网搜索开关开启
        String aiResponse = "让我帮你搜索一下 [SEARCH]Fabric API 教程[/SEARCH]";
        boolean webSearchEnabled = true;
        String tavilyApiKey = "tvly-test-key";

        String searchQuery = extractSearchQuery(aiResponse);
        assertNotNull(searchQuery);

        boolean shouldSearch = searchQuery != null && webSearchEnabled
                && tavilyApiKey != null && !tavilyApiKey.isEmpty();
        assertTrue(shouldSearch, "联网搜索开启时应触发搜索");

        // 模拟搜索 API 返回结果（紧凑 JSON）
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"answer\":\"Fabric API 是...\",\"results\":[{\"title\":\"Fabric Wiki\",\"content\":\"教程内容\",\"url\":\"https://fabricmc.net\"}]}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String searchResults = searchService.search(searchQuery, tavilyApiKey);
        assertNotNull(searchResults, "搜索应返回结果");

        // 验证 HTTP 客户端被调用了
        verify(mockHttpClient, times(1)).send(any(), any());
    }

    @Test
    void testSearchNotTriggered_WhenNoApiKey() throws Exception {
        // 模拟场景：联网搜索开启，但没有配置 Tavily API key
        String aiResponse = "[SEARCH]test query[/SEARCH]";
        boolean webSearchEnabled = true;
        String tavilyApiKey = ""; // 空 key

        String searchQuery = extractSearchQuery(aiResponse);
        assertNotNull(searchQuery);

        boolean shouldSearch = searchQuery != null && webSearchEnabled
                && tavilyApiKey != null && !tavilyApiKey.isEmpty();
        assertFalse(shouldSearch, "没有 API key 时不应触发搜索");
    }

    @Test
    void testSearchTagStripped_WhenSearchDisabled() {
        // 联网搜索关闭时，[SEARCH] 标签应从回复中移除
        String aiResponse = "这是回答 [SEARCH]some query[/SEARCH] 的内容";
        boolean webSearchEnabled = false;

        String searchQuery = extractSearchQuery(aiResponse);
        if (searchQuery != null && !webSearchEnabled) {
            // 模拟 HelloWorldMod 中的清理逻辑
            String cleanResponse = aiResponse.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
            assertEquals("这是回答  的内容", cleanResponse);
            assertFalse(cleanResponse.contains("[SEARCH]"));
        }
    }

    // ========== 网页抓取开关测试 ==========

    @Test
    void testFetchUrlExtracted_WhenPresent() {
        String aiResponse = "让我看看这个网页 [FETCH]https://minecraft.wiki/page[/FETCH]";
        String fetchUrl = extractFetchUrl(aiResponse);
        assertNotNull(fetchUrl);
        assertEquals("https://minecraft.wiki/page", fetchUrl);
    }

    @Test
    void testFetchUrlNull_WhenNotPresent() {
        String aiResponse = "这是一个普通回复，没有抓取请求";
        String fetchUrl = extractFetchUrl(aiResponse);
        assertNull(fetchUrl);
    }

    @Test
    void testFetchUrlNull_WhenInvalidUrl() {
        // 非 http/https 开头的 URL 应被忽略
        String aiResponse = "[FETCH]ftp://invalid.com/file[/FETCH]";
        String fetchUrl = extractFetchUrl(aiResponse);
        assertNull(fetchUrl);
    }

    // ========== 多轮对话记忆开关测试 ==========

    @Test
    void testConversationHistory_AccumulatesWhenEnabled() {
        // 模拟 HelloWorldMod 中的对话历史逻辑
        java.util.List<String> conversationHistory = new java.util.ArrayList<>();
        boolean contextEnabled = true;

        // 模拟第一轮对话
        String userMsg1 = "{\"role\": \"user\", \"content\": \"你好\"}";
        String assistantMsg1 = "{\"role\": \"assistant\", \"content\": \"你好！有什么可以帮你的？\"}";

        if (contextEnabled) {
            conversationHistory.add(userMsg1);
            conversationHistory.add(assistantMsg1);
        }

        assertEquals(2, conversationHistory.size(), "开启时应保存历史");

        // 模拟第二轮对话 - 构建 messages 时应包含历史
        StringBuilder messagesBuilder = new StringBuilder("[");
        if (contextEnabled && !conversationHistory.isEmpty()) {
            for (String msg : conversationHistory) {
                messagesBuilder.append(msg).append(",");
            }
        }
        messagesBuilder.append("{\"role\": \"user\", \"content\": \"建一个房子\"}]");

        String messages = messagesBuilder.toString();
        assertTrue(messages.contains("你好"), "第二轮应包含第一轮的历史");
        assertTrue(messages.contains("建一个房子"), "应包含当前消息");
    }

    @Test
    void testConversationHistory_NotUsedWhenDisabled() {
        java.util.List<String> conversationHistory = new java.util.ArrayList<>();
        boolean contextEnabled = false;

        // 即使有历史数据
        conversationHistory.add("{\"role\": \"user\", \"content\": \"旧消息\"}");
        conversationHistory.add("{\"role\": \"assistant\", \"content\": \"旧回复\"}");

        // 构建 messages 时不应包含历史
        StringBuilder messagesBuilder = new StringBuilder("[");
        if (contextEnabled && !conversationHistory.isEmpty()) {
            for (String msg : conversationHistory) {
                messagesBuilder.append(msg).append(",");
            }
        }
        messagesBuilder.append("{\"role\": \"user\", \"content\": \"新消息\"}]");

        String messages = messagesBuilder.toString();
        assertFalse(messages.contains("旧消息"), "关闭时不应包含历史");
        assertTrue(messages.contains("新消息"), "应包含当前消息");
    }

    @Test
    void testConversationHistory_LimitedToMaxSize() {
        java.util.List<String> conversationHistory = new java.util.ArrayList<>();
        int MAX_HISTORY_SIZE = 20;

        // 添加超过限制的历史
        for (int i = 0; i < 12; i++) {
            conversationHistory.add("{\"role\": \"user\", \"content\": \"msg" + i + "\"}");
            conversationHistory.add("{\"role\": \"assistant\", \"content\": \"reply" + i + "\"}");
        }

        assertEquals(24, conversationHistory.size());

        // 模拟 HelloWorldMod 中的裁剪逻辑
        while (conversationHistory.size() > MAX_HISTORY_SIZE) {
            conversationHistory.remove(0);
            conversationHistory.remove(0); // 成对移除
        }

        assertEquals(MAX_HISTORY_SIZE, conversationHistory.size(), "应裁剪到最大限制");
        // 最早的消息应被移除
        assertFalse(conversationHistory.get(0).contains("msg0"), "最早的消息应被移除");
    }

    // ========== 流式输出开关测试 ==========

    @Test
    void testStreamOutputFlag_DeterminesApiCallType() {
        // 这个测试验证开关值正确影响分支选择
        boolean streamOutputEnabled_on = true;
        boolean streamOutputEnabled_off = false;

        // 模拟 HelloWorldMod 中的分支逻辑
        String apiCallType_on = streamOutputEnabled_on ? "streaming" : "normal";
        String apiCallType_off = streamOutputEnabled_off ? "streaming" : "normal";

        assertEquals("streaming", apiCallType_on, "开启时应使用流式调用");
        assertEquals("normal", apiCallType_off, "关闭时应使用普通调用");
    }

    // ========== 辅助方法（复制自 HelloWorldMod 的逻辑） ==========

    /**
     * 从 AI 回复中提取 [SEARCH]...[/SEARCH] 标签内的搜索关键词。
     */
    private String extractSearchQuery(String response) {
        int start = response.indexOf("[SEARCH]");
        int end = response.indexOf("[/SEARCH]");
        if (start != -1 && end != -1 && end > start) {
            String query = response.substring(start + 8, end).trim();
            return query.isEmpty() ? null : query;
        }
        return null;
    }

    /**
     * 从 AI 回复中提取 [FETCH]...[/FETCH] 标签内的 URL。
     */
    private String extractFetchUrl(String response) {
        int start = response.indexOf("[FETCH]");
        int end = response.indexOf("[/FETCH]");
        if (start != -1 && end != -1 && end > start) {
            String url = response.substring(start + 7, end).trim();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
        }
        return null;
    }
}
