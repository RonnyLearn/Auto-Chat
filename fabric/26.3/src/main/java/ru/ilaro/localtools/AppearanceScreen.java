package ru.ilaro.localtools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

final class AppearanceScreen extends Screen implements FadingMenuScreen {
    private static final int PANEL_HEIGHT = 236;
    private static final int[] PALETTE = {
            0x3F7FC0, 0x8A63D2, 0xD05A8A,
            0xD47A3A, 0x38A36F, 0x28A6A6
    };

    private final LocalToolsClient mod;
    private final List<AbstractWidget> fadeWidgets = new ArrayList<>();
    private final List<TooltipTarget> tooltipTargets = new ArrayList<>();
    private EditBox hudTitle;
    private AutoAdScreen.ValueSlider textAlpha, backgroundAlpha, hudScale, rgbSpeed;
    private AutoAdScreen.ValueSlider menuFade, menuBackground, buttonAlpha;
    private DarkButton hud, background3d, dimBackground, hudMode, hudAnchor, rgbAccents, rgbText;
    private AutoAdScreen.RgbSwatch rgb;
    private int leftX, middleX, rightX, panelY, panelWidth;
    private boolean dragging;
    private int dragOffsetX, dragOffsetY;
    private long openedAt;
    private long closingAt;
    private final boolean animateOpening;

    AppearanceScreen(LocalToolsClient mod) {
        this(mod, false);
    }

    AppearanceScreen(LocalToolsClient mod, boolean animateOpening) {
        super(Component.translatable("localtools.appearance.screen_title"));
        this.mod = mod;
        this.animateOpening = animateOpening;
    }

