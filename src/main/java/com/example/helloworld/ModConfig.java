package com.example.helloworld;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 模组配置管理，从 ai-helper/config/ai-builder.properties 读取 API 配置。
 * 如果配置文件不存在，会自动创建带默认值的文件。
 */
public class ModConfig {

    private String apiBaseUrl;
    private String apiKey;
    private String model;
    private boolean screenshotEnabled;
    private boolean contextEnabled;
    private boolean webSearchEnabled;
    private String tavilyApiKey;
    private boolean streamOutputEnabled;
    private int maxToolRounds;
    private boolean vanillaCommandsEnabled;
    private boolean confirmBeforeExecuteEnabled;
    private String language;
    private String apiFormat;

    // 默认值
    private static final String DEFAULT_API_BASE_URL = "https://api.kimi.com/coding/v1/messages";
    private static final String DEFAULT_API_KEY = "your-api-key-here";
    private static final String DEFAULT_MODEL = "kimi-for-coding";
    private static final boolean DEFAULT_SCREENSHOT_ENABLED = false;
    private static final boolean DEFAULT_CONTEXT_ENABLED = true;
    private static final boolean DEFAULT_WEB_SEARCH_ENABLED = true;
    private static final String DEFAULT_TAVILY_API_KEY = "";
    private static final boolean DEFAULT_STREAM_OUTPUT_ENABLED = true;
    // 多轮工具调用的最大轮数：0=禁用（单层行为），N>0=最多允许 N 轮工具调用后必须给出最终答复
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 3;
    // 是否允许 AI 使用原版命令（execute_command）：关闭时 system prompt 不会出现该指令说明，
    // AI 只能使用 mod 自带的具体功能（ACTION/BLUEPRINT 等）
    private static final boolean DEFAULT_VANILLA_COMMANDS_ENABLED = true;
    // 是否要求玩家在 AI 执行 mod 自定义功能（ACTION/BLUEPRINT）前先在聊天框确认：
    // 关闭时保持原有行为（AI 决定即执行），开启后需玩家点击 [是] 才会真正执行，默认关闭以保持现有体验。
    private static final boolean DEFAULT_CONFIRM_BEFORE_EXECUTE_ENABLED = false;
    private static final String DEFAULT_LANGUAGE = "en_us";
    // API 格式：auto（自动检测）/ openai / anthropic
    private static final String DEFAULT_API_FORMAT = "auto";

    // API 格式常量
    public static final String FORMAT_OPENAI = "openai";
    public static final String FORMAT_ANTHROPIC = "anthropic";

    public void load() {
        Path configPath = ModPaths.getConfigFile();
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
        webSearchEnabled = Boolean.parseBoolean(props.getProperty("web_search_enabled", String.valueOf(DEFAULT_WEB_SEARCH_ENABLED)));
        tavilyApiKey = props.getProperty("tavily_api_key", DEFAULT_TAVILY_API_KEY);
        streamOutputEnabled = Boolean.parseBoolean(props.getProperty("stream_output_enabled", String.valueOf(DEFAULT_STREAM_OUTPUT_ENABLED)));
        maxToolRounds = parseMaxToolRounds(props.getProperty("max_tool_rounds", String.valueOf(DEFAULT_MAX_TOOL_ROUNDS)));
        vanillaCommandsEnabled = Boolean.parseBoolean(props.getProperty("vanilla_commands_enabled", String.valueOf(DEFAULT_VANILLA_COMMANDS_ENABLED)));
        confirmBeforeExecuteEnabled = Boolean.parseBoolean(props.getProperty("confirm_before_execute_enabled", String.valueOf(DEFAULT_CONFIRM_BEFORE_EXECUTE_ENABLED)));
        language = props.getProperty("language", DEFAULT_LANGUAGE);
        apiFormat = props.getProperty("api_format", DEFAULT_API_FORMAT);

        HelloWorldMod.LOGGER.info("配置已加载: model={}, url={}, context={}, webSearch={}, stream={}, maxToolRounds={}, vanillaCommands={}, confirmBeforeExecute={}, language={}, apiFormat={}(生效={})", model, apiBaseUrl, contextEnabled, webSearchEnabled, streamOutputEnabled, maxToolRounds, vanillaCommandsEnabled, confirmBeforeExecuteEnabled, language, apiFormat, getEffectiveApiFormat());
    }

    /** 解析 max_tool_rounds，非法值回退默认，并 clamp 到 >=0。 */
    private static int parseMaxToolRounds(String raw) {
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            HelloWorldMod.LOGGER.warn("max_tool_rounds 配置值非法: {}，回退默认 {}", raw, DEFAULT_MAX_TOOL_ROUNDS);
            return DEFAULT_MAX_TOOL_ROUNDS;
        }
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
            props.setProperty("web_search_enabled", String.valueOf(DEFAULT_WEB_SEARCH_ENABLED));
            props.setProperty("tavily_api_key", DEFAULT_TAVILY_API_KEY);
            props.setProperty("stream_output_enabled", String.valueOf(DEFAULT_STREAM_OUTPUT_ENABLED));
            props.setProperty("max_tool_rounds", String.valueOf(DEFAULT_MAX_TOOL_ROUNDS));
            props.setProperty("vanilla_commands_enabled", String.valueOf(DEFAULT_VANILLA_COMMANDS_ENABLED));
            props.setProperty("confirm_before_execute_enabled", String.valueOf(DEFAULT_CONFIRM_BEFORE_EXECUTE_ENABLED));
            props.setProperty("language", DEFAULT_LANGUAGE);
            props.setProperty("api_format", DEFAULT_API_FORMAT);
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
    public boolean isWebSearchEnabled() { return webSearchEnabled; }
    public String getTavilyApiKey() { return tavilyApiKey; }
    public boolean isStreamOutputEnabled() { return streamOutputEnabled; }

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

