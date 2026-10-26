package ru.ilaro.localtools;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.awt.Color;
import java.net.URI;

public final class LocalToolsClient implements ClientModInitializer {
    private static final String OFFICIAL_PAGE = "https://modrinth.com/mod/auto-chat";
    private static final String WARNING_LINE = "━".repeat(40);

    private AutoMessageConfig config;
    private OfficialSource officialSource;
    private long nextMessageAt = -1;
    private long pausedRemainingMillis = -1;
    private int countdownTotalSeconds;
    private boolean lastHudVisible;
    private long hudShownAt = System.currentTimeMillis();
    private long stateChangedAt;
    private long menuKeyHandledAt;
    private KeyMapping menuKey;
    private KeyMapping pauseKey;
    private KeyMapping resetAppearanceKey;
    private boolean menuOpen;
    private boolean menuKeyWasDown;
    private FadingMenuScreen activeMenuScreen;
    private Screen fadingScreen;
    private long fadingUntil;
    private int queueIndex;
    private boolean menuDirty;
    private boolean wasInWorld;
    private long manualSendCooldownUntil;
    private float menuDimProgress = -1.0f;
    private long menuDimUpdatedAt;

    @Override
    public void onInitializeClient() {
        config = AutoMessageConfig.load();
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("localtools", "controls"));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.localtools.open_menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F8,
                category));
        pauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.localtools.toggle_pause",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F9,
                category));
        resetAppearanceKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.localtools.reset_appearance", InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F10, category));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("localtools")
                        .then(ClientCommands.literal("hint").executes(context -> {
                            config.hideTooltipIntro = false;
                            config.save();
                            return 1;
                        }))
                        .then(ClientCommands.literal("hidewarning").executes(context -> {
                            if (officialSource != null) officialSource.dismiss();
                            sendFeedback(Component.translatable("message.localtools.source.hidden")
                                    .withStyle(ChatFormatting.GRAY));
                            return 1;
                        }))));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("localtools", "autoad_status"), (graphics, delta) -> renderHud(graphics));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("localtools", "menu_fade"), (graphics, delta) -> renderFadingMenu(graphics));
    }

    private void tickOfficialSource(Minecraft client) {
        if (officialSource == null) {
            officialSource = new OfficialSource(FabricLoader.getInstance().getConfigDir().toFile(), "localtools",
                    "ru/ilaro/localtools/LocalToolsClient.class", "modrinth.com");
        }
        officialSource.tick(client.player != null, this::showOfficialSourceWarning);
    }

    private void showOfficialSourceWarning() {
        sendFeedback(Component.literal(WARNING_LINE).withStyle(ChatFormatting.DARK_RED));
        sendFeedback(Component.translatable("message.localtools.source.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        sendFeedback(Component.translatable("message.localtools.source.danger").withStyle(ChatFormatting.RED));
        sendFeedback(Component.translatable("message.localtools.source.support").withStyle(ChatFormatting.GOLD));
        sendFeedback(Component.translatable("message.localtools.source.link").withStyle(style -> style
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(OFFICIAL_PAGE)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(OFFICIAL_PAGE)))));
        sendFeedback(Component.translatable("message.localtools.source.hide").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/localtools hidewarning"))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.localtools.source.hide.hover")))));
        sendFeedback(Component.literal(WARNING_LINE).withStyle(ChatFormatting.DARK_RED));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.125F));
    }

    private static void sendFeedback(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.sendSystemMessage(message);
    }

    private void tick(Minecraft client) {
        tickOfficialSource(client);
        if (fadingScreen != null && System.currentTimeMillis() >= fadingUntil) fadingScreen = null;
        while (pauseKey.consumeClick()) togglePaused();
        while (resetAppearanceKey.consumeClick()) resetAppearanceSettings();
        if (menuOpen) {
            boolean keyDown = isMenuKeyDown(client);
            if (keyDown && !menuKeyWasDown) closeMenu(client);
            menuKeyWasDown = keyDown;
        } else {
            menuKeyWasDown = false;
            while (menuKey.consumeClick()) {
                toggleMenu(client);
                menuKeyWasDown = true;
            }
        }
        if (config.showHud && !lastHudVisible) hudShownAt = System.currentTimeMillis();
        lastHudVisible = config.showHud;
        boolean inWorld = client.player != null && client.getConnection() != null;
        if (!inWorld) {
            if (wasInWorld && config.enabled && !config.paused) {
                long now = System.currentTimeMillis();
                pausedRemainingMillis = nextMessageAt < 0
                        ? config.initialDelaySeconds * 1_000L
                        : Math.max(0, nextMessageAt - now);
                config.paused = true;
                stateChangedAt = now;
                config.save();
            }
            wasInWorld = false;
            return;
        }
        wasInWorld = true;
        if (!config.enabled) return;
        long now = System.currentTimeMillis();
        if (config.paused) {
            if (pausedRemainingMillis < 0) {
                pausedRemainingMillis = nextMessageAt < 0
                        ? config.initialDelaySeconds * 1_000L
                        : Math.max(0, nextMessageAt - now);
            }
            return;
        }
        if (pausedRemainingMillis >= 0) {
            nextMessageAt = now + pausedRemainingMillis;
            pausedRemainingMillis = -1;
        }
        if (nextMessageAt < 0) {
            countdownTotalSeconds = config.initialDelaySeconds;
            nextMessageAt = now + config.initialDelaySeconds * 1_000L;
        }
        if (now >= nextMessageAt) {
            String message = nextAutomaticMessage();
            if (!message.isBlank()) {
                client.getConnection().sendChat(message);
                config.automaticMessagesSent++;
                config.save();
            }
            countdownTotalSeconds = config.intervalSeconds;
            nextMessageAt = now + config.intervalSeconds * 1_000L;
        }
    }

    private void renderHud(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        if (!config.showHud) return;
        renderHudAt(graphics, hudTitle());
    }

    private void renderFadingMenu(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        if (fadingScreen == null) return;
        fadingScreen.extractBackground(graphics, -1, -1, 1.0f);
        fadingScreen.extractRenderState(graphics, -1, -1, 1.0f);
    }

    void renderHudAt(net.minecraft.client.gui.GuiGraphicsExtractor graphics, String title) {
        String state = stateText();
        int width = hudWidth(title);
        applyHudAnchor(width);
        float appear = Math.min(1.0f, (System.currentTimeMillis() - hudShownAt) / 220.0f);
        float scale = config.hudScalePercent / 100.0f * (0.96f + appear * 0.04f);
        int backgroundAlpha = Math.round(config.hudBackgroundOpacity * appear);
        int textAlpha = Math.round(config.hudTextOpacity * appear);
        graphics.pose().pushMatrix();
        graphics.pose().translate(config.hudX, config.hudY);
        graphics.pose().scale(scale, scale);
        drawHudBackground(graphics, 0, 0, width, 22, backgroundAlpha << 24);
        int textX = 7;
        if (!config.compactHud) {
            String titlePart = title + ": ";
            int titleColor = config.rgbHud && config.rgbText
                    ? rainbowColor(textAlpha)
                    : (textAlpha << 24) | 0x00D0D1D3;
            drawExtrudedText(graphics, titlePart, textX, 7, titleColor);
            textX += Minecraft.getInstance().font.width(titlePart);
        }
        drawExtrudedText(graphics, state, textX, 7, stateColor(textAlpha));
        textX += Minecraft.getInstance().font.width(state);
        if (config.enabled) {
            String timer = " | " + secondsRemaining() + " " + net.minecraft.network.chat.Component.translatable("localtools.seconds").getString();
            drawExtrudedText(graphics, timer, textX, 7, timerColor(textAlpha));
        }
        graphics.pose().popMatrix();
    }

    AutoMessageConfig config() { return config; }

    boolean handleMenuKey(KeyEvent event) {
        if (!menuKey.matches(event)) return false;
        requestMenuClose(Minecraft.getInstance());
        return true;
    }

    private void toggleMenu(Minecraft client) {
        long now = System.currentTimeMillis();
        if (now - menuKeyHandledAt < 150L) return;
        menuKeyHandledAt = now;
        if (menuOpen) {
            requestMenuClose(client);
        } else {
            menuOpen = true;
            menuDirty = false;
            fadingScreen = null;
            client.gui.setScreen(new AutoAdScreen(this, true));
        }
    }

    void menuOpened(FadingMenuScreen screen) {
        menuOpen = true;
        activeMenuScreen = screen;
    }

    void menuClosed() {
        menuOpen = false;
        activeMenuScreen = null;
    }

    private void closeMenu(Minecraft client) {
        requestMenuClose(client);
    }

    void closeActiveMenu() {
        requestMenuClose(Minecraft.getInstance());
    }

    void discardAndCloseMenu() {
        discardUnsavedMenuChanges();
        requestMenuClose(Minecraft.getInstance(), false);
    }

    private void requestMenuClose(Minecraft client) {
        requestMenuClose(client, true);
    }

    private void requestMenuClose(Minecraft client, boolean keepChanges) {
        if (activeMenuScreen == null) {
            finishMenuClose();
            return;
        }
        if (keepChanges) {
            activeMenuScreen.commitDraft();
            config.save();
            menuDirty = false;
        }
        fadingScreen = (Screen) activeMenuScreen;
        fadingUntil = System.currentTimeMillis() + config.menuFadeMillis;
        activeMenuScreen.startClosing();
        activeMenuScreen = null;
        menuOpen = false;
        menuKeyHandledAt = System.currentTimeMillis();
        client.gui.setScreen(null);
    }

    void showMenuScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    void finishMenuClose() {
        menuOpen = false;
        activeMenuScreen = null;
        menuKeyHandledAt = System.currentTimeMillis();
        Minecraft.getInstance().gui.setScreen(null);
    }

    private boolean isMenuKeyDown(Minecraft client) {
        InputConstants.Key boundKey = KeyMappingHelper.getBoundKeyOf(menuKey);
        if (boundKey.getValue() < 0) return false;
        if (boundKey.getType() == InputConstants.Type.MOUSE) return menuKey.isDown();
        return InputConstants.isKeyDown(boundKey.getValue());
    }
    int secondsRemaining() {
        if (config.paused && pausedRemainingMillis >= 0) {
            return (int) Math.max(0, (pausedRemainingMillis + 999) / 1_000);
        }
        return nextMessageAt < 0
                ? config.initialDelaySeconds
                : (int) Math.max(0, (nextMessageAt - System.currentTimeMillis() + 999) / 1000);
    }

    void toggleEnabled() {
        config.enabled = !config.enabled;
        if (config.enabled) config.autoChatEnableCount++;
        stateChangedAt = System.currentTimeMillis();
        nextMessageAt = -1;
        pausedRemainingMillis = -1;
        queueIndex = 0;
        config.save();
    }

    void togglePaused() {
        long now = System.currentTimeMillis();
        if (!config.paused) {
            pausedRemainingMillis = nextMessageAt < 0
                    ? config.initialDelaySeconds * 1_000L
                    : Math.max(0, nextMessageAt - now);
            config.paused = true;
            config.pauseActivationCount++;
        } else {
            config.paused = false;
            nextMessageAt = now + Math.max(0, pausedRemainingMillis);
            pausedRemainingMillis = -1;
        }
        stateChangedAt = now;
        config.save();
    }

    void resetTimer() {
        resetQueue();
        long delayMillis = config.initialDelaySeconds * 1_000L;
        countdownTotalSeconds = config.initialDelaySeconds;
        if (config.paused) {
            pausedRemainingMillis = delayMillis;
        } else if (config.enabled) {
            nextMessageAt = System.currentTimeMillis() + delayMillis;
            pausedRemainingMillis = -1;
        } else {
            nextMessageAt = -1;
            pausedRemainingMillis = -1;
        }
        stateChangedAt = System.currentTimeMillis();
    }

    void resetAppearanceSettings() {
        config.resetAppearanceDefaults();
        menuDirty = false;
    }

    void discardUnsavedMenuChanges() {
        config = AutoMessageConfig.load();
        menuDirty = false;
        queueIndex = 0;
    }

    boolean sendNow(String message) {
        Minecraft client = Minecraft.getInstance();
        if (message == null || message.isBlank() || client.player == null || client.getConnection() == null) return false;
        client.getConnection().sendChat(message);
        config.manualMessagesSent++;
        config.save();
        countdownTotalSeconds = config.intervalSeconds;
        if (config.paused) {
            pausedRemainingMillis = config.intervalSeconds * 1_000L;
        } else {
            nextMessageAt = System.currentTimeMillis() + config.intervalSeconds * 1_000L;
        }
        return true;
    }

    String nextManualMessage(String draftMessage) {
        return hasQueuedMessages() ? nextAutomaticMessage() : draftMessage;
    }

    int timerColor(int alpha) {
        int total = countdownTotalSeconds;
        if (total <= 0) total = config.initialDelaySeconds > 0 ? config.initialDelaySeconds : config.intervalSeconds;
        float ratio = total <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, secondsRemaining() / (float) total));
        int rgb = Color.HSBtoRGB(ratio / 3.0f, 0.92f, 1.0f) & 0xFFFFFF;
        return (alpha << 24) | rgb;
    }

    int hudWidth(String title) {
        int longestTimer = Math.max(config.intervalSeconds, config.initialDelaySeconds);
        String prefix = config.compactHud ? "" : title + ": ";
        String timer = " | " + longestTimer + " " + net.minecraft.network.chat.Component.translatable("localtools.seconds").getString();
        String enabled = prefix + net.minecraft.network.chat.Component.translatable("localtools.state.on").getString() + timer;
        String paused = prefix + net.minecraft.network.chat.Component.translatable("localtools.state.paused").getString() + timer;
        String disabled = prefix + net.minecraft.network.chat.Component.translatable("localtools.state.off").getString();
        var font = Minecraft.getInstance().font;
        return Math.max(font.width(disabled), Math.max(font.width(enabled), font.width(paused))) + 12;
    }

    int scaledHudWidth(String title) {
        return (int) Math.ceil(hudWidth(title) * config.hudScalePercent / 100.0);
    }

    int scaledHudHeight() {
        return (int) Math.ceil(22 * config.hudScalePercent / 100.0);
    }

    private void applyHudAnchor(int unscaledWidth) {
        if (config.hudAnchor == 0) {
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            int scaledWidth = (int) Math.ceil(unscaledWidth * config.hudScalePercent / 100.0);
            int scaledHeight = scaledHudHeight();
            config.hudX = Math.max(0, Math.min(Math.max(0, screenWidth - scaledWidth), config.hudX));
            config.hudY = Math.max(0, Math.min(Math.max(0, screenHeight - scaledHeight), config.hudY));
            return;
        }
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int scaledWidth = (int) Math.ceil(unscaledWidth * config.hudScalePercent / 100.0);
        int scaledHeight = scaledHudHeight();
        int margin = 6;
        config.hudX = switch (config.hudAnchor) {
            case 2, 4 -> Math.max(0, screenWidth - scaledWidth - margin);
            default -> margin;
        };
        config.hudY = switch (config.hudAnchor) {
            case 3, 4 -> Math.max(0, screenHeight - scaledHeight - margin);
            default -> margin;
        };
    }

    String hudTitle() {
        return config.hudTitle.isBlank() || config.hudTitle.equals("Авточат")
                ? net.minecraft.network.chat.Component.translatable("localtools.hud.title").getString()
                : config.hudTitle;
    }

    String messageText() {
        return config.message;
    }

    boolean hasAutomaticMessage(String draftMessage) {
        return hasQueuedMessages()
                || draftMessage != null && !draftMessage.isBlank();
    }

    private boolean hasQueuedMessages() {
        return config.queueEnabled && !config.messageQueue.isEmpty();
    }

    void resetQueue() {
        queueIndex = 0;
    }

    private String nextAutomaticMessage() {
        if (config.queueEnabled && !config.messageQueue.isEmpty()) {
            String queued = config.messageQueue.get(queueIndex % config.messageQueue.size());
            queueIndex = (queueIndex + 1) % config.messageQueue.size();
            return queued;
        }
        return messageText();
    }

    void drawHudBackground(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        int darkBackground = (color & 0xFF000000) | 0x00151515;
        if (config.background3d) {
            graphics.fill(x + width, y + 2, x + width + 2, y + height + 2, 0xA8000000);
            graphics.fill(x + 2, y + height, x + width, y + height + 2, 0xA8000000);
        }
        graphics.fill(x, y, x + width, y + height, darkBackground);
        int accentRgb = config.rgbHud && config.rgbAccents
                ? rainbowColor(255) & 0xFFFFFF
                : config.accentColor;
        int accent = ((color >>> 24) << 24) | accentRgb;
        graphics.fill(x, y, x + 2, y + height, accent);
    }

    private String stateText() {
        String key = config.paused
                ? "localtools.state.paused"
                : config.enabled ? "localtools.state.on" : "localtools.state.off";
        return net.minecraft.network.chat.Component.translatable(key).getString();
    }

    private int stateColor(int alpha) {
        int rgb = config.paused ? 0xE9C75F : config.enabled ? 0x68D391 : 0xF06A6A;
        long elapsed = System.currentTimeMillis() - stateChangedAt;
        if (elapsed >= 0 && elapsed < 520) {
            float pulse = (float) Math.sin(elapsed / 520.0 * Math.PI) * 0.28f;
            rgb = blendRgb(rgb, 0xFFFFFF, pulse);
        }
        return (alpha << 24) | rgb;
    }

    private int rainbowColor(int alpha) {
        double speed = config.rgbSpeedPercent / 100.0;
        long period = Math.max(1_200L, Math.round(6_000L / speed));
        float hue = (System.currentTimeMillis() % period) / (float) period;
        return (alpha << 24) | (Color.HSBtoRGB(hue, 0.78f, 1.0f) & 0xFFFFFF);
    }

    int interfaceAccentColor() {
        return config.rgbHud && config.rgbAccents
                ? rainbowColor(255) & 0xFFFFFF
                : config.accentColor;
    }

    int interfaceRainbowColor() {
        return rainbowColor(255) & 0xFFFFFF;
    }

    int interfaceTextColor(int alpha, int fallbackRgb) {
        return config.rgbHud && config.rgbText
                ? rainbowColor(alpha)
                : (alpha << 24) | (fallbackRgb & 0xFFFFFF);
    }

    int menuSurfaceColor() {
        return (config.menuBackgroundOpacity << 24) | 0x001B1D20;
    }

    float menuDimFactor() {
        long now = System.currentTimeMillis();
        if (menuDimProgress < 0.0f) {
            menuDimProgress = config.dimMenuBackground ? 1.0f : 0.0f;
            menuDimUpdatedAt = now;
            return menuDimProgress;
        }
        float elapsed = Math.min(100L, Math.max(0L, now - menuDimUpdatedAt));
        menuDimUpdatedAt = now;
        float step = elapsed / 300.0f;
        if (config.dimMenuBackground) {
            menuDimProgress = Math.min(1.0f, menuDimProgress + step);
        } else {
            menuDimProgress = Math.max(0.0f, menuDimProgress - step);
        }
        return menuDimProgress;
    }

    long manualSendCooldownRemainingMillis() {
        return Math.max(0L, manualSendCooldownUntil - System.currentTimeMillis());
    }

    void startManualSendCooldown() {
        manualSendCooldownUntil = System.currentTimeMillis() + 15_000L;
    }

    int menuDepthColor() {
        int alpha = Math.min(0xA8,
                Math.round(0xA8 * config.menuBackgroundOpacity / 242.0f));
        return alpha << 24;
    }

    String modVersion() {
        return FabricLoader.getInstance().getModContainer("localtools")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    String informationStateText() {
        return stateText();
    }

    void resetStatistics() {
        config.automaticMessagesSent = 0;
        config.manualMessagesSent = 0;
        config.autoChatEnableCount = 0;
        config.pauseActivationCount = 0;
        config.save();
    }

    void markMenuDirty() {
        menuDirty = true;
    }

    void clearMenuDirty() {
        menuDirty = false;
    }

    boolean hasUnsavedMenuChanges() {
        return menuDirty;
    }

    private static int blendRgb(int from, int to, float amount) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return (r << 16) | (g << 8) | b;
    }

    void drawExtrudedText(net.minecraft.client.gui.GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        int alpha = (color >>> 24) & 0xFF;
        int shadow = (Math.round(alpha * 0.82f) << 24);
        var font = Minecraft.getInstance().font;
        graphics.text(font, text, x + 1, y + 1, shadow, false);
        graphics.text(font, text, x, y, color, false);
    }
}
