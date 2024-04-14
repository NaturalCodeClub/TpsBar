package cn.paper_card.tps_bar;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class TpsBar extends JavaPlugin {
    //TODO Add Config to configure the time of auto-saving
    private double mspt = 0;

    private @NotNull HashSet<UUID> playerTpsBar = new HashSet<>();
    private Thread autoSaveThread;
    private Gson gson = new Gson();
    public FileConfiguration langConfig;
    public FileConfiguration config;
    public File configFile = new File(getDataFolder(), "config.yml");
    public File langConfigFile = new File(getDataFolder(), "lang.yml");
    public static int autoSaveTime;
    private File file = new File(getDataFolder(), "config.json");
    private BossBar bossBar = getServer().createBossBar(null, BarColor.GREEN, BarStyle.SEGMENTED_20);
    private FileOutputStream configOutput;


    @Override
    public void onEnable() {
        getLogger().info("Plugin enabling");
        if (canLoadJson(gson, file)) {
            try {
                playerTpsBar = gson.fromJson(new FileReader(file), new TypeToken<HashSet<UUID>>() {
                }.getType());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                onDisable();
            }
        }
        loadConfigConfig(configFile);
        config = YamlConfiguration.loadConfiguration(configFile);
        autoSaveTime = config.getInt("auto_save",5);
        try {
            configOutput = new FileOutputStream(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.getServer().getAsyncScheduler().runAtFixedRate(this, task -> {

            final double tps = this.mspt > 50 ? (1000 / this.mspt) : 20;

            synchronized (this.playerTpsBar) {
                for (final UUID uuid : this.playerTpsBar) {

                    final Player player = this.getServer().getPlayer(uuid);
                    if (player == null || !player.isOnline()) continue;

                    bossBar.setTitle("MSPT: %.2f    TPS: %.2f   Ping: %dms".formatted(this.mspt, tps, player.getPing()));
                    bossBar.setProgress(this.mspt / 50);
                }
            }

        }, 1, 1, TimeUnit.SECONDS);


        this.getServer().getPluginManager().registerEvents(new Listener() {

            private long startTime = -1;
            private int ticks = 0;
            private long time = 0;

            @EventHandler
            public void on1(@NotNull ServerTickStartEvent event) {
                this.startTime = System.currentTimeMillis();
            }

            @EventHandler
            public void on2(@NotNull ServerTickEndEvent event) {
                final long time = System.currentTimeMillis() - this.startTime;
                this.time += time;
                ++this.ticks;
                if (this.ticks == 20) {
                    TpsBar.this.mspt = (double) this.time / this.ticks;
                    this.ticks = 0;
                    this.time = 0;
                }
            }

        }, this);

        this.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void on1(@NotNull PlayerJoinEvent event) {
                synchronized (playerTpsBar) {
                    if (playerTpsBar.contains(event.getPlayer().getUniqueId())) {
                        bossBar.addPlayer(event.getPlayer());
                    }
                }
            }

            @EventHandler
            public void on2(@NotNull PlayerQuitEvent event) {
                synchronized (playerTpsBar) {
                    if (playerTpsBar.contains(event.getPlayer().getUniqueId())) bossBar.removePlayer(event.getPlayer());
                }
            }

        }, this);

        final PluginCommand command = this.getCommand("tps-bar");
        assert command != null;
        command.setExecutor((commandSender, command1, s, strings) -> {
            if (!(commandSender instanceof final Player player)) {
                commandSender.sendMessage(Component.text("该命令只能由玩家来执行"));
                return true;
            }

            final UUID id = player.getUniqueId();

            synchronized (this.playerTpsBar) {
                if (!playerTpsBar.contains(((Player) commandSender).getUniqueId())) {
                    bossBar.addPlayer(player);
                    this.playerTpsBar.add(id);
                    commandSender.sendMessage(Component.text("已开启你的TpsBar"));

                } else {
                    bossBar.removePlayer(Objects.requireNonNull(((Player) commandSender).getPlayer()));
                    this.playerTpsBar.remove(id);
                    commandSender.sendMessage(Component.text("已关闭你的TpsBar"));
                }
            }

            return true;
        });
        new Thread() {
            final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);


            @Override
            public void run() {
                autoSaveThread = Thread.currentThread();
                executor.schedule(() -> {
                    saveJson(gson, file, playerTpsBar, configOutput);
                    getLogger().info("Auto Save Data Succeed");
                }, autoSaveTime, TimeUnit.MINUTES);
            }
        }.start();
        getLogger().info("Plugin enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("Saving data to disk...");
        saveJson(gson, file, playerTpsBar, configOutput);
        autoSaveThread.interrupt();
        getLogger().info("Plugin disabled");
    }

    public void saveJson(Gson g, File f, HashSet<UUID> set, @NotNull OutputStream output) {
        String str;
        str = g.toJson(set);
        try (BufferedOutputStream o = new BufferedOutputStream(output)) {
            o.write(str.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean canLoadJson(Gson g, File f) {
        @NotNull HashSet<UUID> set;
        if (!f.exists()) {
            if (!f.getParentFile().exists()) f.getParentFile().mkdir();
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            set = g.fromJson(new FileReader(f), new TypeToken<HashSet<UUID>>() {
            }.getType());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return set != null;
    }

    //TODO finish it
    public void loadConfigConfig(File f) {
        if (!f.exists()) {
            if (!f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                getLogger().log(Level.SEVERE, "Server config cannot be crated,please check you file permission");
                onDisable();
            }
            FileConfiguration fc = YamlConfiguration.loadConfiguration(f);
            fc.set("auto_save", 5);
            try {
                fc.save(f);
            } catch (IOException e) {
                e.printStackTrace();
                getLogger().log(Level.SEVERE, "Server config cannot be crated,please check you file permission");
                onDisable();
            }
        }
    }
}
