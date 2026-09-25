package com.example.helloworld.structure;

import com.example.helloworld.I18n;
import com.example.helloworld.ModPaths;
import com.example.helloworld.nbt.NbtToTxtConverter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 结构格式转换界面：
 *   左侧 - 源格式下拉（自动识别 / 仅 NBT / 仅 Litematic）+ 待转换文件列表（"+"添加文件，"-"移除单项）
 *   右侧 - 目标格式（目前仅 TXT）+ 输出目录 + 转换后文件名预览列表
 *   底部 - "开始转换" / "返回"
 *
 * 支持一次添加多个 .nbt/.litematic 文件，逐个转换为同名 .txt 输出到目标目录。
 * 源格式下拉仅影响"添加文件"对话框的扩展名过滤（自动识别=不过滤，两种都显示；
 * 仅 NBT/仅 Litematic=只显示对应扩展名）；实际转换始终通过
 * {@code NbtStructureParser#parseAny} 按文件真实扩展名分派解析器，
 * 已添加到列表中的文件不受后续切换下拉影响。
 */
public class StructureConvertScreen extends Screen {

    /** 源格式下拉选项：控制"添加文件"对话框的扩展名过滤，不影响实际转换逻辑。 */
    private enum SourceFormat {
        AUTO("structureconvert.source_format.auto", null, null),
        NBT_ONLY("structureconvert.source_format.nbt", new String[]{"*.nbt"}, "structureconvert.filter_desc.nbt"),
        LITEMATIC_ONLY("structureconvert.source_format.litematic", new String[]{"*.litematic"}, "structureconvert.filter_desc.litematic");

        final String labelKey;
        final String[] filters; // null 表示不过滤（nbt + litematic 都显示）
        final String filterDescKey;

        SourceFormat(String labelKey, String[] filters, String filterDescKey) {
            this.labelKey = labelKey;
            this.filters = filters;
            this.filterDescKey = filterDescKey;
        }

        String label() { return I18n.tr(labelKey); }
    }

    private final Screen parent;

    private final List<File> sourceFiles = new ArrayList<>();

    private TextFieldWidget targetDirField;

    private String statusMessage = "";
    private boolean statusIsError = false;

    private SourceFormat sourceFormat = SourceFormat.AUTO;
    private boolean sourceFormatDropdownOpen = false;

    // 布局区域
    private int listLeft, listTop, listWidth, listHeight;
    private int previewLeft, previewTop, previewWidth, previewHeight;
    private int sourceFormatBoxLeft, sourceFormatBoxTop, sourceFormatBoxWidth, sourceFormatBoxHeight;
    private static final int ITEM_HEIGHT = 14;
    private static final int MINUS_BTN_SIZE = 14;

    public StructureConvertScreen(Screen parent) {
        super(Text.literal(I18n.tr("structureconvert.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int margin = 10;
        int cx = this.width / 2;

        int panelWidth = Math.min(220, (this.width - margin * 3) / 2);
        int leftX = cx - margin / 2 - panelWidth;
        int rightX = cx + margin / 2;

        int top = 70;
        int bottom = this.height - 60;

        // 源格式下拉框：紧跟在"源格式:"标签右侧
        sourceFormatBoxLeft = leftX + 55;
        sourceFormatBoxTop = top - 2;
        sourceFormatBoxWidth = Math.min(110, panelWidth - 55);
        sourceFormatBoxHeight = 16;

        listLeft = leftX;
        listTop = top + 18; // 让出"源格式"下拉行
        listWidth = panelWidth;
        listHeight = bottom - listTop - 20; // 底部留给"+"按钮

        previewLeft = rightX;
        previewTop = top + 60; // 让出"目标格式"行 + 目录选择行
        previewWidth = panelWidth;
        previewHeight = bottom - previewTop;

        // ---- 右侧：目标目录选择 ----
        int dirY = top + 20;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("\uD83D\uDCC1"),
                button -> chooseTargetFolder())
                .dimensions(rightX, dirY, 20, 18)
                .build()
        );
        targetDirField = new TextFieldWidget(this.textRenderer, rightX + 22, dirY, panelWidth - 22, 18,
                Text.literal(I18n.tr("structureconvert.target_dir")));
        targetDirField.setMaxLength(512);
        targetDirField.setPlaceholder(Text.literal(I18n.tr("structureconvert.target_dir.placeholder")));
        targetDirField.setText(ModPaths.getTxtsDir().toAbsolutePath().toString());
        this.addDrawableChild(targetDirField);

        // ---- 底部按钮 ----
        int btnY = this.height - 40;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("structureconvert.button.convert")),
                button -> doConvert())
                .dimensions(cx - 105, btnY, 100, 20)
                .build()
        );
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("button.back")),
                button -> close())
                .dimensions(cx + 5, btnY, 100, 20)
                .build()
        );
    }

    /**
     * 打开原生文件选择对话框（支持多选），选中后追加到列表。
     * 显示哪些扩展名由当前选中的 {@link #sourceFormat} 决定：
     * 自动识别 = .nbt + .litematic 都显示；仅 NBT / 仅 Litematic = 只显示对应一种。
     */
    private void openAddFileDialog() {
        String[] extFilters = sourceFormat.filters != null
                ? sourceFormat.filters : new String[]{"*.nbt", "*.litematic"};
        String filterDesc = sourceFormat.filterDescKey != null
                ? I18n.tr(sourceFormat.filterDescKey) : I18n.tr("structureconvert.filter_desc");

        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(extFilters.length);
            for (String ext : extFilters) {
                filters.put(stack.UTF8(ext));
            }
            filters.flip();

            Path defaultDir = defaultDirForSourceFormat().toAbsolutePath().normalize();
            if (!Files.isDirectory(defaultDir)) {
                defaultDir = Path.of(System.getProperty("user.home"));
            }
            selected = TinyFileDialogs.tinyfd_openFileDialog(
                    I18n.tr("structureconvert.choose_file"),
                    defaultDir.toString() + File.separator,
                    filters,
                    filterDesc,
                    true);
        }
        if (selected == null || selected.isEmpty()) return;

        // tinyfd 多选结果以 '|' 分隔
        LinkedHashSet<String> existing = new LinkedHashSet<>();
        for (File f : sourceFiles) existing.add(f.getAbsolutePath());

        for (String path : selected.split("\\|")) {
            if (path.isBlank()) continue;
            File f = new File(path);
            if (!existing.contains(f.getAbsolutePath())) {
                sourceFiles.add(f);
                existing.add(f.getAbsolutePath());
            }
        }
        statusMessage = "";
    }

    /**
     * 添加文件对话框的默认打开目录：
     *   自动识别 → structures/ 根目录（同时能看到 nbts/、litematic/ 子文件夹）；
     *   仅 NBT → structures/nbts/；仅 Litematic → structures/litematic/。
     */
    private Path defaultDirForSourceFormat() {
        return switch (sourceFormat) {
            case NBT_ONLY -> ModPaths.getNbtsDir();
            case LITEMATIC_ONLY -> ModPaths.getLitematicDir();
            default -> ModPaths.getStructuresDir();
        };
    }

    private void removeSourceFile(int index) {
        if (index >= 0 && index < sourceFiles.size()) {
            sourceFiles.remove(index);
            statusMessage = "";
        }
    }

    /** 打开原生文件夹选择对话框，用于选择输出目录。 */
    private void chooseTargetFolder() {
        Path defaultDir = ModPaths.getTxtsDir().toAbsolutePath().normalize();
        if (!Files.isDirectory(defaultDir)) {
            defaultDir = Path.of(System.getProperty("user.home"));
        }
        String selected = TinyFileDialogs.tinyfd_selectFolderDialog(
                I18n.tr("structureconvert.choose_folder"), defaultDir.toString());
        if (selected != null) {
            targetDirField.setText(selected);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** 逐个转换列表中的所有文件，任一失败不影响其余文件继续转换。 */
    private void doConvert() {
        if (sourceFiles.isEmpty()) {
            statusMessage = I18n.tr("structureconvert.status.no_source");
            statusIsError = true;
            return;
        }

        String targetDirText = targetDirField.getText().trim();
        Path targetDir = targetDirText.isEmpty() ? ModPaths.getTxtsDir() : Path.of(targetDirText);

        int success = 0;
        String lastError = null;
        for (File file : sourceFiles) {
            try {
                NbtToTxtConverter.convertToFile(file, targetDir);
                success++;
            } catch (Exception e) {
                lastError = e.getMessage();
            }
        }

        if (lastError == null) {
            statusMessage = I18n.tr("structureconvert.status.batch_done", success, targetDir.toAbsolutePath());
            statusIsError = false;
        } else {
            statusMessage = I18n.tr("structureconvert.status.partial_done", success, sourceFiles.size(), lastError);
            statusIsError = success == 0;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 12, 0xFFFFFF);

        int top = 70;

        // ---- 左侧：源格式下拉 ----
        context.drawTextWithShadow(this.textRenderer, Text.literal(I18n.tr("structureconvert.source_label")), listLeft, top, 0xFFE080);
        renderSourceFormatDropdown(context, mouseX, mouseY);

        // 左侧文件列表背景
        context.fill(listLeft - 1, listTop - 1, listLeft + listWidth + 1, listTop + listHeight + 1, 0xFFA0A0A0);
        context.fill(listLeft, listTop, listLeft + listWidth, listTop + listHeight, 0xFF000000);

        int rowWidth = listWidth - MINUS_BTN_SIZE - 6;
        for (int i = 0; i < sourceFiles.size(); i++) {
            int itemY = listTop + i * ITEM_HEIGHT;
            if (itemY + ITEM_HEIGHT > listTop + listHeight) break;

            boolean hovered = mouseX >= listLeft && mouseX < listLeft + rowWidth
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered) {
                context.fill(listLeft, itemY, listLeft + rowWidth, itemY + ITEM_HEIGHT, 0xFF303050);
            }

            String name = sourceFiles.get(i).getName();
            drawTruncated(context, name, listLeft + 3, itemY + 3, rowWidth - 6, 0xFFFFFF);

            // "-" 删除按钮
            int minusX = listLeft + listWidth - MINUS_BTN_SIZE - 2;
            boolean minusHovered = mouseX >= minusX && mouseX < minusX + MINUS_BTN_SIZE
                    && mouseY >= itemY && mouseY < itemY + MINUS_BTN_SIZE;
            context.drawTextWithShadow(this.textRenderer, Text.literal(minusHovered ? "§c§l-" : "§c-"),
                    minusX + 3, itemY + 3, 0xFFFFFF);
        }

        // "+" 添加按钮（列表下方）
        int plusY = listTop + listHeight + 3;
        boolean plusHovered = mouseX >= listLeft && mouseX < listLeft + 20
                && mouseY >= plusY && mouseY < plusY + ITEM_HEIGHT;
        context.drawTextWithShadow(this.textRenderer, Text.literal(plusHovered ? "§a§l+" : "§a+"),
                listLeft + 2, plusY, 0xFFFFFF);

        // ---- 右侧：目标格式 ----
        context.drawTextWithShadow(this.textRenderer, Text.literal(I18n.tr("structureconvert.target_label")), previewLeft, top, 0xFFE080);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§aTXT"), previewLeft + 70, top, 0xFFFFFF);

        // 预览标签 + 列表
        context.drawTextWithShadow(this.textRenderer, Text.literal(I18n.tr("structureconvert.preview_label")), previewLeft, previewTop - 12, 0xFFE080);

        context.fill(previewLeft - 1, previewTop - 1, previewLeft + previewWidth + 1, previewTop + previewHeight + 1, 0xFFA0A0A0);
        context.fill(previewLeft, previewTop, previewLeft + previewWidth, previewTop + previewHeight, 0xFF000000);

        for (int i = 0; i < sourceFiles.size(); i++) {
            int itemY = previewTop + i * ITEM_HEIGHT;
            if (itemY + ITEM_HEIGHT > previewTop + previewHeight) break;
            String previewName = stripExtension(sourceFiles.get(i).getName()) + ".txt";
            drawTruncated(context, previewName, previewLeft + 3, itemY + 3, previewWidth - 6, 0x55FF55);
        }
        if (sourceFiles.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(I18n.tr("structureconvert.preview.none")),
                    previewLeft + 3, previewTop + 3, 0x808080);
        }

        // 状态信息
        if (!statusMessage.isEmpty()) {
            int color = statusIsError ? 0xFF5555 : 0x55FF55;
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(statusMessage), cx, this.height - 55, color);
        }

        super.render(context, mouseX, mouseY, delta);

        // 下拉展开的选项列表要盖在其它元素之上，放在最后渲染
        if (sourceFormatDropdownOpen) {
            renderSourceFormatOptions(context, mouseX, mouseY);
        }
    }

    /** 渲染源格式下拉框本体（收起状态下显示的当前选中值 + 边框 + 展开箭头）。 */
    private void renderSourceFormatDropdown(DrawContext context, int mouseX, int mouseY) {
        boolean hovered = isInBox(mouseX, mouseY, sourceFormatBoxLeft, sourceFormatBoxTop, sourceFormatBoxWidth, sourceFormatBoxHeight);

        int borderColor = (hovered || sourceFormatDropdownOpen) ? 0xFFFFFFFF : 0xFFA0A0A0;
        context.fill(sourceFormatBoxLeft - 1, sourceFormatBoxTop - 1,
                sourceFormatBoxLeft + sourceFormatBoxWidth + 1, sourceFormatBoxTop + sourceFormatBoxHeight + 1, borderColor);
        context.fill(sourceFormatBoxLeft, sourceFormatBoxTop,
                sourceFormatBoxLeft + sourceFormatBoxWidth, sourceFormatBoxTop + sourceFormatBoxHeight, 0xFF202020);

        drawTruncated(context, sourceFormat.label(), sourceFormatBoxLeft + 4, sourceFormatBoxTop + 4,
                sourceFormatBoxWidth - 16, 0xFFFFFF);

        // 展开箭头（展开时朝上，收起时朝下）
        String arrow = sourceFormatDropdownOpen ? "▲" : "▼";
        context.drawTextWithShadow(this.textRenderer, Text.literal(arrow),
                sourceFormatBoxLeft + sourceFormatBoxWidth - 10, sourceFormatBoxTop + 4, 0xFFC0C0C0);
    }

    /** 渲染展开状态下悬浮在下拉框下方的选项列表。 */
    private void renderSourceFormatOptions(DrawContext context, int mouseX, int mouseY) {
        SourceFormat[] options = SourceFormat.values();
        int optionsTop = sourceFormatBoxTop + sourceFormatBoxHeight + 1;
        int optionsHeight = options.length * ITEM_HEIGHT;

        context.fill(sourceFormatBoxLeft - 1, optionsTop - 1,
                sourceFormatBoxLeft + sourceFormatBoxWidth + 1, optionsTop + optionsHeight + 1, 0xFFFFFFFF);
        context.fill(sourceFormatBoxLeft, optionsTop,
                sourceFormatBoxLeft + sourceFormatBoxWidth, optionsTop + optionsHeight, 0xFF101010);

        for (int i = 0; i < options.length; i++) {
            int optionY = optionsTop + i * ITEM_HEIGHT;
            boolean optHovered = isInBox(mouseX, mouseY, sourceFormatBoxLeft, optionY, sourceFormatBoxWidth, ITEM_HEIGHT);
            boolean isSelected = options[i] == sourceFormat;

            if (optHovered) {
                context.fill(sourceFormatBoxLeft, optionY, sourceFormatBoxLeft + sourceFormatBoxWidth, optionY + ITEM_HEIGHT, 0xFF404070);
            }
            int textColor = isSelected ? 0xFFFF00 : 0xFFFFFF;
            drawTruncated(context, options[i].label(), sourceFormatBoxLeft + 4, optionY + 3, sourceFormatBoxWidth - 8, textColor);
        }
    }

    private boolean isInBox(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawTruncated(DrawContext context, String text, int x, int y, int maxWidth, int color) {
        String display = text;
        while (this.textRenderer.getWidth(display) > maxWidth && display.length() > 3) {
            display = display.substring(0, display.length() - 1);
        }
        if (!display.equals(text)) display = display + "...";
        context.drawTextWithShadow(this.textRenderer, Text.literal(display), x, y, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 下拉展开时优先处理：点选项则选中并收起，点其它任意位置都先收起下拉（不再继续穿透处理该次点击）
        if (sourceFormatDropdownOpen) {
            SourceFormat[] options = SourceFormat.values();
            int optionsTop = sourceFormatBoxTop + sourceFormatBoxHeight + 1;
            for (int i = 0; i < options.length; i++) {
                int optionY = optionsTop + i * ITEM_HEIGHT;
                if (isInBox(mouseX, mouseY, sourceFormatBoxLeft, optionY, sourceFormatBoxWidth, ITEM_HEIGHT)) {
                    sourceFormat = options[i];
                    sourceFormatDropdownOpen = false;
                    return true;
                }
            }
            sourceFormatDropdownOpen = false;
            return true;
        }

        // 点击源格式下拉框本体：展开选项列表
        if (isInBox(mouseX, mouseY, sourceFormatBoxLeft, sourceFormatBoxTop, sourceFormatBoxWidth, sourceFormatBoxHeight)) {
            sourceFormatDropdownOpen = true;
            return true;
        }

        // 点击 "+" 添加文件
        int plusY = listTop + listHeight + 3;
        if (mouseX >= listLeft && mouseX < listLeft + 20 && mouseY >= plusY && mouseY < plusY + ITEM_HEIGHT) {
            openAddFileDialog();
            return true;
        }

        // 点击某一行的 "-" 删除
        if (mouseX >= listLeft && mouseX < listLeft + listWidth && mouseY >= listTop && mouseY < listTop + listHeight) {
            int minusX = listLeft + listWidth - MINUS_BTN_SIZE - 2;
            if (mouseX >= minusX && mouseX < minusX + MINUS_BTN_SIZE) {
                int index = (int) ((mouseY - listTop) / ITEM_HEIGHT);
                removeSourceFile(index);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
