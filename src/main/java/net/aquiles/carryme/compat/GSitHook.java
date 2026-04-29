package net.aquiles.carryme.compat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class GSitHook implements CarryBlockerHook {

    private static final List<String> API_CLASS_NAMES = Arrays.asList(
            "dev.geco.gsit.api.GSitAPI",
            "dev.geco.gsit.api.GSitApi"
    );
    private static final List<String> METHOD_NAMES = Arrays.asList(
            "isSitting",
            "isPlayerSitting",
            "isLaying",
            "isPlayerLaying",
            "isCrawling",
            "isPlayerCrawling",
            "isPosing",
            "isPlayerPosing"
    );

    private final JavaPlugin plugin;
    private final List<Method> stateMethods = new ArrayList<>();

    public GSitHook(JavaPlugin plugin) {
        this.plugin = plugin;
        loadApiMethods();
    }

    @Override
    public String getPluginName() {
        return "GSit";
    }

    @Override
    public boolean blocksCarry(Player player) {
        for (Method method : stateMethods) {
            try {
                Object result = method.invoke(null, player);
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("GSit hook failed while checking player state: " + exception.getMessage());
                return false;
            }
        }
        return false;
    }

    public boolean isAvailable() {
        return !stateMethods.isEmpty();
    }

    private void loadApiMethods() {
        Class<?> apiClass = findApiClass();
        if (apiClass == null) {
            return;
        }

        for (String methodName : METHOD_NAMES) {
            Method method = findStateMethod(apiClass, methodName);
            if (method != null) {
                stateMethods.add(method);
            }
        }
    }

    private Class<?> findApiClass() {
        for (String className : API_CLASS_NAMES) {
            try {
                return Class.forName(className, true, plugin.getClass().getClassLoader());
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private Method findStateMethod(Class<?> apiClass, String methodName) {
        for (Method method : apiClass.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!method.getName().equalsIgnoreCase(methodName)) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(Player.class)) {
                continue;
            }
            if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
                continue;
            }
            return method;
        }
        return null;
    }
}
