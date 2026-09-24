package com.example.helloworld;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 国际化工具类（方向一）。
 *
 * <p>文本内容放在 lang 文件中（assets/helloworld/lang/zh_cn.json、en_us.json），
 * 在 mod 启动时加载进内存。显示语言完全跟随 Minecraft 当前游戏语言，不支持手动切换：
 * <ul>
 *   <li>客户端：实时读取 {@link MinecraftClient#getInstance()} 的
 *       {@code getLanguageManager().getLanguage()}，随游戏语言设置变化立即生效。</li>
 *   <li>服务端（专用服务器进程，没有 {@link MinecraftClient} 实例）：固定使用英文。</li>
 * </ul>
 *
 * <p>调用方式：{@code I18n.tr("key.some.text")}，支持 {@code %s / %d} 等参数占位：
 * {@code I18n.tr("cmd.placed", blockName, pos)}。若键缺失则回退为键名本身，便于发现遗漏。
 */
public class I18n {

    private I18n() {}

    /** 中文语言表：key -> 文本 */
    private static Map<String, String> zh = Collections.emptyMap();
    /** 英文语言表：key -> 文本 */
    private static Map<String, String> en = Collections.emptyMap();

    private static final String ZH_PATH = "/assets/helloworld/lang/zh_cn.json";
    private static final String EN_PATH = "/assets/helloworld/lang/en_us.json";

    /**
     * 加载两份 lang 文件到内存。应在 mod 初始化时调用一次。
     * 失败时保持为空表，tr() 会回退为键名。
     */
    public static void load() {
        zh = loadLangFile(ZH_PATH);
        en = loadLangFile(EN_PATH);
        HelloWorldMod.LOGGER.info("语言表已加载: zh_cn={} 条, en_us={} 条", zh.size(), en.size());
    }

    private static Map<String, String> loadLangFile(String resourcePath) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = I18n.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                HelloWorldMod.LOGGER.warn("找不到语言文件: {}", resourcePath);
                return map;
            }
            JsonObject obj = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    map.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            HelloWorldMod.LOGGER.error("加载语言文件失败: {}", resourcePath, e);
        }
        return map;
    }

    /**
     * 将 Minecraft 语言代码归一化为本 mod 支持的两种语言之一。
     * 语言码以 "zh" 开头（zh_cn/zh_tw/zh_hk 等）归为中文，其余归为英文。
     *
     * @param mcLanguage Minecraft 当前语言代码，如 "zh_cn"、"en_us"；为 null 时按中文处理
     * @return "zh_cn" 或 "en_us"
     */
    public static String normalizeLanguage(String mcLanguage) {
        if (mcLanguage != null && mcLanguage.toLowerCase().startsWith("zh")) {
            return "zh_cn";
        }
        return mcLanguage == null ? "zh_cn" : "en_us";
    }

    /**
     * 判断当前是否为英文模式。
     *
     * <p>客户端：实时读取 Minecraft 当前游戏语言并归一化判断；
     * 服务端（{@link MinecraftClient#getInstance()} 为 {@code null}，即专用服务器进程）：固定返回英文。
     */
    public static boolean isEnglish() {
        MinecraftClient client = getClientInstanceSafely();
        if (client == null) {
            // 专用服务器没有客户端语言概念，固定使用英文
            return true;
        }
        if (client.getLanguageManager() == null) {
            return true;
        }
        String mcLanguage = client.getLanguageManager().getLanguage();
        return "en_us".equals(normalizeLanguage(mcLanguage));
    }

    /**
     * 安全获取 {@link MinecraftClient} 实例。
     *
     * <p>专用服务器（dedicated server）进程虽然会加载本类，但 {@code MinecraftClient} 相关的类
     * 只有在客户端环境下才会被初始化；直接调用 {@code MinecraftClient.getInstance()} 在服务端环境
     * 也是安全的（返回 {@code null}），这里额外包一层 try/catch 防御，避免任何环境差异导致的异常
     * 影响到聊天反馈等核心功能。
     */
    private static MinecraftClient getClientInstanceSafely() {
        try {
            return MinecraftClient.getInstance();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 按当前语言解析翻译键，支持参数格式化。
     *
     * @param key  翻译键，如 "cmd.block.placed"
     * @param args 可选的格式化参数（对应文本中的 %s/%d 等）
     * @return 解析后的文本；键缺失时返回键名本身
     */
    public static String tr(String key, Object... args) {
        Map<String, String> table = isEnglish() ? en : zh;
        String template = table.get(key);
        if (template == null) {
            // 回退：先尝试另一种语言，再回退键名，避免直接显示空白
            template = (isEnglish() ? zh : en).get(key);
            if (template == null) {
                return key;
            }
        }
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(template, args);
        } catch (Exception e) {
            // 格式串与参数不匹配时，返回原模板，避免抛异常影响渲染
            return template;
        }
    }

}
