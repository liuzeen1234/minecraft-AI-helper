package com.example.helloworld;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 模组配置管理，从 config/helloworld.properties 读取 API 配置。
 * 如果配置文件不存在，会自动创建带默认值的文件。
 */
public class ModConfig {

    private static final String CONFIG_FILE = "config/helloworld.properties";

    private String apiBaseUrl;
    private String apiKey;
    private String model;
    private boolean screenshotEnabled;
    private boolean contextEnabled;

    // 默认值
    private static final String DEFAULT_API_BASE_URL = "https://api.kimi.com/coding/v1/messages";
    private static final String DEFAULT_API_KEY = "your-api-key-here";
    private static final String DEFAULT_MODEL = "kimi-for-coding";
    private static final boolean DEFAULT_SCREENSHOT_ENABLED = true;
    private static final boolean DEFAULT_CONTEXT_ENABLED = true;

    public void load() {
        Path configPath = Path.of(CONFIG_FILE);
        Properties props = new Properties();

        if (!Files.exists(configPath)) {
            // 配置文件不存在，创建默认配置
            createDefault(configPath);
        }

        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("读取配置文件失败", e);
        }

        apiBaseUrl = props.getProperty("api_base_url", DEFAULT_API_BASE_URL);
        apiKey = props.getProperty("api_key", DEFAULT_API_KEY);
        model = props.getProperty("model", DEFAULT_MODEL);
        screenshotEnabled = Boolean.parseBoolean(props.getProperty("screenshot_enabled", String.valueOf(DEFAULT_SCREENSHOT_ENABLED)));
        contextEnabled = Boolean.parseBoolean(props.getProperty("context_enabled", String.valueOf(DEFAULT_CONTEXT_ENABLED)));

        HelloWorldMod.LOGGER.info("配置已加载: model={}, url={}, context={}", model, apiBaseUrl, contextEnabled);
    }

    private void createDefault(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());
            Properties props = new Properties();
            props.setProperty("api_base_url", DEFAULT_API_BASE_URL);
            props.setProperty("api_key", DEFAULT_API_KEY);
            props.setProperty("model", DEFAULT_MODEL);
            props.setProperty("screenshot_enabled", String.valueOf(DEFAULT_SCREENSHOT_ENABLED));
            props.setProperty("context_enabled", String.valueOf(DEFAULT_CONTEXT_ENABLED));
            try (OutputStream out = Files.newOutputStream(configPath)) {
                props.store(out, "HelloWorld Mod - AI API Configuration");
            }
            HelloWorldMod.LOGGER.info("已创建默认配置文件: {}", configPath);
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("创建默认配置文件失败", e);
        }
    }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public boolean isScreenshotEnabled() { return screenshotEnabled; }
    public boolean isContextEnabled() { return contextEnabled; }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        save();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        save();
    }

    public void setModel(String model) {
        this.model = model;
        save();
    }

    public void setScreenshotEnabled(boolean screenshotEnabled) {
        this.screenshotEnabled = screenshotEnabled;
        save();
    }

    public void setContextEnabled(boolean contextEnabled) {
        this.contextEnabled = contextEnabled;
        save();
    }

    private void save() {
        Path configPath = Path.of(CONFIG_FILE);
        Properties props = new Properties();
        props.setProperty("api_base_url", apiBaseUrl);
        props.setProperty("api_key", apiKey);
        props.setProperty("model", model);
        props.setProperty("screenshot_enabled", String.valueOf(screenshotEnabled));
        props.setProperty("context_enabled", String.valueOf(contextEnabled));
        try (OutputStream out = Files.newOutputStream(configPath)) {
            props.store(out, "HelloWorld Mod - AI API Configuration");
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("保存配置文件失败", e);
        }
    }
}
