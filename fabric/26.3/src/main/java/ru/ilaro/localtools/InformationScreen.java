package ru.ilaro.localtools;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class InformationScreen extends Screen implements FadingMenuScreen {
    private static final int PANEL_HEIGHT = 216;

    private final LocalToolsClient mod;
    private final List<AbstractWidget> fadeWidgets = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private long closingAt;
    private long resetConfirmUntil;
    private long resetResultUntil;
    private DarkButton resetStatistics;

    InformationScreen(LocalToolsClient mod) {
        super(Component.translatable("localtools.information.screen_title"));
        this.mod = mod;
    }

    @Override
    protected void init() {
        mod.menuOpened(this);
        fadeWidgets.clear();
        panelWidth = Math.min(310, width - 20);
        panelX = (width - panelWidth) / 2;
        panelY = Math.max(4, height / 2 - PANEL_HEIGHT / 2);

        addRenderableOnly((graphics, mouseX, mouseY, delta) -> renderPanel(graphics));
        int gap = 6;
        int buttonWidth = (panelWidth - 28 - gap) / 2;
        resetStatistics = addRenderableWidget(new DarkButton(
                panelX + 14, panelY + 184, buttonWidth,
                Component.translatable("localtools.button.reset_statistics"),
                this::requestStatisticsReset,
                mod::interfaceAccentColor,
                () -> mod.config().background3d,
                () -> mod.config().buttonOpacity,
                () -> mod.interfaceTextColor(255, 0xD7D9DC)));
        fadeWidgets.add(resetStatistics);
        DarkButton back = addRenderableWidget(new DarkButton(
                panelX + 14 + buttonWidth + gap, panelY + 184, buttonWidth,
                Component.translatable("localtools.button.just_back"), this::back,
                mod::interfaceAccentColor,
                () -> mod.config().background3d,
                () -> mod.config().buttonOpacity,
                () -> mod.interfaceTextColor(255, 0xD7D9DC)));
        fadeWidgets.add(back);
    }

    private void renderPanel(GuiGraphicsExtractor graphics) {
        if (mod.config().background3d) {
            AutoAdScreen.drawExtrusion(
                    graphics, panelX, panelY, panelWidth, PANEL_HEIGHT,
                    fadeColor(mod.menuDepthColor()));
        }
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + PANEL_HEIGHT,
                fadeColor(mod.menuSurfaceColor()));
        graphics.fill(panelX, panelY, panelX + 3, panelY + PANEL_HEIGHT,
                fadeColor(0xE6000000 | mod.interfaceAccentColor()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float fade = menuFade();
        for (AbstractWidget widget : fadeWidgets) widget.setAlpha(fade);
        updateResetButton();
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        drawText(graphics, getTitle().getString(), panelX + 15, panelY + 10, 0xFFE3E4E6);
        AutoMessageConfig c = mod.config();
        long totalMessages = c.automaticMessagesSent + c.manualMessagesSent;
        List<Component> rows = List.of(
                Component.translatable("localtools.info.version", mod.modVersion()),
                Component.translatable("localtools.info.state", mod.informationStateText()),
                Component.translatable("localtools.info.timer", mod.secondsRemaining()),
                Component.translatable("localtools.info.automatic_messages", c.automaticMessagesSent),
                Component.translatable("localtools.info.manual_messages", c.manualMessagesSent),
                Component.translatable("localtools.info.total_messages", totalMessages),
                Component.translatable("localtools.info.enable_count", c.autoChatEnableCount),
                Component.translatable("localtools.info.pause_count", c.pauseActivationCount),
                Component.translatable("localtools.info.interval", c.intervalSeconds),
                Component.translatable("localtools.info.delay", c.initialDelaySeconds)
        );
        int rowY = panelY + 32;
        for (Component row : rows) {
            drawText(graphics, row.getString(), panelX + 15, rowY, 0xFFC8CACD);
            rowY += 14;
        }
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

    private void back() {
        mod.showMenuScreen(new AutoAdScreen(mod, false));
    }

    private void requestStatisticsReset() {
        long now = System.currentTimeMillis();
        if (now <= resetConfirmUntil) {
            mod.resetStatistics();
            resetConfirmUntil = 0;
            resetResultUntil = now + 1_500L;
            resetStatistics.pulse();
        } else {
            resetConfirmUntil = now + 3_000L;
        }
        updateResetButton();
    }

    private void updateResetButton() {
        if (resetStatistics == null) return;
        long now = System.currentTimeMillis();
        String key = now <= resetResultUntil
                ? "localtools.button.statistics_reset"
                : now <= resetConfirmUntil
                ? "localtools.button.confirm_reset_statistics"
                : "localtools.button.reset_statistics";
        resetStatistics.setMessage(Component.translatable(key));
    }

    @Override
    public void onClose() {
        back();
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

    private float menuFade() {
        if (closingAt == 0) return 1.0f;
        return Math.max(0.0f, 1.0f - (System.currentTimeMillis() - closingAt)
                / (float) mod.config().menuFadeMillis);
    }

    private int fadeColor(int color) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * menuFade());
        return (alpha << 24) | (color & 0xFFFFFF);
    }
}
