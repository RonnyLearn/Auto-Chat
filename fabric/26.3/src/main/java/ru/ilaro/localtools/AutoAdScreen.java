package ru.ilaro.localtools;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

final class AutoAdScreen extends Screen implements FadingMenuScreen {
    private static final int PANEL_HEIGHT = 226;

    private final LocalToolsClient mod;
    private final List<PlaceholderField> placeholders = new ArrayList<>();
    private final List<TooltipTarget> tooltipTargets = new ArrayList<>();
    private final List<AbstractWidget> fadeWidgets = new ArrayList<>();
    private EditBox message, interval, delay;
    private DarkButton enabled, paused, sendNow;
    private DarkButton exit;
    private long openedAt;
    private long closingAt;
    private final boolean animateOpening;
    private long messageErrorStarted;
    private long messageErrorUntil;
    private boolean showTooltipIntro;
    private boolean suppressTooltipIntro;
    private boolean exitAwaitingConfirmation;
    private int noticeX, noticeY, noticeWidth, noticeHeight;
    private int mainX, panelY, mainWidth;

    AutoAdScreen(LocalToolsClient mod) {
        this(mod, true);
    }

    AutoAdScreen(LocalToolsClient mod, boolean animateOpening) {
        super(Component.translatable("localtools.screen.title"));
        this.mod = mod;
        this.animateOpening = animateOpening;
    }

    @Override
    protected void init() {
        mod.menuOpened(this);
        openedAt = System.currentTimeMillis();
        placeholders.clear();
        tooltipTargets.clear();
        fadeWidgets.clear();
        showTooltipIntro = !mod.config().hideTooltipIntro;
        suppressTooltipIntro = false;

        mainWidth = Math.min(270, width - 20);
        mainX = (width - mainWidth) / 2;
        panelY = Math.max(4, height / 2 - PANEL_HEIGHT / 2);
        noticeWidth = Math.min(380, width - 10);
        noticeX = (width - noticeWidth) / 2;
        noticeY = 5;
        int noticeLines = font.split(
                Component.translatable("localtools.notice.tooltip_hint"), noticeWidth - 32).size();
        noticeHeight = 24 + noticeLines * 10;
        AutoMessageConfig c = mod.config();
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> renderPanels(graphics));

        enabled = button(mainX + 14, panelY + 30, mainWidth - 28, Component.empty(), () -> {
            if (!c.enabled && !mod.hasAutomaticMessage(message.getValue())) {
                triggerMessageError();
                return;
            }
            c.message = message.getValue();
            mod.toggleEnabled();
            enabled.pulse();
            updateButtons();
        });

        message = field(mainX + 14, panelY + 56, mainWidth - 28, c.message,
                Component.translatable("localtools.label.message"), true);
        message.setMaxLength(256);
        message.setResponder(value -> {
            if (!value.isBlank()) messageErrorUntil = 0;
        });
        addTip(message, Component.translatable("localtools.help.message"));

        int fieldGap = 8;
        int halfWidth = (mainWidth - 28 - fieldGap) / 2;
        interval = field(mainX + 14, panelY + 82, halfWidth,
                c.intervalSeconds == 300 ? "" : String.valueOf(c.intervalSeconds),
                Component.translatable("localtools.label.interval"), false);
        interval.setMaxLength(5);
        addTip(interval, Component.translatable("localtools.help.interval"));

        delay = field(mainX + 14 + halfWidth + fieldGap, panelY + 82, halfWidth,
                c.initialDelaySeconds == 20 ? "" : String.valueOf(c.initialDelaySeconds),
                Component.translatable("localtools.label.delay"), false);
        delay.setMaxLength(5);
        addTip(delay, Component.translatable("localtools.help.delay"));

        paused = button(mainX + 14, panelY + 108, mainWidth - 28, Component.empty(), () -> {
            mod.togglePaused();
            paused.pulse();
            updateButtons();
        });
        addTip(paused, Component.translatable("localtools.help.pause"));

        int actionGap = 6;
        int actionWidth = (mainWidth - 28 - actionGap) / 2;
        sendNow = button(mainX + 14, panelY + 134, actionWidth,
                Component.translatable("localtools.button.send_now"), this::requestSendNow);
        addTip(sendNow, Component.translatable("localtools.help.send_now"));
        DarkButton queue = button(mainX + 14 + actionWidth + actionGap,
                panelY + 134, actionWidth,
                Component.translatable("localtools.button.queue"), this::openQueue);
        addTip(queue, Component.translatable("localtools.help.queue"));

