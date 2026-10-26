package ru.ilaro.localtools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;

final class DarkButton extends AbstractButton {
    private final Runnable action;
    private final IntSupplier accentColor;
    private final BooleanSupplier background3d;
    private final IntSupplier buttonOpacity;
    private final IntSupplier textColor;
    private float hoverProgress;
    private long pulseStarted;
    private long errorStarted;

    DarkButton(int x, int y, int width, Component message, Runnable action,
               IntSupplier accentColor, BooleanSupplier background3d,
               IntSupplier buttonOpacity, IntSupplier textColor) {
        super(x, y, width, 20, message);
        this.action = action;
        this.accentColor = accentColor;
        this.background3d = background3d;
        this.buttonOpacity = buttonOpacity;
        this.textColor = textColor;
    }

    void pulse() { pulseStarted = System.currentTimeMillis(); }

    void triggerError() { errorStarted = System.currentTimeMillis(); }

    @Override public void onPress(InputWithModifiers input) { action.run(); }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        long errorElapsed = System.currentTimeMillis() - errorStarted;
        float error = errorElapsed >= 0 && errorElapsed < 1_050
                ? (float) Math.sin(errorElapsed / 1_050.0 * Math.PI) * 0.92f : 0.0f;
        int shake = errorElapsed >= 0 && errorElapsed < 720
                ? Math.round((float) Math.sin(errorElapsed / 720.0 * Math.PI * 10.0)
                * (1.0f - errorElapsed / 720.0f) * 5.0f) : 0;
        x += shake;
        float target = isHoveredOrFocused() ? 1.0f : 0.0f;
        hoverProgress += (target - hoverProgress) * 0.22f;
        int background = blend(0xFF292B2E, 0xFF34373B, hoverProgress);
        int accent = blend(0xFF000000 | accentColor.getAsInt(),
                brighten(0xFF000000 | accentColor.getAsInt(), 12), hoverProgress);
        long pulseElapsed = System.currentTimeMillis() - pulseStarted;
        if (pulseElapsed >= 0 && pulseElapsed < 480) {
            float pulse = (float) Math.sin(pulseElapsed / 480.0 * Math.PI) * 0.22f;
            background = blend(background, 0xFF000000 | accentColor.getAsInt(), pulse);
        }
        background = blend(background, 0xFF6A2027, error);
        accent = blend(accent, 0xFFF04F5F, error);

        if (background3d.getAsBoolean()) drawExtrusion(graphics, x, y, width, height, withAlpha(0xB8000000));
        graphics.fill(x, y, x + width, y + height, withAlpha(background));
        graphics.fill(x, y, x + 2, y + height, withAlpha(accent));

        var font = Minecraft.getInstance().font;
        String text = getMessage().getString();
        int textX = x + (width - font.width(text)) / 2 + 1;
        int textY = y + 6;
        graphics.text(font, text, textX + 1, textY + 1, withAlpha(0xD0000000), false);
        int foreground = active ? textColor.getAsInt() : 0xFF888888;
        graphics.text(font, text, textX, textY, withAlpha(foreground), false);
    }

    private void drawExtrusion(GuiGraphicsExtractor graphics,
                               int x, int y, int width, int height, int color) {
        graphics.fill(x + width, y + 2, x + width + 2, y + height + 2, color);
        graphics.fill(x + 2, y + height, x + width, y + height + 2, color);
    }

    private int withAlpha(int color) {
        int colorAlpha = Math.round(((color >>> 24) & 0xFF) * alpha * buttonOpacity.getAsInt() / 255.0f);
        return (colorAlpha << 24) | (color & 0xFFFFFF);
    }

    private static int blend(int from, int to, float amount) {
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * amount);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int brighten(int color, int amount) {
        int a = color & 0xFF000000;
        int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int b = Math.min(255, (color & 0xFF) + amount);
        return a | (r << 16) | (g << 8) | b;
    }
}
