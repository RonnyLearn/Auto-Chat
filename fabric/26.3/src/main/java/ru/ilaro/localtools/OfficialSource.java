package ru.ilaro.localtools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

public final class OfficialSource {
    private static final int FIRST_WARNING_DELAY_TICKS = 60;
    private static final int REPEAT_TICKS = 5 * 60 * 20;

    private final File dismissFile;
    private final List<String> officialHosts;
    private volatile boolean unofficial;
    private boolean dismissed;
    private boolean wasInWorld;
    private int ticksUntilWarning;

    public OfficialSource(File configDir, String modId, String markerEntry, String... officialHosts) {
        this.dismissFile = new File(configDir, modId + "-official-source.txt");
        this.officialHosts = Arrays.asList(officialHosts);
        this.dismissed = dismissFile.exists();
        if (!dismissed) {
            File modsDir = new File(configDir.getAbsoluteFile().getParentFile(), "mods");
            Thread check = new Thread(() -> unofficial = isAnyCopyUnofficial(modsDir, markerEntry), modId + " source check");
            check.setDaemon(true);
            check.start();
        }
    }

    public void tick(boolean inWorld, Runnable showWarning) {
        if (!inWorld) {
            wasInWorld = false;
            return;
        }
        if (!wasInWorld) {
            wasInWorld = true;
            ticksUntilWarning = FIRST_WARNING_DELAY_TICKS;
        }
        if (dismissed || !unofficial) {
            return;
        }
        if (--ticksUntilWarning <= 0) {
            ticksUntilWarning = REPEAT_TICKS;
            showWarning.run();
        }
    }

    public void dismiss() {
        dismissed = true;
        try (Writer writer = Files.newBufferedWriter(dismissFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("The warning about an unofficial download was hidden by the player.\n");
        } catch (IOException ignored) {
        }
    }

    private boolean isAnyCopyUnofficial(File modsDir, String markerEntry) {
        File[] jars = modsDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            return false;
        }
        for (File jar : jars) {
            if (containsEntry(jar, markerEntry) && isFromUnofficialSite(jar)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsEntry(File jar, String entry) {
        try (ZipFile zip = new ZipFile(jar)) {
            return zip.getEntry(entry) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isFromUnofficialSite(File jar) {
        List<String> urls = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(jar.getPath() + ":Zone.Identifier"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("HostUrl=") || line.startsWith("ReferrerUrl=")) {
                    urls.add(line.substring(line.indexOf('=') + 1).trim());
                }
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
        boolean anySite = false;
        for (String url : urls) {
            String host = hostOf(url);
            if (host == null) {
                continue;
            }
            anySite = true;
            if (isOfficial(host)) {
                return false;
            }
        }
        return anySite;
    }

    private boolean isOfficial(String host) {
        for (String official : officialHosts) {
            if (host.equals(official) || host.endsWith("." + official)) {
                return true;
            }
        }
        return false;
    }

    private static String hostOf(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null;
        }
        try {
            String host = new URI(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