    @Override
    protected void init() {
        mod.menuOpened(this);
        openedAt = System.currentTimeMillis();
        fadeWidgets.clear();
        tooltipTargets.clear();

        int totalWidth = Math.min(606, width - 20);
        panelWidth = (totalWidth - 20) / 3;
        leftX = (width - totalWidth) / 2;
        middleX = leftX + panelWidth + 10;
        rightX = middleX + panelWidth + 10;
        panelY = Math.max(4, height / 2 - PANEL_HEIGHT / 2);

        AutoMessageConfig c = mod.config();
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            renderPanel(graphics, leftX, panelY, panelWidth, PANEL_HEIGHT);
            renderPanel(graphics, middleX, panelY, panelWidth, PANEL_HEIGHT);
            renderPanel(graphics, rightX, panelY, panelWidth, PANEL_HEIGHT);
        });

        rgbSpeed = slider(leftX + 10, panelY + 32, panelWidth - 20,
                25, 300, c.rgbSpeedPercent,
                value -> Component.translatable("localtools.slider.rgb_speed", value),
                value -> c.rgbSpeedPercent = value);
        addTip(rgbSpeed, Component.translatable("localtools.help.rgb_speed"));

        int swatchWidth = Math.max(24, (panelWidth - 40) / 3);
        int paletteX = leftX + (panelWidth - (swatchWidth * 3 + 8)) / 2;
        for (int i = 0; i < PALETTE.length; i++) {
            int color = PALETTE[i];
            int swatchX = paletteX + (i % 3) * (swatchWidth + 4);
            int swatchY = panelY + 82 + (i / 3) * 24;
            AutoAdScreen.ColorSwatch swatch = addRenderableWidget(new AutoAdScreen.ColorSwatch(
                    swatchX, swatchY, swatchWidth, color,
                    () -> c.rgbHud ? -1 : c.accentColor,
                    () -> {
                        c.rgbHud = false;
                        c.accentColor = color;
                        mod.markMenuDirty();
                        updateButtons();
                    },
                    () -> c.background3d));
            fadeWidgets.add(swatch);
        }

        rgb = addRenderableWidget(new AutoAdScreen.RgbSwatch(
                paletteX, panelY + 130, swatchWidth, 18,
                () -> c.rgbHud, mod::interfaceRainbowColor, () -> {
                    c.rgbHud = !c.rgbHud;
                    if (c.rgbHud) c.rgbAccents = true;
                    mod.markMenuDirty();
                    updateButtons();
                }, () -> c.background3d));
        fadeWidgets.add(rgb);
        addTip(rgb, Component.translatable("localtools.help.rgb_interface"));
        AutoAdScreen.PickerSwatch customColor = addRenderableWidget(
                new AutoAdScreen.PickerSwatch(
                        paletteX + swatchWidth + 4, panelY + 130, swatchWidth, 18,
                        () -> c.accentColor, this::openColorPicker,
                        () -> c.background3d));
        fadeWidgets.add(customColor);
        addTip(customColor, Component.translatable("localtools.help.custom_color"));

        DarkButton restoreDefaults = button(leftX + 10, panelY + 202, panelWidth - 20,
                Component.translatable("localtools.button.restore_defaults"),
                this::restoreAppearanceDefaults);
        addTip(restoreDefaults, Component.translatable("localtools.help.restore_defaults"));

        menuFade = slider(middleX + 10, panelY + 32, panelWidth - 20,
                1, 50, Math.max(1, c.menuFadeMillis / 100),
                value -> Component.translatable("localtools.slider.menu_fade",
                        String.format(java.util.Locale.ROOT, "%.1f", value / 10.0f)),
                value -> c.menuFadeMillis = value * 100);

        menuBackground = slider(
                middleX + 10, panelY + 58, panelWidth - 20,
                0, 255, c.menuBackgroundOpacity,
                value -> Component.translatable("localtools.slider.menu_background",
                        Math.round(value / 255.0f * 100)),
                value -> c.menuBackgroundOpacity = value);
        addTip(menuBackground, Component.translatable("localtools.help.menu_background"));

        buttonAlpha = slider(middleX + 10, panelY + 84, panelWidth - 20,
                0, 255, c.buttonOpacity,
                value -> Component.translatable("localtools.slider.button_opacity",
                        Math.round(value / 255.0f * 100)),
                value -> c.buttonOpacity = value);

        rgbAccents = button(middleX + 10, panelY + 110, panelWidth - 20,
                Component.empty(), () -> {
                    c.rgbAccents = !c.rgbAccents;
                    mod.markMenuDirty();
                    rgbAccents.pulse();
                    updateButtons();
                });

        rgbText = button(middleX + 10, panelY + 136, panelWidth - 20,
                Component.empty(), () -> {
                    c.rgbText = !c.rgbText;
                    mod.markMenuDirty();
                    rgbText.pulse();
                    updateButtons();
                });

        background3d = button(middleX + 10, panelY + 162, panelWidth - 20,
                Component.empty(), () -> {
                    c.background3d = !c.background3d;
                    mod.markMenuDirty();
                    background3d.pulse();
                    updateButtons();
                });
        addTip(background3d, Component.translatable("localtools.help.3d"));

        dimBackground = button(middleX + 10, panelY + 188, panelWidth - 20,
                Component.empty(), () -> {
                    c.dimMenuBackground = !c.dimMenuBackground;
                    mod.markMenuDirty();
                    dimBackground.pulse();
                    updateButtons();
                });
        addTip(dimBackground, Component.translatable("localtools.help.menu_dim"));

        DarkButton back = button(middleX + 10, panelY + 214, panelWidth - 20,
                Component.translatable("localtools.button.back"), this::saveAndBack);
        addTip(back, Component.translatable("localtools.help.back"));

        hud = button(rightX + 10, panelY + 32, panelWidth - 20, Component.empty(), () -> {
            c.showHud = !c.showHud;
            mod.markMenuDirty();
            hud.pulse();
            updateButtons();
        });

        hudTitle = field(rightX + 10, panelY + 58, panelWidth - 20, c.hudTitle,
                Component.translatable("localtools.label.hud_title"));
        hudTitle.setMaxLength(32);
        hudTitle.setResponder(value -> mod.markMenuDirty());
        addTip(hudTitle, Component.translatable("localtools.help.hud_title"));

        textAlpha = slider(rightX + 10, panelY + 84, panelWidth - 20,
                0, 255, c.hudTextOpacity,
                value -> Component.translatable("localtools.slider.opacity",
                        Component.translatable("localtools.label.text"), Math.round(value / 255.0f * 100)),
                value -> c.hudTextOpacity = value);
        addTip(textAlpha, Component.translatable("localtools.help.opacity"));

        backgroundAlpha = slider(rightX + 10, panelY + 110, panelWidth - 20,
                0, 255, c.hudBackgroundOpacity,
                value -> Component.translatable("localtools.slider.opacity",
                        Component.translatable("localtools.label.background"), Math.round(value / 255.0f * 100)),
                value -> c.hudBackgroundOpacity = value);
        addTip(backgroundAlpha, Component.translatable("localtools.help.opacity"));

        hudScale = slider(rightX + 10, panelY + 136, panelWidth - 20,
                50, 300, c.hudScalePercent,
                value -> Component.translatable("localtools.slider.hud_scale", value),
                value -> c.hudScalePercent = value);
        addTip(hudScale, Component.translatable("localtools.help.hud_scale"));

        hudMode = button(rightX + 10, panelY + 162, panelWidth - 20, Component.empty(), () -> {
            c.compactHud = !c.compactHud;
            mod.markMenuDirty();
            hudMode.pulse();
            updateButtons();
        });
        addTip(hudMode, Component.translatable("localtools.help.hud_mode"));

        hudAnchor = button(rightX + 10, panelY + 188, panelWidth - 20,
                Component.empty(), () -> {
                    c.hudAnchor = (c.hudAnchor + 1) % 5;
                    mod.markMenuDirty();
                    hudAnchor.pulse();
                    updateButtons();
                });
        addTip(hudAnchor, Component.translatable("localtools.help.hud_anchor"));

        DarkButton resetTimer = button(rightX + 10, panelY + 212, panelWidth - 20,
                Component.translatable("localtools.button.reset_timer"), () -> {
                    mod.resetTimer();
                    mod.config().save();
                });
        addTip(resetTimer, Component.translatable("localtools.help.reset_timer"));

        String hudHeading = Component.translatable("localtools.appearance.hud_title").getString();
        AutoAdScreen.HelpIcon helpIcon = addRenderableWidget(new AutoAdScreen.HelpIcon(
                rightX + 11 + font.width(hudHeading) + 2, panelY + 10));
        fadeWidgets.add(helpIcon);
        addTip(helpIcon, Component.translatable("localtools.hud.drag_hint"));
        updateButtons();
    }

    private void renderPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (mod.config().background3d) {
            AutoAdScreen.drawExtrusion(
                    graphics, x, y, width, height, fadeColor(mod.menuDepthColor()));
        }
        graphics.fill(x, y, x + width, y + height, fadeColor(mod.menuSurfaceColor()));
        graphics.fill(x, y, x + 3, y + height,
                fadeColor(0xE6000000 | mod.interfaceAccentColor()));
    }

    private EditBox field(int x, int y, int width, String value, Component placeholder) {
        AutoAdScreen.SoftEditBox box = new AutoAdScreen.SoftEditBox(
                font, x + 8, y + 6, width - 16, 9, placeholder, x, y, width, 20);
        box.setBordered(false);
        box.setTextShadow(true);
        box.setTextColor(0xFFD0D1D3);
        box.setValue(value);
        box.setHint(Component.empty());
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            if (mod.config().background3d) {
                AutoAdScreen.drawExtrusion(graphics, x, y, width, 20, fadeColor(0xA8000000));
            }
            graphics.fill(x, y, x + width, y + 20,
                    fadeColor(box.isFocused() ? 0xFF303236 : 0xFF25272A));
            graphics.fill(x, y, x + 2, y + 20,
                    fadeColor(0xD9000000 | mod.interfaceAccentColor()));
        });
        EditBox widget = addRenderableWidget(box);
        fadeWidgets.add(widget);
        return widget;
    }

    private DarkButton button(int x, int y, int width, Component text, Runnable action) {
        DarkButton widget = addRenderableWidget(new DarkButton(
                x, y, width, text, action,
                mod::interfaceAccentColor,
                () -> mod.config().background3d,
                () -> mod.config().buttonOpacity,
                () -> mod.interfaceTextColor(255, 0xD7D9DC)));
        fadeWidgets.add(widget);
        return widget;
    }

    private AutoAdScreen.ValueSlider slider(int x, int y, int width, int min, int max, int initial,
                                            IntFunction<Component> messageFactory, IntConsumer onChange) {
        AutoAdScreen.ValueSlider widget = addRenderableWidget(new AutoAdScreen.ValueSlider(
                x, y, width, min, max, initial, messageFactory, value -> {
                    onChange.accept(value);
                    mod.markMenuDirty();
                },
                mod::interfaceAccentColor,
                () -> mod.config().background3d,
                () -> mod.config().buttonOpacity,
                () -> mod.interfaceTextColor(255, 0xD0D1D3)));
        fadeWidgets.add(widget);
        return widget;
    }

    private void addTip(AbstractWidget widget, Component text) {
        tooltipTargets.add(new TooltipTarget(widget, text));
    }

    private void updateButtons() {
        AutoMessageConfig c = mod.config();
        background3d.setMessage(Component.translatable("localtools.button.3d",
                Component.translatable(c.background3d ? "localtools.state.on" : "localtools.state.off")));
        hud.setMessage(Component.translatable("localtools.button.hud",
                Component.translatable(c.showHud ? "localtools.state.on" : "localtools.state.off")));
        hudMode.setMessage(Component.translatable("localtools.button.hud_mode",
                Component.translatable(c.compactHud ? "localtools.mode.compact" : "localtools.mode.full")));
        String anchorKey = switch (c.hudAnchor) {
            case 1 -> "localtools.anchor.top_left";
            case 2 -> "localtools.anchor.top_right";
            case 3 -> "localtools.anchor.bottom_left";
            case 4 -> "localtools.anchor.bottom_right";
            default -> "localtools.anchor.free";
        };
        hudAnchor.setMessage(Component.translatable("localtools.button.hud_anchor",
                Component.translatable(anchorKey)));
        rgbAccents.setMessage(Component.translatable("localtools.button.rgb_accents",
                Component.translatable(c.rgbAccents ? "localtools.state.on" : "localtools.state.off")));
        rgbText.setMessage(Component.translatable("localtools.button.rgb_text",
                Component.translatable(c.rgbText ? "localtools.state.on" : "localtools.state.off")));
        dimBackground.setMessage(Component.translatable("localtools.button.menu_dim",
                Component.translatable(c.dimMenuBackground ? "localtools.state.on" : "localtools.state.off")));
    }

    private void saveAndBack() {
        mod.config().hudTitle = hudTitle.getValue();
        mod.config().save();
        mod.clearMenuDirty();
        mod.showMenuScreen(new AutoAdScreen(mod, false));
    }

    @Override
    public void commitDraft() {
        mod.config().hudTitle = hudTitle.getValue();
    }

    private void restoreAppearanceDefaults() {
        mod.config().resetAppearanceDefaults();
        AutoMessageConfig c = mod.config();
        hudTitle.setValue(c.hudTitle);
        textAlpha.setNumber(c.hudTextOpacity);
        backgroundAlpha.setNumber(c.hudBackgroundOpacity);
        hudScale.setNumber(c.hudScalePercent);
        rgbSpeed.setNumber(c.rgbSpeedPercent);
        menuFade.setNumber(Math.max(1, c.menuFadeMillis / 100));
        menuBackground.setNumber(c.menuBackgroundOpacity);
        buttonAlpha.setNumber(c.buttonOpacity);
        updateButtons();
        mod.clearMenuDirty();
    }

    private void openColorPicker() {
        mod.config().hudTitle = hudTitle.getValue();
        mod.showMenuScreen(new ColorPickerScreen(mod));
    }

    @Override
    public void onClose() {
        saveAndBack();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            requestUserClose();
            return true;
        }
        return mod.handleMenuKey(event) || super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        if (closingAt > 0
                && System.currentTimeMillis() - closingAt >= mod.config().menuFadeMillis) {
            mod.finishMenuClose();
        }
    }

    @Override
    public void startClosing() {
        if (closingAt == 0) closingAt = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClicked) {
        AutoMessageConfig c = mod.config();
        int previewWidth = mod.scaledHudWidth(previewTitle());
        int previewHeight = mod.scaledHudHeight();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && event.x() >= c.hudX && event.x() <= c.hudX + previewWidth
                && event.y() >= c.hudY && event.y() <= c.hudY + previewHeight) {
            dragging = true;
            dragOffsetX = (int) event.x() - c.hudX;
            dragOffsetY = (int) event.y() - c.hudY;
            return true;
        }
        boolean inPanel = insidePanel(event.x(), event.y(), leftX)
                || insidePanel(event.x(), event.y(), middleX)
                || insidePanel(event.x(), event.y(), rightX);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && !inPanel) {
            requestUserClose();
            return true;
        }
        return super.mouseClicked(event, doubleClicked);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            mod.config().hudAnchor = 0;
            mod.markMenuDirty();
            int previewWidth = mod.scaledHudWidth(previewTitle());
            int previewHeight = mod.scaledHudHeight();
            mod.config().hudX = Math.max(0, Math.min(width - previewWidth, (int) event.x() - dragOffsetX));
            mod.config().hudY = Math.max(0, Math.min(height - previewHeight, (int) event.y() - dragOffsetY));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    private boolean insidePanel(double mouseX, double mouseY, int x) {
        return mouseX >= x && mouseX < x + panelWidth
                && mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT;
    }

    @Override
    public void requestUserClose() {
        mod.config().hudTitle = hudTitle.getValue();
        mod.showMenuScreen(new AutoAdScreen(mod, false));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float fade = menuFade();
        for (AbstractWidget widget : fadeWidgets) widget.setAlpha(fade);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        drawText(graphics, Component.translatable("localtools.appearance.interface_title").getString(),
                leftX + 11, panelY + 10, 0xFFE3E4E6);
        drawText(graphics, Component.translatable("localtools.palette.title").getString(),
                leftX + 11, panelY + 63, 0xFFBEBEBE);
        drawText(graphics, Component.translatable("localtools.appearance.details_title").getString(),
                middleX + 11, panelY + 10, 0xFFE3E4E6);
        drawText(graphics, Component.translatable("localtools.appearance.hud_title").getString(),
                rightX + 11, panelY + 10, 0xFFE3E4E6);

        if (hudTitle.getValue().isEmpty()) {
            int dots = hudTitle.isFocused() ? (int) ((System.currentTimeMillis() / 350L) % 4L) : -1;
            String placeholder = dots >= 0
                    ? ".".repeat(dots)
                    : Component.translatable("localtools.label.hud_title").getString();
            drawText(graphics, placeholder, rightX + 18, panelY + 64, 0xFFA8AAAD);
        }
        if (mod.config().showHud) mod.renderHudAt(graphics, previewTitle());
        renderCustomTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float fade = menuFade() * mod.menuDimFactor();
        int topAlpha = Math.round(0xC0 * fade);
        int bottomAlpha = Math.round(0xD0 * fade);
        graphics.fillGradient(0, 0, width, height,
                (topAlpha << 24) | 0x00101010,
                (bottomAlpha << 24) | 0x00101010);
    }

    private String previewTitle() {
        String title = hudTitle == null ? mod.config().hudTitle : hudTitle.getValue();
        return title.isBlank() ? mod.hudTitle() : title;
    }

    private void renderCustomTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        TooltipTarget hovered = null;
        for (TooltipTarget target : tooltipTargets) {
            if (target.widget.isMouseOver(mouseX, mouseY)) {
                hovered = target;
                break;
            }
        }
        if (hovered == null) return;

        List<FormattedCharSequence> lines = font.split(hovered.text, 190);
        String title = Component.translatable("localtools.tooltip.title").getString();
        int boxWidth = font.width(title);
        for (FormattedCharSequence line : lines) boxWidth = Math.max(boxWidth, font.width(line));
        boxWidth += 16;
        int boxHeight = 18 + lines.size() * 10;
        int x = Math.max(5, Math.min(width - boxWidth - 5, mouseX + 12));
        int y = Math.max(5, Math.min(height - boxHeight - 5, mouseY + 10));

        if (mod.config().background3d) {
            AutoAdScreen.drawExtrusion(
                    graphics, x, y, boxWidth, boxHeight, mod.menuDepthColor());
        }
        graphics.fill(x, y, x + boxWidth, y + boxHeight, mod.menuSurfaceColor());
        graphics.fill(x, y, x + 2, y + boxHeight, 0xFFE5C95A);
        mod.drawExtrudedText(graphics, title, x + 8, y + 6, 0xFFE5C95A);
        int lineY = y + 17;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x + 9, lineY + 1, 0xC0000000, false);
            graphics.text(font, line, x + 8, lineY, 0xFFD0D1D3, false);
            lineY += 10;
        }
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        int alpha = (color >>> 24) & 0xFF;
        mod.drawExtrudedText(graphics, text, x, y,
                fadeColor(mod.interfaceTextColor(alpha, color & 0xFFFFFF)));
    }

    private float menuFade() {
        if (closingAt > 0) {
            return Math.max(0.0f, 1.0f - (System.currentTimeMillis() - closingAt)
                    / (float) mod.config().menuFadeMillis);
        }
        if (!animateOpening) return 1.0f;
        return Math.min(1.0f, (System.currentTimeMillis() - openedAt)
                / (float) mod.config().menuFadeMillis);
    }

    private int fadeColor(int color) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * menuFade());
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    private record TooltipTarget(AbstractWidget widget, Component text) {
    }
}
