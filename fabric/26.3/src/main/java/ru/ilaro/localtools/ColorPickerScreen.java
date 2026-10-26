package ru.ilaro.localtools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

final class ColorPickerScreen extends Screen implements FadingMenuScreen {
    private static final int PANEL_HEIGHT = 216;

    private final LocalToolsClient mod;
    private final List<AbstractWidget> fadeWidgets = new ArrayList<>();
    private int panelX, panelY, panelWidth;
    private int matrixX, matrixY, matrixWidth, matrixHeight;
    private int hueX, hueY, hueWidth;
    private float hue, saturation, brightness;
    private int dragging;
    private long closingAt;

    ColorPickerScreen(LocalToolsClient mod) {
        super(Component.translatable("localtools.color_picker.screen_title"));
        this.mod = mod;
    }

    @Override
    protected void init() {
        mod.menuOpened(this);
        fadeWidgets.clear();
        panelWidth = Math.min(320, width - 20);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(4, height / 2 - PANEL_HEIGHT / 2);
        matrixX = panelX + 16;
        matrixY = panelY + 34;
        matrixWidth = panelWidth - 32;
        matrixHeight = 102;
        hueX = matrixX;
        hueY = panelY + 146;
        hueWidth = matrixWidth;

        int rgb = mod.config().accentColor;
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];

        addRenderableOnly((graphics, mouseX, mouseY, delta) -> renderPicker(graphics));
        DarkButton back = addRenderableWidget(new DarkButton(
                panelX + 16, panelY + 184, panelWidth - 32,
                Component.translatable("localtools.button.back"), this::saveAndBack,
                mod::interfaceAccentColor,
                () -> mod.config().background3d,
                () -> mod.config().buttonOpacity,
                () -> mod.interfaceTextColor(255, 0xD7D9DC)));
        fadeWidgets.add(back);
    }

    private void renderPicker(GuiGraphicsExtractor graphics) {
        if (mod.config().background3d) {
            AutoAdScreen.drawExtrusion(graphics, panelX, panelY, panelWidth, PANEL_HEIGHT,
                    fadeColor(mod.menuDepthColor()));
        }
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + PANEL_HEIGHT,
                fadeColor(mod.menuSurfaceColor()));
        graphics.fill(panelX, panelY, panelX + 3, panelY + PANEL_HEIGHT,
                fadeColor(0xE6000000 | mod.interfaceAccentColor()));

        int columns = 40;
        int rows = 18;
        for (int row = 0; row < rows; row++) {
            float value = 1.0f - row / (float) (rows - 1);
            int y1 = matrixY + row * matrixHeight / rows;
            int y2 = matrixY + (row + 1) * matrixHeight / rows;
            for (int column = 0; column < columns; column++) {
                float sat = column / (float) (columns - 1);
                int x1 = matrixX + column * matrixWidth / columns;
                int x2 = matrixX + (column + 1) * matrixWidth / columns;
                int color = Color.HSBtoRGB(hue, sat, value);
                graphics.fill(x1, y1, x2, y2, fadeColor(color));
            }
        }

        int hueSegments = 60;
        for (int segment = 0; segment < hueSegments; segment++) {
            int x1 = hueX + segment * hueWidth / hueSegments;
            int x2 = hueX + (segment + 1) * hueWidth / hueSegments;
            graphics.fill(x1, hueY, x2, hueY + 14,
                    fadeColor(Color.HSBtoRGB(segment / (float) hueSegments, 1.0f, 1.0f)));
        }

        int selectorX = matrixX + Math.round(saturation * matrixWidth);
        int selectorY = matrixY + Math.round((1.0f - brightness) * matrixHeight);
        drawSelector(graphics, selectorX, selectorY);
        int hueSelectorX = hueX + Math.round(hue * hueWidth);
        graphics.fill(hueSelectorX - 1, hueY - 2, hueSelectorX + 1, hueY + 16,
                fadeColor(0xFFFFFFFF));
        graphics.fill(hueSelectorX, hueY - 2, hueSelectorX + 1, hueY + 16,
                fadeColor(0xFF000000));

        String hex = String.format("#%06X", mod.config().accentColor & 0xFFFFFF);
        drawText(graphics,
                Component.translatable("localtools.color_picker.selected", hex).getString(),
                panelX + 16, panelY + 166, 0xFFC8CACD);
    }

    private void drawSelector(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 4, y - 4, x + 5, y - 3, fadeColor(0xFFFFFFFF));
        graphics.fill(x - 4, y + 4, x + 5, y + 5, fadeColor(0xFFFFFFFF));
        graphics.fill(x - 4, y - 3, x - 3, y + 4, fadeColor(0xFFFFFFFF));
        graphics.fill(x + 4, y - 3, x + 5, y + 4, fadeColor(0xFFFFFFFF));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClicked) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y(),
                matrixX, matrixY, matrixWidth, matrixHeight)) {
            dragging = 1;
            updateMatrix(event.x(), event.y());
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y(), hueX, hueY, hueWidth, 14)) {
            dragging = 2;
            updateHue(event.x());
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && !inside(event.x(), event.y(), panelX, panelY, panelWidth, PANEL_HEIGHT)) {
            requestUserClose();
            return true;
        }
        return super.mouseClicked(event, doubleClicked);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging == 1) {
            updateMatrix(event.x(), event.y());
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging == 2) {
            updateHue(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = 0;
        return super.mouseReleased(event);
    }

    private void updateMatrix(double mouseX, double mouseY) {
        saturation = clamp((float) ((mouseX - matrixX) / matrixWidth));
        brightness = 1.0f - clamp((float) ((mouseY - matrixY) / matrixHeight));
        applyColor();
    }

    private void updateHue(double mouseX) {
        hue = clamp((float) ((mouseX - hueX) / hueWidth));
        applyColor();
    }

    private void applyColor() {
        mod.config().accentColor = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        mod.config().rgbHud = false;
        mod.markMenuDirty();
    }

    private static boolean inside(double mouseX, double mouseY,
                                  int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private void saveAndBack() {
        mod.config().save();
        mod.clearMenuDirty();
        mod.showMenuScreen(new AppearanceScreen(mod, false));
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
    public void requestUserClose() {
        mod.showMenuScreen(new AppearanceScreen(mod, false));
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
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawText(graphics, getTitle().getString(), panelX + 16, panelY + 10, 0xFFE3E4E6);
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
