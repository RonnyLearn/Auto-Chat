package ru.ilaro.localtools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class AutoMessageConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("local-tools.json");

    boolean enabled = false;
    boolean paused = false;
    int intervalSeconds = 300;
    int initialDelaySeconds = 20;
    String message = "";
    boolean queueEnabled = false;
    List<String> messageQueue = new ArrayList<>();
    boolean showHud = true;
    String hudTitle = "";
    int hudX = 6;
    int hudY = 6;
    int hudTextOpacity = 255;
    int hudBackgroundOpacity = 255;
    int hudScalePercent = 100;
    int hudAnchor = 0;
    boolean compactHud = false;
    boolean rgbHud = false;
    int rgbSpeedPercent = 100;
    boolean rgbAccents = true;
    boolean rgbText = false;
    boolean background3d = true;
    int menuFadeMillis = 600;
    int menuBackgroundOpacity = 255;
    int buttonOpacity = 255;
    boolean hideTooltipIntro = false;
    boolean dimMenuBackground = true;
    long automaticMessagesSent = 0;
    long manualMessagesSent = 0;
    long autoChatEnableCount = 0;
    long pauseActivationCount = 0;
    int accentColor = 0x3F7FC0;
    int configVersion = 8;

    static AutoMessageConfig load() {
        try {
            if (Files.exists(FILE)) {
                AutoMessageConfig config = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), AutoMessageConfig.class);
                if (config != null) {
                    config.normalize();
                    return config;
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        AutoMessageConfig config = new AutoMessageConfig();
        config.save();
        return config;
    }

    void normalize() {
        if (configVersion < 2) {
            background3d = true;
            configVersion = 2;
        }
        if (configVersion < 3) {
            rgbAccents = true;
            rgbText = false;
            menuFadeMillis = 600;
            menuBackgroundOpacity = 242;
            buttonOpacity = 255;
            configVersion = 3;
        }
        if (configVersion < 4) {
            rgbAccents = true;
            rgbText = false;
            configVersion = 4;
        }
        if (configVersion < 5) {
            hideTooltipIntro = false;
            configVersion = 5;
        }
        if (configVersion < 6) {
            configVersion = 6;
        }
        if (configVersion < 7) {
            if (messageQueue == null) messageQueue = new ArrayList<>();
            hudAnchor = 0;
            configVersion = 7;
        }
        if (configVersion < 8) {
            dimMenuBackground = true;
            configVersion = 8;
        }
        intervalSeconds = Math.max(60, Math.min(intervalSeconds, 86_400));
        initialDelaySeconds = Math.max(0, Math.min(initialDelaySeconds, 86_400));
        if (message == null
                || message.equals("Загляните в наш магазин!")
                || message.equals("Check out our shop!")) {
            message = "";
        }
        if (message.length() > 256) message = message.substring(0, 256);
        if (messageQueue == null) messageQueue = new ArrayList<>();
        List<String> normalizedQueue = new ArrayList<>();
        for (String queued : messageQueue) {
            if (queued == null || queued.isBlank()) continue;
            normalizedQueue.add(queued.length() > 256 ? queued.substring(0, 256) : queued);
            if (normalizedQueue.size() >= 6) break;
        }
        messageQueue = normalizedQueue;
        if (hudTitle == null) hudTitle = "";
        if (hudTitle.length() > 32) hudTitle = hudTitle.substring(0, 32);
        hudX = Math.max(0, hudX);
        hudY = Math.max(0, hudY);
        hudTextOpacity = Math.max(0, Math.min(hudTextOpacity, 255));
        hudBackgroundOpacity = Math.max(0, Math.min(hudBackgroundOpacity, 255));
        hudScalePercent = hudScalePercent == 0 ? 100 : Math.max(50, Math.min(hudScalePercent, 300));
        hudAnchor = Math.max(0, Math.min(hudAnchor, 4));
        rgbSpeedPercent = rgbSpeedPercent == 0 ? 100 : Math.max(25, Math.min(rgbSpeedPercent, 300));
        menuFadeMillis = Math.max(100, Math.min(menuFadeMillis, 5_000));
        menuBackgroundOpacity = Math.max(0, Math.min(menuBackgroundOpacity, 255));
        buttonOpacity = Math.max(0, Math.min(buttonOpacity, 255));
        automaticMessagesSent = Math.max(0, automaticMessagesSent);
        manualMessagesSent = Math.max(0, manualMessagesSent);
        autoChatEnableCount = Math.max(0, autoChatEnableCount);
        pauseActivationCount = Math.max(0, pauseActivationCount);
        accentColor &= 0xFFFFFF;
    }

    void save() {
        normalize();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    void resetAppearanceDefaults() {
        int preservedHudScale = hudScalePercent;
        showHud = true;
        hudTitle = "";
        hudX = 6;
        hudY = 6;
        hudTextOpacity = 255;
        hudBackgroundOpacity = 255;
        hudScalePercent = preservedHudScale;
        compactHud = false;
        hudAnchor = 0;
        rgbHud = false;
        rgbSpeedPercent = 100;
        rgbAccents = true;
        rgbText = false;
        background3d = true;
        dimMenuBackground = true;
        menuFadeMillis = 600;
        menuBackgroundOpacity = 255;
        buttonOpacity = 255;
        accentColor = 0x3F7FC0;
        save();
    }
}
