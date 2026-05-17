package com.example.helloworld;

/**
 * 简易国际化工具类。
 * 根据 ModConfig 中的 language 设置返回对应语言的文本。
 */
public class I18n {

    private I18n() {}

    /**
     * 判断当前是否为英文模式
     */
    public static boolean isEnglish() {
        ModConfig config = HelloWorldMod.getConfig();
        return config != null && "en_us".equals(config.getLanguage());
    }

    /**
     * 根据当前语言返回中文或英文文本
     */
    public static String get(String zhText, String enText) {
        return isEnglish() ? enText : zhText;
    }
}
