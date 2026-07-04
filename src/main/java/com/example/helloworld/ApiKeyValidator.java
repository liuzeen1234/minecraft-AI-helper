package com.example.helloworld;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * AI API Key 验证工具类。
 * 提供快速格式检查和异步网络验证两种能力。
 */
public class ApiKeyValidator {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String DEFAULT_API_KEY = "your-api-key-here";

    private ApiKeyValidator() {}

    /**
     * 验证结果
     */
    public enum ValidationResult {
        VALID,           // API 有效
        EMPTY,           // 未填写
        DEFAULT_VALUE,   // 未修改默认值
        INVALID_FORMAT,  // 格式不正确（太短）
        AUTH_FAILED,     // 认证失败（401/403）
        NETWORK_ERROR,   // 网络错误
        UNKNOWN_ERROR    // 未知错误
    }

    /**
     * 快速格式检查（不发网络请求），适合进入世界时调用。
     * 返回 null 表示格式看起来正常，否则返回问题描述。
     */
    public static ValidationResult quickCheck(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ValidationResult.EMPTY;
        }
        if (DEFAULT_API_KEY.equals(apiKey.trim())) {
            return ValidationResult.DEFAULT_VALUE;
        }
        if (apiKey.trim().length() < 10) {
            return ValidationResult.INVALID_FORMAT;
        }
        return ValidationResult.VALID;
    }

    /**
     * 异步验证 API Key（发送一个最小请求）。
     * 完成后通过 callback 回调结果。
     */
    public static CompletableFuture<ValidationResult> validateAsync(String apiBaseUrl, String apiKey, String model) {
        // 先做快速检查
        ValidationResult quickResult = quickCheck(apiKey);
        if (quickResult != ValidationResult.VALID) {
            return CompletableFuture.completedFuture(quickResult);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 发送一个最小的请求来验证 API Key
                String requestBody = """
                        {
                            "model": "%s",
                            "max_tokens": 1,
                            "system": "Reply with OK",
                            "messages": [{"role": "user", "content": "hi"}]
                        }
                        """.formatted(model);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiBaseUrl))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    return ValidationResult.VALID;
                } else if (statusCode == 401 || statusCode == 403) {
                    return ValidationResult.AUTH_FAILED;
                } else {
                    // 其它状态码（如 429 限流）也认为 key 本身可能是有效的，只是暂时不可用
                    // 但 400 可能是 model 不对，仍然说明配置有问题
                    if (statusCode == 400) {
                        return ValidationResult.UNKNOWN_ERROR;
                    }
                    return ValidationResult.VALID; // 429, 500 等不代表 key 无效
                }
            } catch (java.net.ConnectException | java.net.http.HttpTimeoutException e) {
                return ValidationResult.NETWORK_ERROR;
            } catch (Exception e) {
                HelloWorldMod.LOGGER.warn("API Key 验证异常", e);
                return ValidationResult.NETWORK_ERROR;
            }
        });
    }

    /**
     * 获取验证结果对应的用户提示消息（带颜色代码）
     */
    public static String getResultMessage(ValidationResult result) {
        return switch (result) {
            case VALID -> I18n.get(
                    "§a[AI Builder] API 连接验证成功！",
                    "§a[AI Builder] API connection verified successfully!");
            case EMPTY -> I18n.get(
                    "§c[AI Builder] API Key 未填写！请按 K 键打开设置界面配置 API Key。",
                    "§c[AI Builder] API Key is empty! Press K to open settings and configure your API Key.");
            case DEFAULT_VALUE -> I18n.get(
                    "§c[AI Builder] API Key 仍为默认值，请按 K 键打开设置界面填写有效的 API Key。",
                    "§c[AI Builder] API Key is still the default value. Press K to open settings and enter a valid API Key.");
            case INVALID_FORMAT -> I18n.get(
                    "§c[AI Builder] API Key 格式不正确（过短），请检查是否填写完整。",
                    "§c[AI Builder] API Key format is invalid (too short). Please check if it's complete.");
            case AUTH_FAILED -> I18n.get(
                    "§c[AI Builder] API Key 验证失败（认证错误），请检查 Key 是否正确或已过期。",
                    "§c[AI Builder] API Key validation failed (auth error). Please check if the key is correct or expired.");
            case NETWORK_ERROR -> I18n.get(
                    "§e[AI Builder] API 连接测试失败（网络错误），请检查网络连接和 API URL 是否正确。",
                    "§e[AI Builder] API connection test failed (network error). Please check your network and API URL.");
            case UNKNOWN_ERROR -> I18n.get(
                    "§e[AI Builder] API 验证返回异常，请检查 API URL 和 Model 配置是否正确。",
                    "§e[AI Builder] API validation returned an error. Please check your API URL and Model settings.");
        };
    }
}
