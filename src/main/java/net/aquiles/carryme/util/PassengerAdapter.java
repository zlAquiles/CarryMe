package net.aquiles.carryme.util;

import org.bukkit.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PassengerAdapter {

    public boolean hasPassengers(Entity entity) {
        return !getPassengers(entity).isEmpty();
    }

    public List<Entity> getPassengers(Entity entity) {
        if (entity == null) {
            return Collections.emptyList();
        }

        Method getPassengersMethod = findMethod(entity.getClass(), "getPassengers");
        Object passengers = invoke(entity, getPassengersMethod);
        if (passengers instanceof Collection<?>) {
            List<Entity> result = new ArrayList<>();
            for (Object passenger : (Collection<?>) passengers) {
                if (passenger instanceof Entity) {
                    result.add((Entity) passenger);
                }
            }
            return result;
        }

        Method getPassengerMethod = findMethod(entity.getClass(), "getPassenger");
        Object passenger = invoke(entity, getPassengerMethod);
        if (passenger instanceof Entity) {
            return Collections.singletonList((Entity) passenger);
        }

        return Collections.emptyList();
    }

    public boolean addPassenger(Entity carrier, Entity passenger) {
        if (carrier == null || passenger == null) {
            return false;
        }

        Method addPassengerMethod = findMethod(carrier.getClass(), "addPassenger", Entity.class);
        Object added = invoke(carrier, addPassengerMethod, passenger);
        if (added instanceof Boolean) {
            return (Boolean) added;
        }
        if (addPassengerMethod != null) {
            return true;
        }

        Method setPassengerMethod = findMethod(carrier.getClass(), "setPassenger", Entity.class);
        Object set = invoke(carrier, setPassengerMethod, passenger);
        return setPassengerMethod != null && (!(set instanceof Boolean) || (Boolean) set);
    }

    public boolean removePassenger(Entity carrier, Entity passenger) {
        if (carrier == null || passenger == null) {
            return false;
        }

        Method removePassengerMethod = findMethod(carrier.getClass(), "removePassenger", Entity.class);
        Object removed = invoke(carrier, removePassengerMethod, passenger);
        if (removed instanceof Boolean) {
            return (Boolean) removed;
        }
        if (removePassengerMethod != null) {
            return true;
        }

        if (getPassengers(carrier).contains(passenger)) {
            return carrier.eject();
        }
        return false;
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
            return null;
        }
    }
}
