package com.example.helloworld;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RAG 知识库：管理 ai-helper/knowledge/ 目录下的 Markdown 文档。
 *
 * <p>职责：
 * <ul>
 *   <li>加载知识库子目录（basic_info_en）下的所有 .md 文档；</li>
 *   <li>解析每篇文档的 YAML front matter（title/category/keywords/summary）与正文；</li>
 *   <li>生成不含正文的目录索引，供 system prompt 注入，让 AI 判断是否需要查阅；</li>
 *   <li>按文档名（[KNOWLEDGE] 标签点名）检索正文，并应用 maxDocs/maxChars 上限。</li>
 * </ul>
 *
 * <p>知识库文档只维护英文一份（{@code basic_info_en}），不随 mod 界面语言切换：
 * 知识库是给 AI 检索用的原始资料，AI 具备英文阅读能力，检索到英文正文后仍会按
 * system prompt 中的语言指令用玩家的语言作答，因此不需要为减小 jar 体积而重复
 * 维护中文版知识库。{@code loadDocs(String)}/{@code retrieve(...)} 等方法保留了
 * 可指定语言子目录名的重载，供测试或未来恢复多语言知识库时使用。
 *
 * <p>知识库在每次请求时按需重新加载（文档数量少，直接读盘即可，不做常驻缓存，
 * 方便用户增删文档后无需重启即可生效）。
 */
public class KnowledgeBase {

    private static final String LANG_DIR_EN = "basic_info_en";

    /** 打包在 jar 内的默认知识库资源根路径，随 manifest.txt 列出各语言目录下的文档相对路径。 */
    private static final String BUNDLED_RESOURCE_ROOT = "/assets/helloworld/knowledge";

    /** 知识库根目录。默认走 {@link ModPaths#getKnowledgeDir()}，测试时可注入临时目录。 */
    private final Path knowledgeRootDir;

    public KnowledgeBase() {
        this(ModPaths.getKnowledgeDir());
    }

    /** 测试专用：注入自定义知识库根目录，绕过 {@link ModPaths} 的固定游戏目录路径。 */
    KnowledgeBase(Path knowledgeRootDir) {
        this.knowledgeRootDir = knowledgeRootDir;
    }

    /**
     * 返回知识库使用的子目录名。目前固定为英文版（{@code basic_info_en}），
     * 不随 mod 界面语言切换——见类注释。
     */
    public static String currentLangDirName() {
        return LANG_DIR_EN;
    }

    /**
     * 首次启动释放默认知识库文档到运行目录。
     *
     * <p>仅当目标目录（ai-helper/knowledge/basic_info_en/）不存在或为空时才释放，
     * 已存在内容（包括用户自行修改/新增的文档）不会被覆盖。应在 mod 初始化时调用一次。
     */
    public static void ensureDefaultDocsReleased() {
        releaseLangDirIfEmpty(LANG_DIR_EN);
    }

