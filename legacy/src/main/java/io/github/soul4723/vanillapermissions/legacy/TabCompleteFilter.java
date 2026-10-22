package io.github.soul4723.vanillapermissions.legacy;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Filters denied sub-commands out of command tab completion through {@code TabCompleteEvent}, which exists from
 * Bukkit 1.9. This module compiles against the 1.8.8 API, so the event is found and registered by name; on 1.8.8 it
 * is absent and completion stays unfiltered (denied completions are still refused when run).
 */
final class TabCompleteFilter implements Listener, EventExecutor {

    private static final String EVENT = "org.bukkit.event.server.TabCompleteEvent";

    private final CommandGuard guard;
    private final Class<? extends Event> type;
    private final Method getSender;
    private final Method getBuffer;
    private final Method getCompletions;
    private final Method setCompletions;

    private TabCompleteFilter(CommandGuard guard, Class<? extends Event> type) throws NoSuchMethodException {
        this.guard = guard;
        this.type = type;
        getSender = type.getMethod("getSender");
        getBuffer = type.getMethod("getBuffer");
        getCompletions = type.getMethod("getCompletions");
        setCompletions = type.getMethod("setCompletions", List.class);
    }

    /** Registers the filter if this server has {@code TabCompleteEvent}; returns it, or {@code null} on 1.8.x. */
    static TabCompleteFilter register(Plugin plugin, CommandGuard guard) throws ReflectiveOperationException {
        Class<? extends Event> type;
        try {
            type = Class.forName(EVENT, false, Event.class.getClassLoader()).asSubclass(Event.class);
        } catch (ClassNotFoundException e) {
            return null;
        }
        TabCompleteFilter filter = new TabCompleteFilter(guard, type);
        plugin.getServer().getPluginManager().registerEvent(type, filter, EventPriority.HIGHEST, filter, plugin, true);
        return filter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Listener listener, Event event) throws EventException {
        if (!type.isInstance(event)) return;
        try {
            Object sender = getSender.invoke(event);
            if (!(sender instanceof Player)) return;
            List<String> completions = (List<String>) getCompletions.invoke(event);
            List<String> filtered = guard.filterCompletions((Player) sender, (String) getBuffer.invoke(event), completions);
            if (filtered != completions) setCompletions.invoke(event, filtered);
        } catch (InvocationTargetException e) {
            throw new EventException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new EventException(e);
        }
    }
}
