package io.github.soul4723.vanillapermissions.listener;

import net.luckperms.api.model.user.UserManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class LuckPermsPreLoginListenerCheck {

    public static void main(String[] args) {
        UUID id = UUID.randomUUID();
        String username = "prelogin-check";
        AtomicBoolean loaded = new AtomicBoolean();
        AtomicInteger loads = new AtomicInteger();

        UserManager manager = (UserManager) java.lang.reflect.Proxy.newProxyInstance(
            UserManager.class.getClassLoader(), new Class<?>[]{UserManager.class}, (proxy, method, values) -> {
                return switch (method.getName()) {
                    case "isLoaded" -> loaded.get();
                    case "loadUser" -> {
                        if (!id.equals(values[0]) || !username.equals(values[1])) {
                            throw new AssertionError("preload must retain UUID and username");
                        }
                        loads.incrementAndGet();
                        loaded.set(true);
                        yield CompletableFuture.completedFuture(null);
                    }
                    default -> throw new AssertionError("unexpected UserManager call: " + method.getName());
                };
            }
        );

        LuckPermsPreLoginListener.preloadIfNeeded(manager, id, username);
        LuckPermsPreLoginListener.preloadIfNeeded(manager, id, username);
        if (loads.get() != 1) throw new AssertionError("loaded user must not be loaded twice");
    }
}
