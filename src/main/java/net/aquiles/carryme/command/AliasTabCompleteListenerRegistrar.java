package net.aquiles.carryme.command;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public class AliasTabCompleteListenerRegistrar {

    private static final String TAB_COMPLETE_EVENT_CLASS = "org.bukkit.event.server.TabCompleteEvent";

    private final JavaPlugin plugin;
    private final CarryCommandHandler commandHandler;

    public AliasTabCompleteListenerRegistrar(JavaPlugin plugin, CarryCommandHandler commandHandler) {
        this.plugin = plugin;
        this.commandHandler = commandHandler;
    }

    public void register() {
        Class<? extends Event> eventClass = findEventClass();
        if (eventClass == null) {
            return;
        }

        Listener listener = new Listener() {
        };
        EventExecutor executor = new EventExecutor() {
            @Override
            public void execute(Listener ignored, Event event) throws EventException {
                handleTabComplete(event);
            }
        };

        plugin.getServer().getPluginManager().registerEvent(eventClass, listener, EventPriority.HIGHEST, executor, plugin);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Event> findEventClass() {
        try {
            Class<?> rawClass = Class.forName(TAB_COMPLETE_EVENT_CLASS);
            if (!Event.class.isAssignableFrom(rawClass)) {
                return null;
            }
            return (Class<? extends Event>) rawClass;
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private void handleTabComplete(Event event) {
        Object buffer = invoke(event, "getBuffer");
        Object sender = invoke(event, "getSender");
        if (!(buffer instanceof String) || !(sender instanceof CommandSender)) {
            return;
        }

        List<String> completions = commandHandler.getAliasSuggestions((CommandSender) sender, (String) buffer);
        if (completions == null) {
            return;
        }

        invoke(event, "setCompletions", List.class, completions);
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }

    private Object invoke(Object target, String methodName, Class<?> parameterType, Object argument) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            return method.invoke(target, argument);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }
}
