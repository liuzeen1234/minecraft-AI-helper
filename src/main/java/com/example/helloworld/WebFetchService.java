package com.example.helloworld;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抓取指定 URL 的网页内容，提取纯文本返回给 AI。
 */
public class WebFetchService {

    private final HttpClient httpClient;
    private static final int MAX_CONTENT_LENGTH = 8000; // 限制返回给 AI 的文本长度

    public WebFetchService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 抓取 URL 并返回提取后的纯文本内容。
     */
    public String fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MinecraftMod/1.0")
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                HelloWorldMod.LOGGER.error("网页抓取失败: HTTP {} for {}", response.statusCode(), url);
                return null;
            }

            String html = response.body();
            String text = extractText(html);

            if (text.length() > MAX_CONTENT_LENGTH) {
                text = text.substring(0, MAX_CONTENT_LENGTH) + "\n...(内容已截断)";
            }

            return text;
        } catch (Exception e) {
            HelloWorldMod.LOGGER.error("网页抓取异常: {}", url, e);
            return null;
        }
    }

    /**
     * 从 HTML 中提取纯文本，去除标签、脚本、样式等。
     */
    private String extractText(String html) {
        // 移除 script 和 style 块
        String cleaned = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        cleaned = cleaned.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        cleaned = cleaned.replaceAll("(?is)<noscript[^>]*>.*?</noscript>", "");
        cleaned = cleaned.replaceAll("(?is)<!--.*?-->", "");

        // 尝试只提取 body 内容
        Pattern bodyPattern = Pattern.compile("(?is)<body[^>]*>(.*?)</body>");
        Matcher bodyMatcher = bodyPattern.matcher(cleaned);
        if (bodyMatcher.find()) {
            cleaned = bodyMatcher.group(1);
        }

        // 把 <br>, <p>, <div>, <li>, <tr>, <h1>-<h6> 等转为换行
        cleaned = cleaned.replaceAll("(?i)<br\\s*/?>", "\n");
        cleaned = cleaned.replaceAll("(?i)</(p|div|li|tr|h[1-6]|section|article)>", "\n");
        cleaned = cleaned.replaceAll("(?i)<(p|div|li|tr|h[1-6]|section|article)[^>]*>", "\n");

        // 移除所有剩余 HTML 标签
        cleaned = cleaned.replaceAll("<[^>]+>", "");

        // 解码常见 HTML 实体
        cleaned = cleaned.replace("&amp;", "&");
        cleaned = cleaned.replace("&lt;", "<");
        cleaned = cleaned.replace("&gt;", ">");
        cleaned = cleaned.replace("&quot;", "\"");
        cleaned = cleaned.replace("&apos;", "'");
        cleaned = cleaned.replace("&#39;", "'");
        cleaned = cleaned.replace("&nbsp;", " ");

        // 清理多余空白
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replaceAll("\\n[ \\t]+", "\n");
        cleaned = cleaned.replaceAll("[ \\t]+\\n", "\n");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        return cleaned.trim();
    }
}