    public void setWebSearchEnabled(boolean webSearchEnabled) {
        this.webSearchEnabled = webSearchEnabled;
        save();
    }

    public void setTavilyApiKey(String tavilyApiKey) {
        this.tavilyApiKey = tavilyApiKey;
        save();
    }

    public void setStreamOutputEnabled(boolean streamOutputEnabled) {
        this.streamOutputEnabled = streamOutputEnabled;
        save();
    }

    /** 多轮工具调用最大轮数：0=禁用多轮，N>0=最多 N 轮工具调用。 */
    public int getMaxToolRounds() { return maxToolRounds; }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = Math.max(0, maxToolRounds);
        save();
    }

    /**
     * 是否允许 AI 使用原版命令（execute_command）。
     * 关闭时，system prompt 不会包含 execute_command 相关说明，且运行时也会二次拦截，
     * AI 只能使用 mod 自带的具体功能（放置方块、给物品、生成实体等），不能建议任何原版命令。
     */
    public boolean isVanillaCommandsEnabled() { return vanillaCommandsEnabled; }

    public void setVanillaCommandsEnabled(boolean vanillaCommandsEnabled) {
        this.vanillaCommandsEnabled = vanillaCommandsEnabled;
        save();
    }

    /**
     * 是否要求玩家在 AI 执行 mod 自定义功能（放置方块、建造蓝图、给物品、查询等 ACTION/BLUEPRINT 操作）前，
     * 先在聊天框点击 [是]/[否] 确认。关闭时保持原有行为（AI 决定即执行）。
     */
    public boolean isConfirmBeforeExecuteEnabled() { return confirmBeforeExecuteEnabled; }

    public void setConfirmBeforeExecuteEnabled(boolean confirmBeforeExecuteEnabled) {
        this.confirmBeforeExecuteEnabled = confirmBeforeExecuteEnabled;
        save();
    }

    public String getLanguage() { return language; }

    public void setLanguage(String language) {
        this.language = language;
        save();
    }

    /** 用户配置的原始 API 格式（auto / openai / anthropic）。 */
    public String getApiFormat() { return apiFormat; }

    public void setApiFormat(String apiFormat) {
        this.apiFormat = apiFormat;
        save();
    }

    /**
     * 返回实际生效的 API 格式（openai 或 anthropic）。
     * 当配置为 auto 时，根据 api_base_url 和 api_key 自动检测：
     * - URL 含 /chat/completions 或以 /v1 结尾，或 key 以 sk- 开头 → OpenAI
     * - URL 含 anthropic 或 /messages → Anthropic
     * - 默认回退到 OpenAI（当前最通用的格式）
     */
    public String getEffectiveApiFormat() {
        if (FORMAT_OPENAI.equalsIgnoreCase(apiFormat)) return FORMAT_OPENAI;
        if (FORMAT_ANTHROPIC.equalsIgnoreCase(apiFormat)) return FORMAT_ANTHROPIC;

        // auto 检测
        String url = apiBaseUrl == null ? "" : apiBaseUrl.toLowerCase();
        if (url.contains("anthropic") || url.contains("/messages")) {
            return FORMAT_ANTHROPIC;
        }
        if (url.contains("/chat/completions") || url.contains("/v1")) {
            return FORMAT_OPENAI;
        }
        // 兜底：默认按 OpenAI 兼容格式处理
        return FORMAT_OPENAI;
    }

    /** 是否使用 OpenAI 兼容格式。 */
    public boolean isOpenAiFormat() {
        return FORMAT_OPENAI.equals(getEffectiveApiFormat());
    }

    /**
     * 返回用于发送请求的完整端点 URL。
     * OpenAI 格式下，如果用户仅配置到 /v1（或未含 completions 路径），自动补全 /chat/completions。
     */
    public String getResolvedEndpoint() {
        String url = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        if (isOpenAiFormat()) {
            String lower = url.toLowerCase();
            if (!lower.contains("/chat/completions")) {
                // 去掉结尾斜杠后拼接
                String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
                return base + "/chat/completions";
            }
        }
        return url;
    }

    private void save() {
        Path configPath = ModPaths.getConfigFile();
        Properties props = new Properties();
        props.setProperty("api_base_url", apiBaseUrl);
        props.setProperty("api_key", apiKey);
        props.setProperty("model", model);
        props.setProperty("screenshot_enabled", String.valueOf(screenshotEnabled));
        props.setProperty("context_enabled", String.valueOf(contextEnabled));
        props.setProperty("web_search_enabled", String.valueOf(webSearchEnabled));
        props.setProperty("tavily_api_key", tavilyApiKey);
        props.setProperty("stream_output_enabled", String.valueOf(streamOutputEnabled));
        props.setProperty("max_tool_rounds", String.valueOf(maxToolRounds));
        props.setProperty("vanilla_commands_enabled", String.valueOf(vanillaCommandsEnabled));
        props.setProperty("confirm_before_execute_enabled", String.valueOf(confirmBeforeExecuteEnabled));
        props.setProperty("language", language);
        props.setProperty("api_format", apiFormat);
        try (OutputStream out = Files.newOutputStream(configPath)) {
            props.store(out, "HelloWorld Mod - AI API Configuration");
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("保存配置文件失败", e);
        }
    }
}
