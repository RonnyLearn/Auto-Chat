package ru.ilaro.localtools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class QueueScreen extends Screen implements FadingMenuScreen {
    private static final int PANEL_HEIGHT = 236;
    private static final int MAX_MESSAGES = 6;

    private final LocalToolsClient mod;
    private final List<AbstractWidget> fadeWidgets = new ArrayList<>();
    private final List<EditBox> fields = new ArrayList<>();
    private DarkButton enabled;
    private int panelX, panelY, panelWidth;
    private long closingAt;

    QueueScreen(LocalToolsClient mod) {
        super(Component.translatable("localtools.queue.screen_title"));
        this.mod = mod;
    }

    @Override
    protected void init() {
        mod.menuOpened(this);
        fadeWidgets.clear();
        fields.clear();
        panelWidth = Math.min(310, width - 20);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(4, height / 2 - PANEL_HEIGHT / 2);
        AutoMessageConfig c = mod.config();

        addRenderableOnly((graphics, mouseX, mouseY, delta) -> renderPanel(graphics));
        enabled = button(panelX + 14, panelY + 32, panelWidth - 28,
                Component.empty(), () -> {
                    c.queueEnabled = !c.queueEnabled;
                    mod.markMenuDirty();
                    enabled.pulse();
                    updateButton();
                });

        for (int i = 0; i < MAX_MESSAGES; i++) {
            String value = i < c.messageQueue.size() ? c.messageQueue.get(i) : "";
            fields.add(field(panelX + 14, panelY + 56 + i * 24,
                    panelWidth - 28, value, i + 1));
        }

        button(panelX + 14, panelY + 204, panelWidth - 28,
                Component.translatable("localtools.button.just_back"), this::saveAndBack);
        updateButton();
    }

    private void renderPanel(GuiGraphicsExtractor graphics) {
        if (mod.config().background3d) {
            AutoAdScreen.drawExtrusion(graphics, panelX, panelY, panelWidth, PANEL_HEIGHT,
                    fadeColor(mod.menuDepthColor()));
        }
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + PANEL_HEIGHT,
                fadeColor(mod.menuSurfaceColor()));
        graphics.fill(panelX, panelY, panelX + 3, panelY + PANEL_HEIGHT,
                fadeColor(0xE6000000 | mod.interfaceAccentColor()));
    }

    private EditBox field(int x, int y, int fieldWidth, String value, int number) {
        Component label = Component.translatable("localtools.queue.message", number);
        AutoAdScreen.SoftEditBox box = new AutoAdScreen.SoftEditBox(
                font, x + 8, y + 6, fieldWidth - 16, 9,
                label, x, y, fieldWidth, 20);
        box.setBordered(false);
        box.setTextShadow(true);
        box.setTextColor(0xFFD0D1D3);
        box.setMaxLength(256);
        box.setValue(value);
        box.setResponder(text -> mod.markMenuDirty());
        box.setHint(Component.empty());
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            if (mod.config().background3d) {
                AutoAdScreen.drawExtrusion(
                        graphics, x, y, fieldWidth, 20, fadeColor(mod.menuDepthColor()));
            }
            graphics.fill(x, y, x + fieldWidth, y + 20,
                    fadeColor(box.isFocused() ? 0xFF303236 : 0xFF25272A));
            graphics.fill(x, y, x + 2, y + 20,
                    fadeColor(0xD9000000 | mod.interfaceAccentColor()));
            if (box.getValue().isEmpty()) {
                int dots = box.isFocused()
                        ? (int) ((System.currentTimeMillis() / 350L) % 4L)
                        : -1;
                String placeholder = dots >= 0 ? ".".repeat(dots) : label.getString();
                drawText(graphics, placeholder, x + 8, y + 6, 0xFFA8AAAD);
            }
        });
        EditBox widget = addRenderableWidget(box);
        fadeWidgets.add(widget);
        return widget;
    }

    private DarkButton button(int x, int y, int buttonWidth, Component text, Runnable action) {
        DarkButton widget = addRenderableWidget(new DarkButton(
                x, y, buttonWidth, text, action,
                mod::interfaceAccentColor,
                () -> mod.config().background3d,
                () -> mod.config().buttonOpacity,
                () -> mod.interfaceTextColor(255, 0xD7D9DC)));
        fadeWidgets.add(widget);
        return widget;
    }

    private void updateButton() {
        enabled.setMessage(Component.translatable("localtools.button.queue_state",
                Component.translatable(mod.config().queueEnabled
                        ? "localtools.state.on" : "localtools.state.off")));
    }

    private void saveAndBack() {
        applyDraft();
        AutoMessageConfig c = mod.config();
        c.save();
        mod.clearMenuDirty();
        mod.resetQueue();
        mod.showMenuScreen(new AutoAdScreen(mod, false));
    }

    @Override
    public void commitDraft() {
        applyDraft();
    }

    private void applyDraft() {
        AutoMessageConfig c = mod.config();
        c.messageQueue.clear();
        for (EditBox field : fields) {
            if (!field.getValue().isBlank()) c.messageQueue.add(field.getValue());
        }
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClicked) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && (event.x() < panelX || event.x() >= panelX + panelWidth
                || event.y() < panelY || event.y() >= panelY + PANEL_HEIGHT)) {
            requestUserClose();
            return true;
        }
        return super.mouseClicked(event, doubleClicked);
    }

    @Override
    public void requestUserClose() {
        applyDraft();
        mod.showMenuScreen(new AutoAdScreen(mod, false));
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float fade = menuFade();
        for (AbstractWidget widget : fadeWidgets) widget.setAlpha(fade);
        int textColor = AutoAdScreen.applyAlpha(0xFFD0D1D3, fade);
        for (EditBox field : fields) field.setTextColor(textColor);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawText(graphics, getTitle().getString(), panelX + 15, panelY + 10, 0xFFE3E4E6);
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

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        int alpha = (color >>> 24) & 0xFF;
        mod.drawExtrudedText(graphics, text, x, y,
                fadeColor(mod.interfaceTextColor(alpha, color & 0xFFFFFF)));
    }

    private float menuFade() {
        if (closingAt == 0) return 1.0f;
        return Math.max(0.0f, 1.0f - (System.currentTimeMillis() - closingAt)
                / (float) mod.config().menuFadeMillis);
    }

    private int fadeColor(int color) {
        return AutoAdScreen.applyAlpha(color, menuFade());
    }
}
