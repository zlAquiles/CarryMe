package net.aquiles.carryme.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;

public class PlatformScheduler {

    private final JavaPlugin plugin;
    private final boolean foliaRuntime;
    private final Method getAsyncSchedulerMethod;
    private final Method getGlobalRegionSchedulerMethod;
    private final Method isOwnedByCurrentRegionMethod;

    public PlatformScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.foliaRuntime = detectFoliaRuntime();
        this.getAsyncSchedulerMethod = findMethod(plugin.getServer().getClass(), "getAsyncScheduler", 0);
        this.getGlobalRegionSchedulerMethod = findMethod(plugin.getServer().getClass(), "getGlobalRegionScheduler", 0);
        this.isOwnedByCurrentRegionMethod = findMethod(Bukkit.class, "isOwnedByCurrentRegion", Entity.class);
    }

    public boolean isFoliaRuntime() {
        return foliaRuntime;
    }

    public void runAsync(Runnable task) {
        Object asyncScheduler = invoke(plugin.getServer(), getAsyncSchedulerMethod);
        Method runNowMethod = asyncScheduler == null ? null : findMethod(asyncScheduler.getClass(), "runNow", 2);
        if (asyncScheduler != null && runNowMethod != null) {
            invoke(asyncScheduler, runNowMethod, plugin, asConsumer(task));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runGlobal(Runnable task) {
        Object globalScheduler = invoke(plugin.getServer(), getGlobalRegionSchedulerMethod);
        Method executeMethod = globalScheduler == null ? null : findMethod(globalScheduler.getClass(), "execute", 2);
        if (globalScheduler != null && executeMethod != null) {
            invoke(globalScheduler, executeMethod, plugin, task);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void executeEntity(Entity entity, Runnable task) {
        if (entity == null) {
            return;
        }

        if (canAccessEntity(entity)) {
            task.run();
            return;
        }

        Object entityScheduler = getEntityScheduler(entity);
        Method executeMethod = entityScheduler == null ? null : findMethod(entityScheduler.getClass(), "execute", 4);
        if (entityScheduler != null && executeMethod != null) {
            invoke(entityScheduler, executeMethod, plugin, task, null, 1L);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, task);
    }

    public ScheduledTaskHandle runEntityLater(Entity entity, long delayTicks, Runnable task) {
        if (entity == null) {
            return ScheduledTaskHandle.NOOP;
        }

        Object entityScheduler = getEntityScheduler(entity);
        Method runDelayedMethod = entityScheduler == null ? null : findMethod(entityScheduler.getClass(), "runDelayed", 4);
        if (entityScheduler != null && runDelayedMethod != null) {
            Object scheduledTask = invoke(entityScheduler, runDelayedMethod, plugin, asConsumer(task), null, Math.max(1L, delayTicks));
            if (scheduledTask == null) {
                return ScheduledTaskHandle.NOOP;
            }

            return () -> invoke(scheduledTask, findMethod(scheduledTask.getClass(), "cancel", 0));
        }

        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        return bukkitTask::cancel;
    }

    public boolean canAccessEntity(Entity entity) {
        if (entity == null) {
            return false;
        }

        if (isOwnedByCurrentRegionMethod != null) {
            Object result = invoke(null, isOwnedByCurrentRegionMethod, entity);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        }

        return Bukkit.isPrimaryThread();
    }

    private Object getEntityScheduler(Entity entity) {
        Method getEntitySchedulerMethod = findMethod(entity.getClass(), "getScheduler", 0);
        return invoke(entity, getEntitySchedulerMethod);
    }

    private Consumer<Object> asConsumer(Runnable task) {
        return ignored -> task.run();
    }

    private boolean detectFoliaRuntime() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private Method findMethod(Class<?> type, String name, int parameterCount) {
        if (type == null) {
            return null;
        }

        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getParameterCount() == parameterCount)
                .min(Comparator.comparingInt(Method::getParameterCount))
                .orElse(null);
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        if (type == null) {
            return null;
        }

        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Object invoke(Object target, Method method, Object... arguments) {
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not access scheduler compatibility method " + method.getName(), exception);
        }
    }
}
