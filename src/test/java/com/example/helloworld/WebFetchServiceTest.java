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
 * 测试 WebFetchService：验证网页抓取功能。
 * Mock 掉 HTTP 调用，不会真正请求外部网页。
 */
class WebFetchServiceTest {

    private HttpClient mockHttpClient;
    private WebFetchService fetchService;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        fetchService = new WebFetchService(mockHttpClient);
    }

    @Test
    void testFetchReturnsText_WhenPageLoadsSuccessfully() throws Exception {
        // 模拟一个简单的 HTML 页面
        String fakeHtml = """
                <html>
                <head><title>Test Page</title></head>
                <body>
                    <h1>Hello World</h1>
                    <p>This is a test paragraph with useful content.</p>
                    <script>var x = 1;</script>
                    <style>.hidden { display: none; }</style>
                </body>
                </html>
                """;

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(fakeHtml);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = fetchService.fetch("https://example.com");

        // 应该提取出文本内容
        assertNotNull(result);
        assertTrue(result.contains("Hello World"));
        assertTrue(result.contains("test paragraph"));
        // 不应包含 script/style 内容
        assertFalse(result.contains("var x = 1"));
        assertFalse(result.contains(".hidden"));
    }

    @Test
    void testFetchReturnsNull_WhenHttpError() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockResponse.body()).thenReturn("Not Found");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = fetchService.fetch("https://example.com/nonexistent");

        assertNull(result);
    }

    @Test
    void testFetchReturnsNull_WhenNetworkException() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("DNS resolution failed"));

        String result = fetchService.fetch("https://invalid-domain.example");

        assertNull(result);
    }

    @Test
    void testFetchTruncatesLongContent() throws Exception {
        // 生成超过 8000 字符的内容
        StringBuilder longContent = new StringBuilder("<html><body>");
        for (int i = 0; i < 1000; i++) {
            longContent.append("<p>This is paragraph number ").append(i).append(" with some filler text.</p>");
        }
        longContent.append("</body></html>");

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(longContent.toString());
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = fetchService.fetch("https://example.com/long-page");

        assertNotNull(result);
        // 内容应被截断
        assertTrue(result.contains("内容已截断"));
        assertTrue(result.length() <= 8100); // 8000 + 截断提示文字
    }

    @Test
    void testFetchStripsHtmlEntities() throws Exception {
        String htmlWithEntities = """
                <html><body>
                <p>5 &gt; 3 &amp; 2 &lt; 4</p>
                <p>&quot;Hello&quot; &amp; &apos;World&apos;</p>
                </body></html>
                """;

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(htmlWithEntities);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        String result = fetchService.fetch("https://example.com");

        assertNotNull(result);
        assertTrue(result.contains("5 > 3 & 2 < 4"));
        assertTrue(result.contains("\"Hello\" & 'World'"));
    }
}
