package daylightcycle;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ClockTimeSkipEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class DaylightCyclePlugin extends JavaPlugin implements Listener {
    private static final long MINECRAFT_DAY_TICKS = 24000L;

    private final Map<UUID, Animation> animations = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Player>> sleepersByWorld = new ConcurrentHashMap<>();

    private long durationTicks;
    private long tickPeriod;
    private boolean clearWeatherOnFinish;
    private boolean normalWorldsOnly;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DaylightCycle enabled.");
    }

    @Override
    public void onDisable() {
        animations.values().forEach(Animation::cancel);
        animations.clear();
        sleepersByWorld.clear();
        Bukkit.getGlobalRegionScheduler().cancelTasks(this);
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(this, task -> {
            if (player.isSleeping()) {
                sleepersByWorld
                    .computeIfAbsent(player.getWorld().getUID(), key -> new ConcurrentHashMap<>())
                    .put(player.getUniqueId(), player);
            }
        }, null, 1L);
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        UUID worldId = player.getWorld().getUID();
        removeSleeper(player);
        cancelAnimationIfNoSleepers(worldId);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID worldId = player.getWorld().getUID();
        removeSleeper(player);
        cancelAnimationIfNoSleepers(worldId);
    }

    @EventHandler
    public void onTimeSkip(TimeSkipEvent event) {
        if (event.getSkipReason() != ClockTimeSkipEvent.SkipReason.NIGHT_SKIP) {
            return;
        }

        World world = event.getWorld();
        if (!shouldAnimate(world) || event.getSkipAmount() <= 0L) {
            return;
        }

        event.setCancelled(true);

        UUID worldId = world.getUID();
        if (animations.containsKey(worldId)) {
            return;
        }

        long startFullTime = world.getFullTime();
        long skipAmount = normalizeSkipAmount(event.getSkipAmount());
        long targetFullTime = startFullTime + skipAmount;
        Animation animation = new Animation(world, startFullTime, targetFullTime);
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            this,
            scheduledTask -> tickAnimation(worldId, animation, scheduledTask),
            1L,
            tickPeriod
        );
        animation.setTask(task);
        animations.put(worldId, animation);
    }

    private void reloadSettings() {
        durationTicks = resolveDurationTicks();
        tickPeriod = Math.max(1L, getConfig().getLong("tick-period", 1L));
        clearWeatherOnFinish = getConfig().getBoolean("clear-weather-on-finish", true);
        normalWorldsOnly = getConfig().getBoolean("normal-worlds-only", true);
    }

    private long resolveDurationTicks() {
        if (getConfig().contains("transition-seconds")) {
            double seconds = Math.max(0.05D, getConfig().getDouble("transition-seconds", 8.0D));
            return Math.max(1L, Math.round(seconds * 20.0D));
        }
        return Math.max(1L, getConfig().getLong("duration-ticks", 160L));
    }

    private boolean shouldAnimate(World world) {
        return !normalWorldsOnly || world.getEnvironment() == Environment.NORMAL;
    }

    private long normalizeSkipAmount(long skipAmount) {
        long normalized = skipAmount % MINECRAFT_DAY_TICKS;
        if (normalized <= 0L) {
            return skipAmount;
        }
        return normalized;
    }

    private void tickAnimation(UUID worldId, Animation animation, ScheduledTask task) {
        World world = animation.world();
        long elapsedTicks = animation.advance(tickPeriod);
        if (elapsedTicks >= durationTicks) {
            finishAnimation(worldId, animation, task);
            return;
        }

        double progress = (double) elapsedTicks / (double) durationTicks;
        long desiredFullTime = animation.startFullTime()
            + Math.round((animation.targetFullTime() - animation.startFullTime()) * smoothStep(progress));

        try {
            long newFullTime = Math.max(world.getFullTime(), desiredFullTime);
            world.setFullTime(newFullTime);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Could not animate time in world " + world.getName() + ": " + exception.getMessage());
            animations.remove(worldId);
            task.cancel();
        }
    }

    private void finishAnimation(UUID worldId, Animation animation, ScheduledTask task) {
        World world = animation.world();
        try {
            world.setFullTime(animation.targetFullTime());
            if (clearWeatherOnFinish) {
                world.setStorm(false);
                world.setThundering(false);
            }
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Could not finish time animation in world " + world.getName() + ": " + exception.getMessage());
        } finally {
            animations.remove(worldId);
            wakeSleepers(worldId);
            task.cancel();
        }
    }

    private double smoothStep(double progress) {
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private void wakeSleepers(UUID worldId) {
        Map<UUID, Player> sleepers = sleepersByWorld.remove(worldId);
        if (sleepers == null) {
            return;
        }

        for (Player player : sleepers.values()) {
            player.getScheduler().run(this, task -> {
                if (!player.isSleeping()) {
                    return;
                }
                try {
                    player.wakeup(false);
                } catch (IllegalStateException ignored) {
                }
            }, null);
        }
    }

    private void removeSleeper(Player player) {
        Map<UUID, Player> sleepers = sleepersByWorld.get(player.getWorld().getUID());
        if (sleepers == null) {
            return;
        }

        sleepers.remove(player.getUniqueId());
        if (sleepers.isEmpty()) {
            sleepersByWorld.remove(player.getWorld().getUID(), sleepers);
        }
    }

    private void cancelAnimationIfNoSleepers(UUID worldId) {
        Map<UUID, Player> sleepers = sleepersByWorld.get(worldId);
        if (sleepers != null && !sleepers.isEmpty()) {
            return;
        }

        Animation animation = animations.remove(worldId);
        if (animation != null) {
            animation.cancel();
        }
    }

    private static final class Animation {
        private final World world;
        private final long startFullTime;
        private final long targetFullTime;
        private long elapsedTicks;
        private ScheduledTask task;

        private Animation(World world, long startFullTime, long targetFullTime) {
            this.world = world;
            this.startFullTime = startFullTime;
            this.targetFullTime = targetFullTime;
        }

        private World world() {
            return world;
        }

        private long startFullTime() {
            return startFullTime;
        }

        private long targetFullTime() {
            return targetFullTime;
        }

        private long advance(long ticks) {
            elapsedTicks += ticks;
            return elapsedTicks;
        }

        private void setTask(ScheduledTask task) {
            this.task = task;
        }

        private void cancel() {
            if (task != null) {
                task.cancel();
            }
        }
    }
}
