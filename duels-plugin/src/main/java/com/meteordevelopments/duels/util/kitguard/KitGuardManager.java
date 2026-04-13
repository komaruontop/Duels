package com.meteordevelopments.duels.util.kitguard;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.util.DateUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// AI-GENERATED (and some beautified by me..)
public class KitGuardManager {
    private static int MAX_PER_PLAYER = 200;

    private static DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Getter
    private static KitGuardManager instance;

    @Setter
    @Getter
    private volatile boolean detectionEnabled = true;
    private Map<UUID, List<Violation>> violations = new ConcurrentHashMap<>();
    private PrintWriter fileWriter;

    private KitGuardManager(DuelsPlugin plugin) {
        openLogFile(plugin);
    }

    public static KitGuardManager init(DuelsPlugin plugin) {
        instance = new KitGuardManager(plugin);
        return instance;
    }

    public void shutdown() {
        if (fileWriter != null) {
            fileWriter.flush();
            fileWriter.close();
        }
        instance = null;
    }

    public void logViolation(Player violator, String eventType, String itemName, String ownerUUID, String ownerName, String world, int x, int y, int z) {
        Violation v = new Violation(Instant.now(), eventType, itemName, ownerUUID, ownerName, world, x, y, z);

        violations.compute(violator.getUniqueId(), (uuid, list) -> {
            if (list == null)
                list = new CopyOnWriteArrayList<>();
            list.add(v);
            while (list.size() > MAX_PER_PLAYER)
                list.remove(0);
            return list;
        });

        writeToFile(violator, v);
    }

    public void logMachineViolation(String ownerUUID, String ownerName, String eventType, String itemName) {
        if (ownerUUID == null)
            return;
        UUID uuid;
        try {
            uuid = UUID.fromString(ownerUUID);
        } catch (IllegalArgumentException e) {
            return;
        }
        Violation v = new Violation(Instant.now(), eventType, itemName, ownerUUID, ownerName, "?", 0, 0, 0);
        violations.compute(uuid, (u, list) -> {
            if (list == null)
                list = new CopyOnWriteArrayList<>();
            list.add(v);
            while (list.size() > MAX_PER_PLAYER)
                list.remove(0);
            return list;
        });
        if (fileWriter != null)
            fileWriter.printf("[%s] MECHANISM player=? owner=%s(%s) event=%s item=%s%n", TS_FMT.format(v.timestamp()), ownerName != null ? ownerName : "?", ownerUUID, eventType, itemName);
    }

    public List<Violation> getViolations(UUID uuid) {
        return violations.getOrDefault(uuid, Collections.emptyList());
    }

    public Map<UUID, List<Violation>> getAllViolations() {
        return Collections.unmodifiableMap(violations);
    }

    public int totalCount() {
        return violations.values().stream().mapToInt(List::size).sum();
    }

    public void clearAll() {
        violations.clear();
    }

    public void clear(UUID uuid) {
        violations.remove(uuid);
    }

    private void openLogFile(DuelsPlugin plugin) {
        try {
            File logsDir = new File(plugin.getDataFolder(), "logs");
            logsDir.mkdirs();
            File file = new File(logsDir, "kitguard-" + DateUtil.formatDate(new Date()) + ".log");
            fileWriter = new PrintWriter(new FileWriter(file, true), true);
        } catch (IOException e) {
            plugin.getLogger().warning("[KitGuard] Could not open log file: " + e.getMessage());
        }
    }

    private void writeToFile(Player violator, Violation v) {
        if (fileWriter == null)
            return;
        fileWriter.printf("[%s] VIOLATION player=%s(%s) event=%s item=%s owner=%s(%s) at %s [%d,%d,%d]%n", TS_FMT.format(v.timestamp()), violator.getName(), violator.getUniqueId(), v.eventType(), v.itemName(), v.ownerName() != null ? v.ownerName() : "?", v.ownerUUID() != null ? v.ownerUUID() : "?", v.world(), v.x(), v.y(), v.z());
    }

    public record Violation(Instant timestamp, String eventType, String itemName, String ownerUUID, String ownerName, String world, int x, int y, int z) {
        public String format() {
            return String.format("[%s] %s — item: %s | owner: %s(%s) | at %s [%d,%d,%d]", TS_FMT.format(timestamp), eventType, itemName, ownerName != null ? ownerName : "?", ownerUUID != null ? ownerUUID.substring(0, 8) + "…" : "?", world, x, y, z);
        }
    }
}
