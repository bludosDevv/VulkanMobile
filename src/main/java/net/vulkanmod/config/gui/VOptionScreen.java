package net.vulkanmod.config.gui;

import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.UpdateChecker;
import net.vulkanmod.config.gui.render.GuiRenderer;
import net.vulkanmod.config.gui.widget.VAbstractWidget;
import net.vulkanmod.config.gui.widget.VButtonWidget;
import net.vulkanmod.config.option.OptionPage;
import net.vulkanmod.config.option.Options;
import net.vulkanmod.config.shader.ShaderPackManager;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.ColorUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class VOptionScreen extends Screen {
    public static final int MARGIN = 20;
    public static final int RED = ColorUtil.ARGB.pack(0.3f, 0.0f, 0.0f, 0.8f);

    private final ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("vulkanmod", "vlogo_transparent.png");
    private final Screen parent;
    private final List<OptionPage> optionPages;

    private int currentListIdx = 0;
    private int shadersPageIdx = -1;
    private List<Path> shaderPackEntries = List.of();

    private int tooltipX;
    private int tooltipY;
    private int tooltipWidth;

    private VButtonWidget supportButton;
    private VButtonWidget doneButton;
    private VButtonWidget applyButton;

    private ShaderPackSelectionList shaderPackSelectionList;
    private ResolutionScaleSlider resolutionScaleSlider;
    private VButtonWidget shaderApplyButton;
    private VButtonWidget shaderSettingsButton;
    private VButtonWidget shaderDoneButton;

    private String activeShaderPack;
    private String pendingShaderPack;

    private final List<VButtonWidget> pageButtons = Lists.newArrayList();
    private final List<VButtonWidget> buttons = Lists.newArrayList();

    public VOptionScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
        this.optionPages = new ArrayList<>();
    }

    private void addPages() {
        this.optionPages.clear();

        this.optionPages.add(new OptionPage(Component.translatable("vulkanmod.options.pages.video").getString(), Options.getVideoOpts()));
        this.optionPages.add(new OptionPage(Component.translatable("vulkanmod.options.pages.graphics").getString(), Options.getGraphicsOpts()));
        this.optionPages.add(new OptionPage(Component.translatable("vulkanmod.options.pages.optimizations").getString(), Options.getOptimizationOpts()));
        this.optionPages.add(new OptionPage(Component.translatable("vulkanmod.options.pages.other").getString(), Options.getOtherOpts()));

        this.shadersPageIdx = this.optionPages.size();
        this.optionPages.add(new OptionPage(Component.translatable("vulkanmod.options.pages.shaders").getString(), new OptionBlock[0]));
    }

    @Override
    protected void init() {
        this.addPages();
        this.shaderPackEntries = ShaderPackManager.listShaderPackEntries();
        this.activeShaderPack = System.getProperty("vulkanmod.shaderpack", "E-LITE");
        this.pendingShaderPack = this.activeShaderPack;

        int top = 40;
        int bottom = 60;
        int itemHeight = 20;

        int leftMargin = MARGIN + 90;
        int listWidth = Math.min(this.width - leftMargin - MARGIN, 420);
        int listHeight = this.height - top - bottom;

        this.buildLists(leftMargin, top, listWidth, listHeight, itemHeight);

        int x = leftMargin + listWidth + 10;
        int width = this.width - x - 10;
        int y = 50;

        if (width < 200) {
            x = 100;
            width = listWidth;
            y = this.height - bottom + 10;
        }

        this.tooltipX = x;
        this.tooltipY = y;
        this.tooltipWidth = width;

        this.buildPage();
        this.applyButton.active = false;
    }

    private void buildLists(int left, int top, int listWidth, int listHeight, int itemHeight) {
        for (OptionPage page : this.optionPages) {
            page.createList(left, top, listWidth, listHeight, itemHeight);
            page.updateOptionStates();
        }
    }

    private void addPageButtons(int x0, int y0, int width, int height, boolean verticalLayout) {
        int x = x0;
        int y = y0;

        for (int i = 0; i < this.optionPages.size(); ++i) {
            OptionPage page = this.optionPages.get(i);
            final int finalIdx = i;

            VButtonWidget widget = new VButtonWidget(x, y, width, height, Component.nullToEmpty(page.name), button -> this.setOptionList(finalIdx));
            this.buttons.add(widget);
            this.pageButtons.add(widget);
            this.addWidget(widget);

            if (verticalLayout) {
                y += height + 1;
            } else {
                x += width + 1;
            }
        }

        this.pageButtons.get(this.currentListIdx).setSelected(true);
    }

    private void buildPage() {
        this.buttons.clear();
        this.pageButtons.clear();
        this.clearWidgets();

        this.addPageButtons(MARGIN, 40, 80, 22, true);

        VOptionList currentList = this.optionPages.get(this.currentListIdx).getOptionList();
        this.addWidget(currentList);

        this.addButtons();
        this.initShaderWidgets();
        this.updateShaderUiVisibility();
    }

    private void addButtons() {
        int rightMargin = 20;
        int buttonHeight = 20;
        int padding = 10;
        int buttonMargin = 5;

        int buttonWidth = this.minecraft.font.width(CommonComponents.GUI_DONE) + 2 * padding;
        int x0 = this.width - buttonWidth - rightMargin;
        int y0 = this.height - buttonHeight - 7;

        this.doneButton = new VButtonWidget(x0, y0, buttonWidth, buttonHeight, CommonComponents.GUI_DONE, button -> this.minecraft.setScreen(this.parent));

        buttonWidth = this.minecraft.font.width(Component.translatable("vulkanmod.options.buttons.apply")) + 2 * padding;
        x0 -= buttonWidth + buttonMargin;
        this.applyButton = new VButtonWidget(x0, y0, buttonWidth, buttonHeight, Component.translatable("vulkanmod.options.buttons.apply"), button -> this.applyOptions());

        buttonWidth = this.minecraft.font.width(Component.translatable("vulkanmod.options.buttons.kofi")) + 10;
        x0 = this.width - buttonWidth - rightMargin;
        this.supportButton = new VButtonWidget(x0, 6, buttonWidth, buttonHeight, Component.translatable("vulkanmod.options.buttons.kofi"), button -> Util.getPlatform().openUri("https://ko-fi.com/xcollateral"));

        this.buttons.add(this.applyButton);
        this.buttons.add(this.doneButton);
        this.buttons.add(this.supportButton);

        this.addWidget(this.applyButton);
        this.addWidget(this.doneButton);
        this.addWidget(this.supportButton);

        if (UpdateChecker.isUpdateAvailable()) {
            int updateWidth = this.minecraft.font.width(Component.translatable("vulkanmod.options.buttons.update_available")) + 10;
            VButtonWidget updateButton = new VButtonWidget(
                    x0 - updateWidth - buttonMargin,
                    6,
                    updateWidth,
                    buttonHeight,
                    Component.translatable("vulkanmod.options.buttons.update_available").withStyle(ChatFormatting.UNDERLINE),
                    button -> Util.getPlatform().openUri("https://modrinth.com/mod/vulkanmod")
            );

            this.buttons.add(updateButton);
            this.addWidget(updateButton);
        }
    }

    private void initShaderWidgets() {
        int panelX = MARGIN + 110;
        int panelWidth = this.width - panelX - MARGIN;
        int listTop = 72;
        int listBottom = this.height - 96;

        this.shaderPackSelectionList = new ShaderPackSelectionList(this, this.minecraft, panelWidth, listBottom - listTop, listTop, listBottom, panelX);
        this.addWidget(this.shaderPackSelectionList);
        this.rebuildShaderSelectionList();

        int sliderWidth = Math.min(260, panelWidth);
        int sliderX = panelX + (panelWidth - sliderWidth) / 2;
        this.resolutionScaleSlider = new ResolutionScaleSlider(sliderX, 46, sliderWidth, 20, 1.0f);
        this.addWidget(this.resolutionScaleSlider);

        int bottomY = this.height - 28;
        int buttonHeight = 20;
        int buttonWidth = Math.min(120, (panelWidth - 10) / 3);
        int buttonsX = panelX + (panelWidth - ((buttonWidth * 3) + 10)) / 2;

        this.shaderApplyButton = new VButtonWidget(buttonsX, bottomY, buttonWidth, buttonHeight, Component.translatable("vulkanmod.options.buttons.apply"), button -> this.applyShaderPackSelection());
        this.shaderSettingsButton = new VButtonWidget(buttonsX + buttonWidth + 5, bottomY, buttonWidth, buttonHeight, Component.literal("Shader Settings"), button -> Initializer.LOGGER.info("Shader Settings placeholder clicked"));
        this.shaderDoneButton = new VButtonWidget(buttonsX + ((buttonWidth + 5) * 2), bottomY, buttonWidth, buttonHeight, CommonComponents.GUI_DONE, button -> this.minecraft.setScreen(this.parent));

        this.buttons.add(this.shaderApplyButton);
        this.buttons.add(this.shaderSettingsButton);
        this.buttons.add(this.shaderDoneButton);

        this.addWidget(this.shaderApplyButton);
        this.addWidget(this.shaderSettingsButton);
        this.addWidget(this.shaderDoneButton);

        this.shaderApplyButton.active = false;
    }

    private void rebuildShaderSelectionList() {
        this.shaderPackEntries = ShaderPackManager.listShaderPackEntries();
        this.shaderPackSelectionList.children().clear();

        if (this.shaderPackEntries.isEmpty()) {
            this.shaderPackSelectionList.addPackEntry(new ShaderPackSelectionList.Entry(this.shaderPackSelectionList, null, Component.translatable("vulkanmod.options.shaders.empty")));
            this.shaderPackSelectionList.setSelected(null);
            return;
        }

        ShaderPackSelectionList.Entry selectedEntry = null;

        for (Path path : this.shaderPackEntries) {
            String fileName = path.getFileName().toString();
            ShaderPackSelectionList.Entry entry = new ShaderPackSelectionList.Entry(this.shaderPackSelectionList, path, Component.literal(fileName));
            this.shaderPackSelectionList.addPackEntry(entry);

            if (fileName.equals(this.pendingShaderPack)) {
                selectedEntry = entry;
            }
        }

        if (selectedEntry == null && !this.shaderPackSelectionList.children().isEmpty()) {
            selectedEntry = this.shaderPackSelectionList.children().get(0);
            this.pendingShaderPack = selectedEntry.getPackName();
        }

        this.shaderPackSelectionList.setSelected(selectedEntry);
        this.updateShaderButtonsState();
    }

    private void updateShaderUiVisibility() {
        boolean isShadersPage = this.currentListIdx == this.shadersPageIdx;

        for (VButtonWidget button : this.buttons) {
            boolean shaderOnly = button == this.shaderApplyButton || button == this.shaderSettingsButton || button == this.shaderDoneButton;
            boolean optionOnly = button == this.applyButton || button == this.doneButton || button == this.supportButton;

            if (shaderOnly) {
                button.visible = isShadersPage;
            } else if (optionOnly) {
                button.visible = !isShadersPage;
            }
        }

        if (this.shaderPackSelectionList != null) {
            this.shaderPackSelectionList.visible = isShadersPage;
            this.shaderPackSelectionList.active = isShadersPage;
        }

        if (this.resolutionScaleSlider != null) {
            this.resolutionScaleSlider.visible = isShadersPage;
            this.resolutionScaleSlider.active = isShadersPage;
        }

        this.updateShaderButtonsState();
    }

    private void updateShaderButtonsState() {
        if (this.shaderApplyButton == null) {
            return;
        }

        boolean hasSelection = this.pendingShaderPack != null && !this.pendingShaderPack.isBlank();
        this.shaderApplyButton.active = hasSelection && !this.pendingShaderPack.equals(this.activeShaderPack);
    }

    private void onShaderSelected(ShaderPackSelectionList.Entry entry) {
        this.pendingShaderPack = entry != null ? entry.getPackName() : null;
        this.updateShaderButtonsState();
    }

    private void applyShaderPackSelection() {
        if (this.pendingShaderPack == null || this.pendingShaderPack.isBlank()) {
            return;
        }

        this.activeShaderPack = this.pendingShaderPack;
        System.setProperty("vulkanmod.shaderpack", this.activeShaderPack);

        try {
            Vulkan.waitIdle();
            PipelineManager.destroyPipelines();
            PipelineManager.init();
            Initializer.LOGGER.info("Applied shader pack '{}' with resolution scale {}", this.activeShaderPack, this.resolutionScaleSlider.getScale());
        } catch (Exception e) {
            Initializer.LOGGER.error("Failed to apply shader pack '{}'.", this.activeShaderPack, e);
        }

        this.updateShaderButtonsState();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        for (GuiEventListener element : this.children()) {
            if (element.mouseClicked(event, bl)) {
                this.setFocused(element);
                if (event.button() == 0) {
                    this.setDragging(true);
                }

                this.updateState();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.setDragging(false);
        this.updateState();
        return this.getChildAt(event.x(), event.y())
                .filter(guiEventListener -> guiEventListener.mouseReleased(event))
                .isPresent();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        GuiRenderer.guiGraphics = guiGraphics;
        VRenderSystem.enableBlend();

        int size = 36;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.icon, MARGIN + 40 - 18, 4, 0f, 0f, size, size, size, size);

        VOptionList currentList = this.optionPages.get(this.currentListIdx).getOptionList();
        currentList.updateState(mouseX, mouseY);

        if (this.currentListIdx == this.shadersPageIdx) {
            this.renderShadersPanel(guiGraphics, mouseX, mouseY, delta);
        } else {
            currentList.renderWidget(mouseX, mouseY);

            List<FormattedCharSequence> list = getHoveredButtonTooltip(currentList, mouseX, mouseY);
            if (list != null) {
                this.renderTooltip(list, this.tooltipX, this.tooltipY);
            }
        }

        this.renderButtons(mouseX, mouseY);
    }

    private void renderShadersPanel(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        int panelX = MARGIN + 100;
        int panelY = 40;
        int panelWidth = this.width - panelX - MARGIN;
        int panelBottom = this.height - 34;

        int bgColor = ColorUtil.ARGB.pack(0.08f, 0.08f, 0.08f, 0.75f);
        GuiRenderer.fill(panelX, panelY, panelX + panelWidth, panelBottom, bgColor);
        GuiRenderer.renderBorder(panelX, panelY, panelX + panelWidth, panelBottom, 1, RED);

        GuiRenderer.drawCenteredString(this.font, Component.translatable("vulkanmod.options.pages.shaders"), panelX + panelWidth / 2, panelY + 8, 0xFFFFFFFF);

        if (this.shaderPackSelectionList != null && this.shaderPackSelectionList.visible) {
            this.shaderPackSelectionList.renderWidget(guiGraphics, mouseX, mouseY, delta);
        }

        if (this.resolutionScaleSlider != null && this.resolutionScaleSlider.visible) {
            this.resolutionScaleSlider.render(mouseX, mouseY);
        }
    }

    public void renderButtons(int mouseX, int mouseY) {
        for (VButtonWidget button : this.buttons) {
            if (button.visible) {
                button.render(mouseX, mouseY);
            }
        }
    }

    private void renderTooltip(List<FormattedCharSequence> list, int x, int y) {
        int padding = 3;
        int width = GuiRenderer.getMaxTextWidth(this.font, list);
        int height = list.size() * 10;
        float intensity = 0.05f;
        int color = ColorUtil.ARGB.pack(intensity, intensity, intensity, 0.6f);
        GuiRenderer.fill(x - padding, y - padding, x + width + padding, y + height + padding, color);

        color = RED;
        GuiRenderer.renderBorder(x - padding, y - padding, x + width + padding, y + height + padding, 1, color);

        int yOffset = 0;
        for (FormattedCharSequence text : list) {
            GuiRenderer.drawString(this.font, text, x, y + yOffset, 0xffffffff);
            yOffset += 10;
        }
    }

    private List<FormattedCharSequence> getHoveredButtonTooltip(VOptionList buttonList, int mouseX, int mouseY) {
        VAbstractWidget widget = buttonList.getHoveredWidget(mouseX, mouseY);
        if (widget != null) {
            Component tooltip = widget.getTooltip();
            if (tooltip == null) {
                return null;
            }

            return this.font.split(tooltip, this.tooltipWidth);
        }

        return null;
    }

    private void updateState() {
        if (this.currentListIdx == this.shadersPageIdx) {
            return;
        }

        boolean modified = false;
        for (OptionPage page : this.optionPages) {
            modified |= page.optionChanged();
        }

        if (modified) {
            for (OptionPage page : this.optionPages) {
                page.optionChanged();
            }
        }

        this.applyButton.active = modified;
    }

    private void setOptionList(int i) {
        this.currentListIdx = i;
        this.buildPage();
        this.pageButtons.get(i).setSelected(true);
    }

    private void applyOptions() {
        List<OptionPage> pages = List.copyOf(this.optionPages);
        for (OptionPage page : pages) {
            page.applyOptionChanges();
            page.updateOptionStates();
        }

        Initializer.CONFIG.write();
    }

    private static final class ResolutionScaleSlider extends VAbstractWidget {
        private float value;
        private boolean dragging;

        private ResolutionScaleSlider(int x, int y, int width, int height, float initialValue) {
            this.setPosition(x, y, width, height);
            this.value = Mth.clamp((initialValue - 0.5f) / 1.5f, 0.0f, 1.0f);
        }

        @Override
        public void renderWidget(double mX, double mY) {
            int bg = ColorUtil.ARGB.pack(0.0f, 0.0f, 0.0f, this.active ? 0.45f : 0.3f);
            GuiRenderer.fill(this.x, this.y, this.x + this.width, this.y + this.height, bg);
            this.renderHovering(0, 0);

            int knobX = this.x + (int) (this.value * (this.width - 8));
            int knobColor = ColorUtil.ARGB.pack(0.85f, 0.85f, 0.85f, 1.0f);
            GuiRenderer.fill(knobX, this.y + 2, knobX + 8, this.y + this.height - 2, knobColor);

            Component text = Component.literal("Resolution Scale: %.2fx".formatted(this.getScale()));
            GuiRenderer.drawCenteredString(Minecraft.getInstance().font, text, this.x + this.width / 2, this.y + 6, 0xFFFFFFFF);
        }

        @Override
        public void onClick(double mX, double mY) {
            this.dragging = true;
            this.setValueFromMouse(mX);
        }

        @Override
        public void onRelease(double mX, double mY) {
            this.dragging = false;
        }

        @Override
        protected void onDrag(double mX, double mY, double f, double g) {
            if (this.dragging) {
                this.setValueFromMouse(mX);
            }
        }

        private void setValueFromMouse(double mouseX) {
            double normalized = (mouseX - this.x - 4.0) / (this.width - 8.0);
            this.value = Mth.clamp((float) normalized, 0.0f, 1.0f);
        }

        private float getScale() {
            return 0.5f + (this.value * 1.5f);
        }
    }

    private static final class ShaderPackSelectionList extends ObjectSelectionList<ShaderPackSelectionList.Entry> {
        private final int left;
        private final VOptionScreen screen;

        private ShaderPackSelectionList(VOptionScreen screen, Minecraft minecraft, int width, int height, int y0, int y1, int left) {
            super(minecraft, width, height, y0, y1);
            this.screen = screen;
            this.left = left;
        }

        public void addPackEntry(Entry entry) {
            this.children().add(entry);
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        @Override
        public int getRowLeft() {
            return this.left + 6;
        }

        @Override
        protected void renderBackground(GuiGraphics guiGraphics) {
        }

        public static final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final ShaderPackSelectionList list;
            private final Path path;
            private final Component label;

            public Entry(ShaderPackSelectionList list, Path path, Component label) {
                this.list = list;
                this.path = path;
                this.label = label;
            }

            public String getPackName() {
                return this.path != null ? this.path.getFileName().toString() : null;
            }

            @Override
            public void renderContent(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
                int bg = isHovered ? ColorUtil.ARGB.pack(0.3f, 0.0f, 0.0f, 0.30f) : ColorUtil.ARGB.pack(0.0f, 0.0f, 0.0f, 0.25f);
                GuiRenderer.fill(left, top, left + width, top + height - 1, bg);

                if (this.list.getSelected() == this) {
                    GuiRenderer.fill(left, top, left + 2, top + height - 1, RED);
                }

                int color = this.path == null ? 0xFFAAAAAA : 0xFFFFFFFF;
                GuiRenderer.drawString(this.list.screen.font, this.label, left + 8, top + 7, color);
            }

            @Override
            public Component getNarration() {
                return this.label;
            }

            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0 && this.path != null) {
                    this.list.setSelected(this);
                    this.list.screen.onShaderSelected(this);
                    return true;
                }

                return false;
            }
        }
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {
    }
}
