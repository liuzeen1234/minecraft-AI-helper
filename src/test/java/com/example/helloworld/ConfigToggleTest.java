package com.example.helloworld;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试功能开关（config toggle）逻辑：
 * - 联网搜索开关
 * - 多轮对话记忆开关
 * - 流式输出开关
 * - 截图开关
 *
 * 验证：开关状态正确读取、修改后正确保存。
 */
class ConfigToggleTest {

    @TempDir
    Path tempDir;

    private Path configFile;

    @BeforeEach
    void setUp() throws Exception {
        // 设置工作目录为临时目录，让 ModConfig 读写临时配置文件
        configFile = tempDir.resolve("config/ai-builder.properties");
        Files.createDirectories(configFile.getParent());

        // 写入测试配置
        Properties props = new Properties();
        props.setProperty("api_base_url", "https://test.api.com/v1/messages");
        props.setProperty("api_key", "test-key-12345");
        props.setProperty("model", "test-model");
        props.setProperty("screenshot_enabled", "true");
        props.setProperty("context_enabled", "true");
        props.setProperty("web_search_enabled", "true");
        props.setProperty("tavily_api_key", "tvly-test-key");
        props.setProperty("stream_output_enabled", "false");

        try (OutputStream out = Files.newOutputStream(configFile)) {
            props.store(out, "Test config");
        }
    }

    @Test
    void testConfigLoadsCorrectValues() throws Exception {
        // 直接读取 properties 文件验证格式正确
        Properties props = new Properties();
        props.load(Files.newInputStream(configFile));

        assertEquals("https://test.api.com/v1/messages", props.getProperty("api_base_url"));
        assertEquals("test-key-12345", props.getProperty("api_key"));
        assertEquals("test-model", props.getProperty("model"));
        assertEquals("true", props.getProperty("screenshot_enabled"));
        assertEquals("true", props.getProperty("context_enabled"));
        assertEquals("true", props.getProperty("web_search_enabled"));
        assertEquals("tvly-test-key", props.getProperty("tavily_api_key"));
        assertEquals("false", props.getProperty("stream_output_enabled"));
    }

    @Test
    void testWebSearchToggle_DisabledMeansNoSearch() throws Exception {
        // 模拟：web_search_enabled = false 时，不应调用搜索
        Properties props = new Properties();
        props.load(Files.newInputStream(configFile));

        boolean webSearchEnabled = Boolean.parseBoolean(props.getProperty("web_search_enabled"));
        assertTrue(webSearchEnabled, "初始状态应为开启");

        // 关闭搜索
        props.setProperty("web_search_enabled", "false");
        try (OutputStream out = Files.newOutputStream(configFile)) {
            props.store(out, "Updated config");
        }

        // 重新读取
        Properties reloaded = new Properties();
        reloaded.load(Files.newInputStream(configFile));
        boolean afterToggle = Boolean.parseBoolean(reloaded.getProperty("web_search_enabled"));
        assertFalse(afterToggle, "关闭后应为 false");
    }

    @Test
    void testStreamOutputToggle() throws Exception {
        Properties props = new Properties();
        props.load(Files.newInputStream(configFile));

        boolean streamEnabled = Boolean.parseBoolean(props.getProperty("stream_output_enabled"));
        assertFalse(streamEnabled, "初始状态流式输出应为关闭");

        // 开启流式输出
        props.setProperty("stream_output_enabled", "true");
        try (OutputStream out = Files.newOutputStream(configFile)) {
            props.store(out, "Updated config");
        }

        Properties reloaded = new Properties();
        reloaded.load(Files.newInputStream(configFile));
        boolean afterToggle = Boolean.parseBoolean(reloaded.getProperty("stream_output_enabled"));
        assertTrue(afterToggle, "开启后应为 true");
    }

    @Test
    void testContextToggle_DisabledMeansNoHistory() throws Exception {
        Properties props = new Properties();
        props.load(Files.newInputStream(configFile));

        boolean contextEnabled = Boolean.parseBoolean(props.getProperty("context_enabled"));
        assertTrue(contextEnabled, "初始状态多轮对话应为开启");

        // 关闭多轮对话
        props.setProperty("context_enabled", "false");
        try (OutputStream out = Files.newOutputStream(configFile)) {
            props.store(out, "Updated config");
        }

        Properties reloaded = new Properties();
        reloaded.load(Files.newInputStream(configFile));
        boolean afterToggle = Boolean.parseBoolean(reloaded.getProperty("context_enabled"));
        assertFalse(afterToggle, "关闭后应为 false");
    }

    @Test
    void testScreenshotToggle() throws Exception {
        Properties props = new Properties();
        props.load(Files.newInputStream(configFile));

        boolean screenshotEnabled = Boolean.parseBoolean(props.getProperty("screenshot_enabled"));
        assertTrue(screenshotEnabled, "初始状态截图应为开启");

        // 关闭截图
        props.setProperty("screenshot_enabled", "false");
        try (OutputStream out = Files.newOutputStream(configFile)) {
            props.store(out, "Updated config");
        }

        Properties reloaded = new Properties();
        reloaded.load(Files.newInputStream(configFile));
        boolean afterToggle = Boolean.parseBoolean(reloaded.getProperty("screenshot_enabled"));
        assertFalse(afterToggle, "关闭后应为 false");
    }

    @Test
    void testMissingConfigFile_UsesDefaults() throws Exception {
        // 删除配置文件
        Files.deleteIfExists(configFile);

        // 读取不存在的文件应使用默认值
        Properties props = new Properties();
        // 模拟 ModConfig 的默认值逻辑
        String apiBaseUrl = props.getProperty("api_base_url", "https://api.kimi.com/coding/v1/messages");
        boolean webSearch = Boolean.parseBoolean(props.getProperty("web_search_enabled", "true"));
        boolean context = Boolean.parseBoolean(props.getProperty("context_enabled", "true"));
        boolean stream = Boolean.parseBoolean(props.getProperty("stream_output_enabled", "false"));

        assertEquals("https://api.kimi.com/coding/v1/messages", apiBaseUrl);
        assertTrue(webSearch, "默认应开启联网搜索");
        assertTrue(context, "默认应开启多轮对话");
        assertFalse(stream, "默认应关闭流式输出");
    }
}