    private static void releaseLangDirIfEmpty(String langDirName) {
        Path targetDir = ModPaths.getKnowledgeDir().resolve(langDirName);
        try {
            if (Files.isDirectory(targetDir)) {
                try (Stream<Path> existing = Files.list(targetDir)) {
                    if (existing.findAny().isPresent()) {
                        return; // 目录已有内容（首次释放过或用户自建），不覆盖
                    }
                }
            }
            List<String> relativePaths = readManifest(langDirName);
            if (relativePaths.isEmpty()) {
                return;
            }
            for (String relativePath : relativePaths) {
                String resourcePath = BUNDLED_RESOURCE_ROOT + "/" + langDirName + "/" + relativePath;
                try (InputStream in = KnowledgeBase.class.getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        HelloWorldMod.LOGGER.warn("默认知识库资源缺失: {}", resourcePath);
                        continue;
                    }
                    Path destFile = targetDir.resolve(relativePath);
                    Files.createDirectories(destFile.getParent());
                    Files.copy(in, destFile);
                } catch (IOException e) {
                    HelloWorldMod.LOGGER.error("释放知识库文档失败: {}", resourcePath, e);
                }
            }
            HelloWorldMod.LOGGER.info("已释放默认知识库文档到 {} ({} 篇)", targetDir, relativePaths.size());
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("释放默认知识库文档失败: {}", targetDir, e);
        }
    }

    /**
     * 读取打包在 jar 内的 manifest.txt，列出该语言目录下所有文档的相对路径（每行一个，UTF-8）。
     * manifest 由构建时一并复制到 knowledge/{langDirName}/manifest.txt，避免运行时在 jar 内递归列目录
     * （中文子目录名在部分 classpath 实现下不便直接枚举）。
     */
    private static List<String> readManifest(String langDirName) {
        String manifestPath = BUNDLED_RESOURCE_ROOT + "/" + langDirName + "/manifest.txt";
        List<String> lines = new ArrayList<>();
        try (InputStream in = KnowledgeBase.class.getResourceAsStream(manifestPath)) {
            if (in == null) {
                HelloWorldMod.LOGGER.warn("知识库 manifest 缺失: {}", manifestPath);
                return lines;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
            }
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("读取知识库 manifest 失败: {}", manifestPath, e);
        }
        return lines;
    }

    /**
     * 加载当前语言对应的知识库文档列表。目录不存在或为空时返回空列表。
     */
    public List<KnowledgeDoc> loadDocs() {
        return loadDocs(currentLangDirName());
    }

    /** 测试专用：加载指定子目录（如 "basic_info_en"）下的文档列表，可传入任意目录名用于测试隔离。 */
    List<KnowledgeDoc> loadDocs(String langDirName) {
        Path langDir = knowledgeRootDir.resolve(langDirName);
        List<KnowledgeDoc> docs = new ArrayList<>();
        if (!Files.isDirectory(langDir)) {
            return docs;
        }
        try (Stream<Path> paths = Files.walk(langDir)) {
            List<Path> mdFiles = paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".md")).toList();
            for (Path file : mdFiles) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    KnowledgeDoc doc = parseDoc(file, langDir, content);
                    if (doc != null) {
                        docs.add(doc);
                    }
                } catch (IOException e) {
                    HelloWorldMod.LOGGER.error("读取知识库文档失败: {}", file, e);
                }
            }
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("扫描知识库目录失败: {}", langDir, e);
        }
        return docs;
    }

    /**
     * 生成知识库目录文本（不含正文），供 system prompt 注入。
     * 每行一篇文档：name、title、category、keywords、summary。
     * 知识库为空时返回 null。
     */
    public String buildDirectoryText() {
        return buildDirectoryText(loadDocs());
    }

    /** 测试专用：基于指定语言子目录生成目录文本。 */
    String buildDirectoryText(String langDirName) {
        return buildDirectoryText(loadDocs(langDirName));
    }

    private String buildDirectoryText(List<KnowledgeDoc> docs) {
        if (docs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeDoc doc : docs) {
            sb.append(doc.toDirectoryEntry()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 按 [KNOWLEDGE] 标签点名的文档名检索正文，应用数量与字符上限。
     *
     * @param requestedNames AI 点名的文档名列表（对应 KnowledgeDoc.getName()，忽略大小写与首尾空格）
     * @param maxDocs        最多返回的文档数（超出的点名被忽略）
     * @param maxChars       返回正文的总字符数上限（超出部分整篇截断，不切碎单篇内容）
     * @return 拼接好的检索结果文本（含未命中提示），点名为空或知识库为空时返回 null
     */
    public String retrieve(List<String> requestedNames, int maxDocs, int maxChars) {
        return retrieve(requestedNames, maxDocs, maxChars, currentLangDirName());
    }

    /** 测试专用：基于指定语言子目录检索文档正文。 */
    String retrieve(List<String> requestedNames, int maxDocs, int maxChars, String langDirName) {
        if (requestedNames == null || requestedNames.isEmpty()) {
            return null;
        }
        List<KnowledgeDoc> docs = loadDocs(langDirName);
        if (docs.isEmpty()) {
            return null;
        }
        Map<String, KnowledgeDoc> byName = new LinkedHashMap<>();
        for (KnowledgeDoc doc : docs) {
            byName.put(doc.getName().toLowerCase(), doc);
        }

        List<String> hitNames = new ArrayList<>();
        List<String> missedNames = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int usedChars = 0;
        int usedDocs = 0;

        for (String rawName : requestedNames) {
            if (rawName == null || rawName.isBlank()) continue;
            String key = rawName.trim().toLowerCase();
            if (usedDocs >= maxDocs) {
                break;
            }
            KnowledgeDoc doc = byName.get(key);
            if (doc == null) {
                missedNames.add(rawName.trim());
                continue;
            }
            String body = doc.getBody();
            if (usedChars + body.length() > maxChars && usedDocs > 0) {
                // 已有至少一篇成功注入，后续超出字符上限的文档整篇跳过，避免切碎内容
                missedNames.add(rawName.trim() + "（超出长度上限，未注入）");
                continue;
            }
            sb.append("## ").append(doc.getTitle()).append(" (").append(doc.getName()).append(")\n\n")
              .append(body).append("\n\n");
            usedChars += body.length();
            usedDocs++;
            hitNames.add(rawName.trim());
        }

        if (hitNames.isEmpty() && missedNames.isEmpty()) {
            return null;
        }
        if (!missedNames.isEmpty()) {
            sb.append("（以下文档未在知识库中找到或因长度上限被跳过: ").append(String.join(", ", missedNames)).append("）\n");
        }
        return sb.toString();
    }

    /**
     * 解析单篇 Markdown 文档：拆出 YAML front matter 与正文。
     * front matter 格式简单（key: value 或 key: [a, b, c]），不支持嵌套结构，
     * 与项目现有手写解析风格一致，不引入 YAML 第三方库。
     */
    private KnowledgeDoc parseDoc(Path file, Path langDir, String content) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);

        String relativePath = langDir.relativize(file).toString();

        String title = null, category = null, summary = null;
        List<String> keywords = new ArrayList<>();
        String body = content;

        String trimmed = content.stripLeading();
        if (trimmed.startsWith("---")) {
            int firstMarker = content.indexOf("---");
            int secondMarker = content.indexOf("---", firstMarker + 3);
            // 兼容 "---\n...\n---\n" 两种换行位置
            int nextNewlineAfterSecond = -1;
            if (secondMarker != -1) {
                // 确保第二个 --- 是独占一行的分隔符，而不是正文里偶然出现的字符
                int lineEnd = content.indexOf('\n', secondMarker);
                nextNewlineAfterSecond = lineEnd == -1 ? content.length() : lineEnd;
            }
            if (secondMarker != -1) {
                String frontMatter = content.substring(firstMarker + 3, secondMarker);
                body = content.substring(Math.min(nextNewlineAfterSecond + 1, content.length()));
                for (String line : frontMatter.split("\n")) {
                    String l = line.trim();
                    if (l.isEmpty()) continue;
                    int colon = l.indexOf(':');
                    if (colon == -1) continue;
                    String key = l.substring(0, colon).trim();
                    String value = l.substring(colon + 1).trim();
                    switch (key) {
                        case "title" -> title = value;
                        case "category" -> category = value;
                        case "summary" -> summary = value;
                        case "keywords" -> keywords = parseKeywordList(value);
                        default -> { /* version/source 等字段目前不需要，忽略 */ }
                    }
                }
            }
        }

        body = body.strip();
        return new KnowledgeDoc(name, relativePath, title, category, keywords, summary, body);
    }

    /** 解析形如 "[苦力怕, creeper, 爆炸]" 的关键词列表。 */
    private List<String> parseKeywordList(String value) {
        List<String> result = new ArrayList<>();
        String v = value.trim();
        if (v.startsWith("[") && v.endsWith("]")) {
            v = v.substring(1, v.length() - 1);
        }
        for (String part : v.split(",")) {
            String kw = part.trim();
            if (!kw.isEmpty()) {
                result.add(kw);
            }
        }
        return result;
    }
}
