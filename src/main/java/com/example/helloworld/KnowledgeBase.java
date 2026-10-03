package com.example.helloworld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 管理 knowledge/info/ 下的资料，以及 knowledge/structure/ 下的结构参考。
 * 每次请求重新读取 workflow.txt 和目录结构，使用户的编辑立即生效。
 */
public class KnowledgeBase {

    private static final String LANG_DIR_EN = "info";

    /** 知识库根目录。默认走 {@link ModPaths#getKnowledgeDir()}，测试时可注入临时目录。 */
    private final Path knowledgeRootDir;

    public KnowledgeBase() {
        this(ModPaths.getKnowledgeDir());
    }

    /** 测试专用：注入自定义知识库根目录，绕过 {@link ModPaths} 的固定游戏目录路径。 */
    KnowledgeBase(Path knowledgeRootDir) {
        this.knowledgeRootDir = knowledgeRootDir;
    }

    /** 按名称检索的资料目录，不随界面语言切换。 */
    public static String currentLangDirName() {
        return LANG_DIR_EN;
    }

    public static void ensureDefaultDocsReleased() {
        new KnowledgeBase().ensureLayout();
    }

    /** 创建默认结构；已有工作流程及资料保持原样。 */
    void ensureLayout() {
        try {
            Files.createDirectories(knowledgeRootDir);
            Path info = knowledgeRootDir.resolve("info");
            Path legacy = knowledgeRootDir.resolve("basic_info_en");
            if (!Files.exists(info) && Files.isDirectory(legacy)) {
                Files.move(legacy, info);
            }
            Files.createDirectories(info);
            Files.createDirectories(knowledgeRootDir.resolve("structure"));
            Path workflow = knowledgeRootDir.resolve("workflow.txt");
            if (!Files.exists(workflow)) {
                Files.writeString(workflow, "",
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("初始化知识库目录失败: {}", knowledgeRootDir, e);
        }
    }

    /** 每次 AI 请求都读取工作流程全文与完整文件目录，不缓存。 */
    public String buildPromptContext() {
        StringBuilder context = new StringBuilder();
        Path workflow = knowledgeRootDir.resolve("workflow.txt");
        try {
            if (Files.isRegularFile(workflow)) {
                context.append("Knowledge base workflow (workflow.txt):\n")
                        .append(Files.readString(workflow, StandardCharsets.UTF_8)).append("\n\n");
            }
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("读取知识库工作流程失败: {}", workflow, e);
        }
        String tree = buildTreeText();
        if (tree != null) {
            context.append("Knowledge base folder/file structure (paths relative to knowledge/):\n")
                    .append(tree).append("\n");
        }
        return context.toString();
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

    /**
     * 生成知识库根目录（{@link ModPaths#getKnowledgeDir()}）下完整的文件/文件夹树状结构文本，
     * 供 [KNOWLEDGE_TREE] 工具使用。与 {@link #buildDirectoryText()}（仅扫描当前语言子目录、
     * 只列 .md 文档摘要）不同，这里递归列出根目录下所有子目录和文件（不限扩展名、不限语言子目录），
     * 让 AI 能看到完整目录结构后，再用 [KNOWLEDGE_FILE] 点名读取具体文件正文。
     *
     * @return 树状结构文本；知识库根目录不存在或为空时返回 null
     */
    public String buildTreeText() {
        if (!Files.isDirectory(knowledgeRootDir)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try {
            boolean any = appendTree(knowledgeRootDir, "", sb);
            if (!any) {
                return null;
            }
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("扫描知识库目录树失败: {}", knowledgeRootDir, e);
            return null;
        }
        return sb.toString();
    }

    /**
     * 递归拼接目录树。子项按名称排序，目录在前，保证输出稳定，方便 AI 阅读和后续引用路径。
     *
     * @return 是否至少写入了一行（用于判断根目录是否为空）
     */
    private boolean appendTree(Path dir, String indent, StringBuilder sb) throws IOException {
        List<Path> children;
        try (Stream<Path> stream = Files.list(dir)) {
            children = stream.sorted((a, b) -> {
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
                if (aDir != bDir) return aDir ? -1 : 1;
                return a.getFileName().toString().compareTo(b.getFileName().toString());
            }).toList();
        }
        boolean any = false;
        for (Path child : children) {
            any = true;
            String name = child.getFileName().toString();
            if (Files.isDirectory(child)) {
                sb.append(indent).append(name).append("/\n");
                appendTree(child, indent + "  ", sb);
            } else {
                sb.append(indent).append(name).append("\n");
            }
        }
        return any;
    }

    /**
     * 按 [KNOWLEDGE_FILE] 标签点名的相对路径读取知识库目录下任意文件的原始文本内容。
     * 路径需以 [KNOWLEDGE_TREE] 返回的树状结构中出现的相对路径为准，支持多级子目录
     * （如 "basic_info_ch/方块特性/全方块图鉴.md"）。会做路径穿越校验，防止越界读取
     * 知识库目录之外的文件。
     *
     * @param relativePath 相对知识库根目录的路径
     * @param maxChars     返回内容的字符数上限，超出部分截断并附加提示
     * @return 文件内容（可能带截断提示），或以 "ERROR:" 开头的错误说明
     */
    public String readFile(String relativePath, int maxChars) {
        if (relativePath == null || relativePath.isBlank()) {
            return "ERROR: 未指定文件路径。";
        }
        Path target;
        try {
            target = ModPaths.resolveWithinBase(knowledgeRootDir, relativePath);
        } catch (IOException e) {
            return "ERROR: 非法路径: " + relativePath;
        }
        if (!Files.isRegularFile(target)) {
            return "ERROR: 文件不存在: " + relativePath;
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            if (maxChars > 0 && content.length() > maxChars) {
                return content.substring(0, maxChars) + "\n\n（内容过长，已截断，仅展示前 " + maxChars + " 字符）";
            }
            return content;
        } catch (IOException e) {
            HelloWorldMod.LOGGER.error("读取知识库文件失败: {}", target, e);
            return "ERROR: 读取文件失败: " + e.getMessage();
        }
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
