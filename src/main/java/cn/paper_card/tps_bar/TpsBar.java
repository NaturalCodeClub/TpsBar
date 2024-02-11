package cn.paper_card.tps_bar;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public final class TpsBar extends JavaPlugin {

    private double mspt = 0;

    private @NotNull HashMap<UUID, BossBar> playerTpsBar = new HashMap<>();
    private Gson gson = new Gson();
    private File file = new File(getDataFolder(), "config.json");


    @Override
    public void onEnable() {
        getLogger().info("Plugin enabling");
        if (canLoadJson(gson, file)) {
            try {
                playerTpsBar = gson.fromJson(new FileReader(file), new TypeToken<HashMap<UUID, BossBar>>() {
                }.getType());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                onDisable();
            }
        }

        this.getServer().getAsyncScheduler().runAtFixedRate(this, task -> {

            final double tps = this.mspt > 50 ? (1000 / this.mspt) : 20;

            synchronized (this.playerTpsBar) {
                for (final UUID uuid : this.playerTpsBar.keySet()) {
                    final BossBar bar = this.playerTpsBar.get(uuid);
                    if (bar == null) continue;

                    final Player player = this.getServer().getPlayer(uuid);
                    if (player == null || !player.isOnline()) continue;

                    bar.setTitle("MSPT: %.2f    TPS: %.2f   Ping: %dms".formatted(this.mspt, tps, player.getPing()));
                    bar.setProgress(this.mspt / 50);
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
                    final BossBar bossBar = playerTpsBar.get(event.getPlayer().getUniqueId());
                    if (bossBar != null) {
                        bossBar.addPlayer(event.getPlayer());
                    }
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
                BossBar bossBar = this.playerTpsBar.get(id);
                if (bossBar == null) {
                    bossBar = getServer().createBossBar(null, BarColor.GREEN, BarStyle.SEGMENTED_20);
                    bossBar.addPlayer(player);
                    this.playerTpsBar.put(id, bossBar);

                    commandSender.sendMessage(Component.text("已开启你的TpsBar"));

                } else {
                    bossBar.removeAll();
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
                executor.schedule(() -> {
                    saveJson(gson, file, playerTpsBar);
                }, 5, TimeUnit.MINUTES);
            }
        };
        getLogger().info("Plugin enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("Saving data to disk...");
        saveJson(gson, file, playerTpsBar);
        getLogger().info("Plugin disabled");
    }

    public void saveJson(Gson g, File f, HashMap<UUID, BossBar> map) {
        FileWriter writer;
        try {
            writer = new FileWriter(f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String str;
        str = g.toJson(map);
        try {
            writer.write(str);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean canLoadJson(Gson g, File f) {
        @NotNull HashMap<UUID, BossBar> map = new HashMap<>();
        try {
            map = g.fromJson(new FileReader(f), new TypeToken<HashMap<UUID, BossBar>>() {
            }.getType());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return !map.isEmpty();
    }

}
