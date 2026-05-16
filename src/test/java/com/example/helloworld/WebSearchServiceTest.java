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
 * 测试 WebSearchService：验证联网搜索开关的逻辑。
 * Mock 掉 HTTP 调用，不会真正请求 Tavily API。
 */
class WebSearchServiceTest {

    private HttpClient mockHttpClient;
    private WebSearchService searchService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        searchService = new WebSearchService(mockHttpClient);
    }

    @Test
    void testSearchReturnsResults_WhenApiReturns200() throws Exception {
        // 模拟 Tavily API 返回正常搜索结果（紧凑 JSON 格式，与实际 API 一致）
        String fakeResponse = "{\"answer\":\"Minecraft 是一款沙盒游戏\"," +
                "\"results\":[" +
                "{\"title\":\"Minecraft 官网\",\"content\":\"Minecraft 是由 Mojang 开发的沙盒建造游戏\",\"url\":\"https://minecraft.net\"}," +
                "{\"title\":\"Minecraft Wiki\",\"content\":\"Minecraft 百科全书\",\"url\":\"https://minecraft.wiki\"}" +
                "]}";

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(fakeResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = searchService.search("Minecraft 是什么", "fake-api-key");

        // 验证返回了搜索结果
        assertNotNull(result);
        assertTrue(result.contains("Minecraft"));
        assertTrue(result.contains("搜索摘要"));
        assertTrue(result.contains("搜索结果"));
    }

    @Test
    void testSearchReturnsNull_WhenApiReturnsError() throws Exception {
        // 模拟 API 返回 401 错误（API key 无效）
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.body()).thenReturn("{\"error\": \"Invalid API key\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = searchService.search("test query", "invalid-key");

        // API 错误时应返回 null
        assertNull(result);
    }

    @Test
    void testSearchReturnsNull_WhenNetworkException() throws Exception {
        // 模拟网络异常
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("Connection timeout"));

        String result = searchService.search("test query", "fake-key");

        // 网络异常时应返回 null
        assertNull(result);
    }

    @Test
    void testSearchHandlesSpecialCharacters() throws Exception {
        // 测试搜索词包含特殊字符时不会崩溃
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"answer\":\"结果\",\"results\":[{\"title\":\"Test\",\"content\":\"内容\",\"url\":\"https://test.com\"}]}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // 包含引号、换行等特殊字符
        String result = searchService.search("how to use \"quotes\" and\nnewlines", "fake-key");

        // 不应抛异常，应正常返回
        assertNotNull(result);
    }
}
