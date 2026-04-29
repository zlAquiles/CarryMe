package net.aquiles.carryme.util;

import net.aquiles.carryme.config.ConfigKeys;
import net.aquiles.carryme.service.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DismountListenerRegistrar {

    private static final String[] DISMOUNT_EVENT_CLASSES = {
            "org.spigotmc.event.entity.EntityDismountEvent",
            "org.bukkit.event.entity.EntityDismountEvent"
    };

    private final JavaPlugin plugin;
    private final MessageService messageService;

    public DismountListenerRegistrar(JavaPlugin plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
    }

    public void register() {
        for (String eventClassName : DISMOUNT_EVENT_CLASSES) {
            if (register(eventClassName)) {
                return;
            }
        }
    }

    private boolean register(String eventClassName) {
        Class<? extends Event> eventClass = findEventClass(eventClassName);
        if (eventClass == null) {
            return false;
        }

        Listener listener = new Listener() {
        };
        EventExecutor executor = new EventExecutor() {
            @Override
            public void execute(Listener ignored, Event event) throws EventException {
                handleDismount(event);
            }
        };

        plugin.getServer().getPluginManager().registerEvent(eventClass, listener, EventPriority.NORMAL, executor, plugin);
        return true;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> findEventClass(String eventClassName) {
        try {
            Class<?> rawClass = Class.forName(eventClassName);
            if (!Event.class.isAssignableFrom(rawClass)) {
                return null;
            }
            return (Class<? extends Event>) rawClass;
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private void handleDismount(Event event) {
        Object passenger = invoke(event, "getEntity");
        Object carrier = invoke(event, "getDismounted");
        if (passenger instanceof Player && carrier instanceof Player) {
            ((Player) carrier).sendMessage(messageService.getMessage(ConfigKeys.Messages.Actions.DROPPED)
                    .replace("%target%", ((Player) passenger).getName()));
        }
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }
}
