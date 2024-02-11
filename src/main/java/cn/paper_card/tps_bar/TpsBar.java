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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TpsBar extends JavaPlugin {

    private double mspt = 0;

    private @NotNull HashSet<UUID> playerTpsBar = new HashSet<>();
    private Thread autoSaveThread;
    private Gson gson = new Gson();
    private File file = new File(getDataFolder(), "config.json");
    private BossBar bossBar = getServer().createBossBar(null, BarColor.GREEN, BarStyle.SEGMENTED_20);
    private BufferedWriter configWriter;


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
        try {
            configWriter = new BufferedWriter(new FileWriter(file,false));
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
                        saveJson(gson, file, playerTpsBar, configWriter);
                        getLogger().info("Auto Save Data Succeed");
                    }, 5, TimeUnit.MINUTES);
            }
        }.start();
        getLogger().info("Plugin enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("Saving data to disk...");
        saveJson(gson, file, playerTpsBar, configWriter);
        autoSaveThread.interrupt();
        try {
            configWriter.flush();
            configWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        getLogger().info("Plugin disabled");
    }

    public void saveJson(Gson g, File f, HashSet<UUID> set, @NotNull Writer writer) {
        String str;
        str = g.toJson(set);
        try {
            writer.write(str);
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

}
