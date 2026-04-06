package com.example.helloworld;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 通过 Tavily API 进行联网搜索，将搜索结果返回给 AI 作为上下文。
 */
public class WebSearchService {

    private final HttpClient httpClient;

    public WebSearchService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 调用 Tavily Search API，返回搜索结果摘要文本。
     */
    public String search(String query, String apiKey) {
        try {
            String escapedQuery = query
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", " ")
                    .replace("\r", "");

            String requestBody = """
                    {
                        "api_key": "%s",
                        "query": "%s",
                        "search_depth": "basic",
                        "max_results": 5,
                        "include_answer": true
                    }
                    """.formatted(apiKey, escapedQuery);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.tavily.com/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                HelloWorldMod.LOGGER.error("Tavily 搜索失败: HTTP {}", response.statusCode());
                return null;
            }

            return parseSearchResults(response.body());
        } catch (Exception e) {
            HelloWorldMod.LOGGER.error("Tavily 搜索异常", e);
            return null;
        }
    }

    /**
     * 从 Tavily 响应 JSON 中提取搜索结果，拼成文本摘要。
     */
    private String parseSearchResults(String json) {
        StringBuilder sb = new StringBuilder();

        // 提取 answer 字段
        String answer = extractJsonString(json, "answer");
        if (answer != null && !answer.isEmpty()) {
            sb.append("搜索摘要: ").append(answer).append("\n\n");
        }

        // 提取 results 数组中的 title + content
        String resultsMarker = "\"results\":[";
        int resultsStart = json.indexOf(resultsMarker);
        if (resultsStart != -1) {
            sb.append("搜索结果:\n");
            int resultIndex = 1;
            int searchFrom = resultsStart;
            while (resultIndex <= 5) {
                int titleIdx = json.indexOf("\"title\":\"", searchFrom);
                if (titleIdx == -1) break;

                String title = extractValueAt(json, titleIdx + 9);
                int contentIdx = json.indexOf("\"content\":\"", titleIdx);
                String content = contentIdx != -1 ? extractValueAt(json, contentIdx + 11) : "";
                int urlIdx = json.indexOf("\"url\":\"", titleIdx);
                String url = urlIdx != -1 ? extractValueAt(json, urlIdx + 7) : "";

                sb.append(resultIndex).append(". ").append(title).append("\n");
                if (!content.isEmpty()) {
                    // 截断过长的内容
                    if (content.length() > 300) {
                        content = content.substring(0, 300) + "...";
                    }
                    sb.append("   ").append(content).append("\n");
                }
                if (!url.isEmpty()) {
                    sb.append("   来源: ").append(url).append("\n");
                }
                sb.append("\n");

                searchFrom = titleIdx + 1;
                resultIndex++;
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private String extractJsonString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int idx = json.indexOf(marker);
        if (idx == -1) return null;
        return extractValueAt(json, idx + marker.length());
    }

    /**
     * 从指定位置提取 JSON 字符串值（处理转义）。
     */
    private String extractValueAt(String json, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case '/' -> { sb.append('/'); i++; }
                    default -> sb.append(c);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