        int navigationGap = 6;
        int navigationWidth = (mainWidth - 28 - navigationGap) / 2;
        DarkButton appearance = button(mainX + 14, panelY + 160, navigationWidth,
                Component.translatable("localtools.button.appearance"), this::openAppearance);
        addTip(appearance, Component.translatable("localtools.help.appearance"));
        DarkButton information = button(mainX + 14 + navigationWidth + navigationGap,
                panelY + 160, navigationWidth,
                Component.translatable("localtools.button.information"), this::openInformation);
        addTip(information, Component.translatable("localtools.help.information"));

        exit = button(mainX + 14, panelY + 190, mainWidth - 28,
                Component.translatable("localtools.button.exit"), this::requestExit);
        updateButtons();
    }

    private void renderPanels(GuiGraphicsExtractor graphics) {
        renderPanel(graphics, mainX, panelY, mainWidth, PANEL_HEIGHT);
    }

    private void renderPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (mod.config().background3d) {
            drawExtrusion(graphics, x, y, width, height, fadeColor(mod.menuDepthColor()));
        }
        graphics.fill(x, y, x + width, y + height, fadeColor(mod.menuSurfaceColor()));
        graphics.fill(x, y, x + 3, y + height,
                fadeColor(0xE6000000 | mod.interfaceAccentColor()));
    }

    private EditBox field(int x, int y, int width, String value, Component placeholder,
                          boolean messageField) {
        SoftEditBox box = new SoftEditBox(font, x + 8, y + 6, width - 16, 9,
                placeholder, x, y, width, 20);
        box.setBordered(false);
        box.setTextShadow(true);
        box.setTextColor(0xFFD0D1D3);
        box.setValue(value);
        box.setHint(Component.empty());
        addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            int shake = messageField ? messageShakeOffset() : 0;
            box.setX(x + 8 + shake);
            if (mod.config().background3d) drawExtrusion(graphics, x, y, width, 20, fadeColor(0xA8000000));
            float error = messageField ? messageErrorStrength() : 0.0f;
            int background = blendRgb(box.isFocused() ? 0x303236 : 0x25272A, 0x6A2027, error);
            int accentRgb = blendRgb(mod.interfaceAccentColor(), 0xF04F5F, error);
            graphics.fill(x + shake, y, x + width + shake, y + 20,
                    fadeColor(0xFF000000 | background));
            graphics.fill(x + shake, y, x + 2 + shake, y + 20,
                    fadeColor(0xD9000000 | accentRgb));
        });
        placeholders.add(new PlaceholderField(box, x + 8, y + 6, placeholder, messageField));
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

    private void addTip(AbstractWidget widget, Component text) {
        tooltipTargets.add(new TooltipTarget(widget, text));
    }

    private void updateButtons() {
        AutoMessageConfig c = mod.config();
        enabled.setMessage(Component.translatable("localtools.button.autoad",
                Component.translatable(c.enabled ? "localtools.state.on" : "localtools.state.off")));
        paused.setMessage(Component.translatable("localtools.button.pause",
                Component.translatable(c.paused ? "localtools.state.on" : "localtools.state.off")));
        delay.active = !c.paused;
        updateSendButton();
    }

    private void requestSendNow() {
        if (mod.manualSendCooldownRemainingMillis() > 0L) {
            sendNow.triggerError();
            updateSendButton();
            return;
        }
        String manualMessage = mod.nextManualMessage(message.getValue());
        if (manualMessage == null || manualMessage.isBlank()) {
            triggerMessageError();
            updateSendButton();
            return;
        }
        if (mod.sendNow(manualMessage)) mod.startManualSendCooldown();
        updateSendButton();
    }

    private void updateSendButton() {
        if (sendNow == null) return;
        long remainingMillis = mod.manualSendCooldownRemainingMillis();
        if (remainingMillis > 0L) {
            long remainingSeconds = (remainingMillis + 999L) / 1_000L;
            sendNow.setMessage(Component.translatable("localtools.button.wait", remainingSeconds));
        } else {
            sendNow.setMessage(Component.translatable("localtools.button.send_now"));
        }
    }

    private void triggerMessageError() {
        messageErrorStarted = System.currentTimeMillis();
        messageErrorUntil = messageErrorStarted + 1_050L;
        message.setFocused(true);
    }

    private float messageErrorStrength() {
        long now = System.currentTimeMillis();
        if (now >= messageErrorUntil || messageErrorStarted == 0) return 0.0f;
        float progress = Math.min(1.0f, (now - messageErrorStarted) / 1_050.0f);
        return (float) Math.sin(progress * Math.PI) * 0.92f;
    }

    private int messageShakeOffset() {
        long now = System.currentTimeMillis();
        if (now >= messageErrorUntil || messageErrorStarted == 0) return 0;
        float progress = Math.min(1.0f, (now - messageErrorStarted) / 720.0f);
        if (progress >= 1.0f) return 0;
        return Math.round((float) Math.sin(progress * Math.PI * 10.0) * (1.0f - progress) * 5.0f);
    }

    private void requestExit() {
        if (!exitAwaitingConfirmation && hasUnsavedDraft()) {
            exitAwaitingConfirmation = true;
            exit.setMessage(Component.translatable("localtools.button.confirm_exit"));
            exit.triggerError();
            return;
        }
        mod.discardAndCloseMenu();
    }

    @Override
    public void commitDraft() {
        applyDraft();
    }

    private void applyDraft() {
        AutoMessageConfig c = mod.config();
        c.message = message.getValue();
        c.intervalSeconds = number(interval, c.intervalSeconds);
        c.initialDelaySeconds = number(delay, c.initialDelaySeconds);
    }

    private void openAppearance() {
        if (hasUnsavedDraft()) mod.markMenuDirty();
        applyDraft();
        mod.showMenuScreen(new AppearanceScreen(mod, false));
    }

    private void openQueue() {
        if (hasUnsavedDraft()) mod.markMenuDirty();
        applyDraft();
        mod.showMenuScreen(new QueueScreen(mod));
    }

    private void openInformation() {
        if (hasUnsavedDraft()) mod.markMenuDirty();
        applyDraft();
        mod.showMenuScreen(new InformationScreen(mod));
    }

    private int number(EditBox box, int fallback) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void onClose() {
        mod.finishMenuClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClicked) {
        if (showTooltipIntro && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int closeX = noticeX + noticeWidth - 16;
            if (event.x() >= closeX && event.x() < closeX + 12
                    && event.y() >= noticeY + 3 && event.y() < noticeY + 15) {
                showTooltipIntro = false;
                if (suppressTooltipIntro) {
                    mod.config().hideTooltipIntro = true;
                    mod.config().save();
                }
                return true;
            }
            int rowY = noticeY + noticeHeight - 17;
            if (event.x() >= noticeX + 8 && event.x() < noticeX + noticeWidth - 8
                    && event.y() >= rowY && event.y() < rowY + 13) {
                suppressTooltipIntro = !suppressTooltipIntro;
                return true;
            }
            if (event.x() >= noticeX && event.x() < noticeX + noticeWidth
                    && event.y() >= noticeY && event.y() < noticeY + noticeHeight) {
                return super.mouseClicked(event, doubleClicked);
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && (event.x() < mainX || event.x() >= mainX + mainWidth
                || event.y() < panelY || event.y() >= panelY + PANEL_HEIGHT)) {
            requestUserClose();
            return true;
        }
        return super.mouseClicked(event, doubleClicked);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float fade = menuFade();
        if (message != null) {
            for (AbstractWidget widget : fadeWidgets) widget.setAlpha(fade);
            int fieldTextColor = applyAlpha(0xFFD0D1D3, fade);
            message.setTextColor(fieldTextColor);
            interval.setTextColor(fieldTextColor);
            delay.setTextColor(fieldTextColor);
        }
        updateSendButton();
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        drawMenuText(graphics, getTitle().getString(), mainX + 15, panelY + 10, 0xFFE3E4E6);

        renderPlaceholders(graphics);
        if (mod.config().showHud) {
            mod.renderHudAt(graphics, mod.hudTitle());
        }
        renderCustomTooltip(graphics, mouseX, mouseY);
        renderTooltipIntro(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float fade = backgroundFade() * mod.menuDimFactor();
        int topAlpha = Math.round(0xC0 * fade);
        int bottomAlpha = Math.round(0xD0 * fade);
        graphics.fillGradient(0, 0, width, height,
                (topAlpha << 24) | 0x00101010,
                (bottomAlpha << 24) | 0x00101010);
    }

    private boolean hasUnsavedDraft() {
        AutoMessageConfig c = mod.config();
        return !message.getValue().equals(c.message)
                || number(interval, c.intervalSeconds) != c.intervalSeconds
                || number(delay, c.initialDelaySeconds) != c.initialDelaySeconds
                || mod.hasUnsavedMenuChanges();
    }

    @Override
    public void requestUserClose() {
        mod.closeActiveMenu();
    }

    private void renderTooltipIntro(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!showTooltipIntro) return;
        int x = noticeX;
        int y = noticeY;
        int w = noticeWidth;
        int h = noticeHeight;
        int accent = mod.interfaceAccentColor();

        if (mod.config().background3d) {
            drawExtrusion(graphics, x, y, w, h, fadeColor(mod.menuDepthColor()));
        }
        graphics.fill(x, y, x + w, y + h, fadeColor(mod.menuSurfaceColor()));
        graphics.fill(x, y, x + 3, y + h, fadeColor(0xE6000000 | accent));

        List<FormattedCharSequence> lines = font.split(
                Component.translatable("localtools.notice.tooltip_hint"), w - 32);
        int textY = y + 7;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x + 10 + 1, textY + 1, fadeColor(0xC0000000), false);
            graphics.text(font, line, x + 10, textY, fadeColor(0xFFD0D1D3), false);
            textY += 10;
        }

        int rowY = y + h - 17;
        int boxColor = suppressTooltipIntro ? 0xFF000000 | accent : 0xFF35383C;
        graphics.fill(x + 9, rowY + 2, x + 18, rowY + 11, fadeColor(boxColor));
        if (suppressTooltipIntro) {
            String checkText = "x";
            int checkX = x + 9 + (9 - font.width(checkText)) / 2;
            graphics.text(font, checkText, checkX, rowY + 2,
                    fadeColor(0xFFE3E4E6), false);
        }
        mod.drawExtrudedText(graphics,
                Component.translatable("localtools.notice.dont_show").getString(),
                x + 23, rowY + 2, fadeColor(0xFFB8BBC0));

        int closeX = x + w - 16;
        boolean closeHovered = mouseX >= closeX && mouseX < closeX + 12
                && mouseY >= y + 3 && mouseY < y + 15;
        String closeText = "x";
        int closeTextX = closeX + (12 - font.width(closeText)) / 2;
        mod.drawExtrudedText(graphics, closeText, closeTextX, y + 5,
                fadeColor(closeHovered ? 0xFFFF9298 : 0xFFF06A6A));
    }

    private void renderPlaceholders(GuiGraphicsExtractor graphics) {
        int dotFrame = (int) ((System.currentTimeMillis() / 350L) % 4L);
        for (PlaceholderField placeholder : placeholders) {
            if (!placeholder.box.getValue().isEmpty()) continue;
            String text = placeholder.box.isFocused()
                    ? ".".repeat(dotFrame)
                    : placeholder.text.getString();
            int shake = placeholder.messageField ? messageShakeOffset() : 0;
            float error = placeholder.messageField ? messageErrorStrength() : 0.0f;
            int rgb = blendRgb(0xA8AAAD, 0xFF5966, error);
            mod.drawExtrudedText(graphics, text, placeholder.x + shake, placeholder.y,
                    fadeColor(0xFF000000 | rgb));
        }
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
        int x = Math.min(width - boxWidth - 5, mouseX + 12);
        int y = Math.min(height - boxHeight - 5, mouseY + 10);
        x = Math.max(5, x);
        y = Math.max(5, y);

        if (mod.config().background3d) {
            drawExtrusion(graphics, x, y, boxWidth, boxHeight, mod.menuDepthColor());
        }
        graphics.fill(x, y, x + boxWidth, y + boxHeight, mod.menuSurfaceColor());
        graphics.fill(x, y, x + 2, y + boxHeight, 0xFFE5C95A);
        graphics.fill(x + 2, y, x + boxWidth, y + 1, 0xFF3A3D42);
        mod.drawExtrudedText(graphics, title, x + 8, y + 6, 0xFFE5C95A);
        int lineY = y + 17;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x + 8 + 1, lineY + 1, 0xC0000000, false);
            graphics.text(font, line, x + 8, lineY, 0xFFD0D1D3, false);
            lineY += 10;
        }
    }

    private void drawMenuText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        int alpha = (color >>> 24) & 0xFF;
        int styled = mod.interfaceTextColor(alpha, color & 0xFFFFFF);
        mod.drawExtrudedText(graphics, text, x, y, fadeColor(styled));
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

    private float backgroundFade() {
        return closingAt > 0 ? menuFade() : 1.0f;
    }

    private int fadeColor(int color) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * menuFade());
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    static int applyAlpha(int color, float alphaMultiplier) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * alphaMultiplier);
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    static void drawExtrusion(GuiGraphicsExtractor graphics,
                              int x, int y, int width, int height, int color) {
        graphics.fill(x + width, y + 2, x + width + 2, y + height + 2, color);
        graphics.fill(x + 2, y + height, x + width, y + height + 2, color);
    }

    private record PlaceholderField(EditBox box, int x, int y, Component text,
                                    boolean messageField) {
    }

    private static int blendRgb(int from, int to, float amount) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return (r << 16) | (g << 8) | b;
    }

    private record TooltipTarget(AbstractWidget widget, Component text) {
    }

    static final class SoftEditBox extends EditBox {
        private final int hitX, hitY, hitWidth, hitHeight;
        private boolean renderingWithoutCursor;

        SoftEditBox(Font font, int x, int y, int width, int height,
                    Component narration, int hitX, int hitY, int hitWidth, int hitHeight) {
            super(font, x, y, width, height, narration);
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitWidth = hitWidth;
            this.hitHeight = hitHeight;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return visible
                    && mouseX >= hitX && mouseX < hitX + hitWidth
                    && mouseY >= hitY && mouseY < hitY + hitHeight;
        }

        @Override
        public boolean isFocused() {
            return !renderingWithoutCursor && super.isFocused();
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            renderingWithoutCursor = true;
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
            renderingWithoutCursor = false;
        }
    }

    static final class ValueSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final IntFunction<Component> messageFactory;
        private final IntConsumer onChange;
        private final IntSupplier accentColor;
        private final BooleanSupplier background3d;
        private final IntSupplier controlOpacity;
        private final IntSupplier textColor;

        ValueSlider(int x, int y, int width, int min, int max, int initial,
                    IntFunction<Component> messageFactory, IntConsumer onChange,
                    IntSupplier accentColor, BooleanSupplier background3d,
                    IntSupplier controlOpacity, IntSupplier textColor) {
            super(x, y, width, 20, Component.empty(), (initial - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.messageFactory = messageFactory;
            this.onChange = onChange;
            this.accentColor = accentColor;
            this.background3d = background3d;
            this.controlOpacity = controlOpacity;
            this.textColor = textColor;
            updateMessage();
        }

        int number() {
            return min + (int) Math.round(value * (max - min));
        }

        void setNumber(int number) {
            int clamped = Math.max(min, Math.min(number, max));
            value = (clamped - min) / (double) (max - min);
            updateMessage();
            applyValue();
        }

        @Override
        protected void updateMessage() {
            setMessage(messageFactory.apply(number()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(number());
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), width = getWidth(), height = getHeight();
            if (background3d.getAsBoolean()) {
                drawExtrusion(graphics, x, y, width, height, styledAlpha(0xA8000000));
            }
            graphics.fill(x, y, x + width, y + height, styledAlpha(0xFF25272A));
            int progressWidth = Math.max(4, (int) (width * value));
            graphics.fill(x, y, x + progressWidth, y + height,
                    styledAlpha(0xB8000000 | accentColor.getAsInt()));
            int handleX = x + (int) (value * (width - 6));
            graphics.fill(handleX, y, handleX + 6, y + height,
                    styledAlpha(isHoveredOrFocused() ? 0xFFDADCE0 : 0xFFAAAEB3));

            var font = Minecraft.getInstance().font;
            String text = getMessage().getString();
            int textX = x + (width - font.width(text)) / 2;
            graphics.text(font, text, textX + 1, y + 7, styledAlpha(0xC7000000), false);
            graphics.text(font, text, textX, y + 6, styledAlpha(textColor.getAsInt()), false);
        }

        private int styledAlpha(int color) {
            return applyAlpha(color, alpha * controlOpacity.getAsInt() / 255.0f);
        }
    }

    static final class ColorSwatch extends AbstractButton {
        private final int color;
        private final IntSupplier selectedColor;
        private final Runnable action;
        private final BooleanSupplier background3d;

        ColorSwatch(int x, int y, int width, int color, IntSupplier selectedColor,
                    Runnable action, BooleanSupplier background3d) {
            super(x, y, width, 18, Component.translatable("localtools.palette.color"));
            this.color = color;
            this.selectedColor = selectedColor;
            this.action = action;
            this.background3d = background3d;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), width = getWidth(), height = getHeight();
            if (background3d.getAsBoolean()) {
                drawExtrusion(graphics, x, y, width, height, applyAlpha(0xA8000000, alpha));
            }
            graphics.fill(x, y, x + width, y + height, applyAlpha(0xFF000000 | color, alpha));
            boolean selected = selectedColor.getAsInt() == color;
            if (selected || isHoveredOrFocused()) {
                int outline = selected ? 0xFFE0E1E3 : 0xFFB8BBC0;
                outline = applyAlpha(outline, alpha);
                graphics.fill(x - 1, y - 1, x + width + 1, y, outline);
                graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, outline);
                graphics.fill(x - 1, y, x, y + height, outline);
                graphics.fill(x + width, y, x + width + 1, y + height, outline);
            }
        }
    }

    static final class RgbSwatch extends AbstractButton {
        private final BooleanSupplier selected;
        private final IntSupplier rainbowColor;
        private final Runnable action;
        private final BooleanSupplier background3d;

        RgbSwatch(int x, int y, int width, int height, BooleanSupplier selected,
                  IntSupplier rainbowColor, Runnable action,
                  BooleanSupplier background3d) {
            super(x, y, width, height, Component.translatable("localtools.label.rgb_interface"));
            this.selected = selected;
            this.rainbowColor = rainbowColor;
            this.action = action;
            this.background3d = background3d;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), width = getWidth(), height = getHeight();
            if (background3d.getAsBoolean()) {
                drawExtrusion(graphics, x, y, width, height, applyAlpha(0xA8000000, alpha));
            }
            int color = rainbowColor.getAsInt();
            if (selected.getAsBoolean()) {
                graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1,
                        applyAlpha(0x88000000 | color, alpha));
            }
            graphics.fill(x, y, x + width, y + height, applyAlpha(0xFF000000 | color, alpha));
            if (selected.getAsBoolean() || isHoveredOrFocused()) {
                int outline = applyAlpha(selected.getAsBoolean() ? 0xFFE3E4E6 : 0xFFB8BBC0, alpha);
                graphics.fill(x - 1, y - 1, x + width + 1, y, outline);
                graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, outline);
                graphics.fill(x - 1, y, x, y + height, outline);
                graphics.fill(x + width, y, x + width + 1, y + height, outline);
            }
        }
    }

    static final class PickerSwatch extends AbstractButton {
        private final IntSupplier color;
        private final Runnable action;
        private final BooleanSupplier background3d;

        PickerSwatch(int x, int y, int width, int height,
                     IntSupplier color, Runnable action,
                     BooleanSupplier background3d) {
            super(x, y, width, height, Component.translatable("localtools.palette.custom"));
            this.color = color;
            this.action = action;
            this.background3d = background3d;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), width = getWidth(), height = getHeight();
            if (background3d.getAsBoolean()) {
                drawExtrusion(graphics, x, y, width, height, applyAlpha(0xA8000000, alpha));
            }
            graphics.fill(x, y, x + width, y + height,
                    applyAlpha(0xFF000000 | color.getAsInt(), alpha));
            var font = Minecraft.getInstance().font;
            String plus = "+";
            int textX = x + (width - font.width(plus)) / 2;
            graphics.text(font, plus, textX + 1, y + 5, applyAlpha(0xB8000000, alpha), false);
            graphics.text(font, plus, textX, y + 4, applyAlpha(0xFFE3E4E6, alpha), false);
            if (isHoveredOrFocused()) {
                int outline = applyAlpha(0xFFE3E4E6, alpha);
                graphics.fill(x - 1, y - 1, x + width + 1, y, outline);
                graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, outline);
                graphics.fill(x - 1, y, x, y + height, outline);
                graphics.fill(x + width, y, x + width + 1, y + height, outline);
            }
        }
    }

    static final class HelpIcon extends AbstractButton {
        HelpIcon(int x, int y) {
            super(x, y, 9, 10, Component.literal("?"));
        }

        @Override
        public void onPress(InputWithModifiers input) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            var font = Minecraft.getInstance().font;
            int color = isHoveredOrFocused() ? 0xFFFFE98A : 0xFFE5C95A;
            graphics.text(font, "?", getX() + 1, getY() + 1, applyAlpha(0xC0000000, alpha), false);
            graphics.text(font, "?", getX(), getY(), applyAlpha(color, alpha), false);
        }
    }
}
