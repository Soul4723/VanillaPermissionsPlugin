package io.github.soul4723.vanillapermissions.listener;

import net.luckperms.api.model.user.UserManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuckPermsPreLoginListenerTest {

    @Test
    void preloadsUserOnceWithLoginIdentity() {
        UUID id = UUID.randomUUID();
        String username = "prelogin-check";
        AtomicBoolean loaded = new AtomicBoolean();
        AtomicInteger loads = new AtomicInteger();

        UserManager manager = (UserManager) Proxy.newProxyInstance(
            UserManager.class.getClassLoader(), new Class<?>[]{UserManager.class}, (proxy, method, values) ->
                switch (method.getName()) {
                    case "isLoaded" -> loaded.get();
                    case "loadUser" -> {
                        assertEquals(id, values[0], "preload must retain UUID");
                        assertEquals(username, values[1], "preload must retain username");
                        loads.incrementAndGet();
                        loaded.set(true);
                        yield CompletableFuture.completedFuture(null);
                    }
                    default -> throw new AssertionError("unexpected UserManager call: " + method.getName());
                });

        LuckPermsPreLoginListener.preloadIfNeeded(manager, id, username);
        LuckPermsPreLoginListener.preloadIfNeeded(manager, id, username);
        assertEquals(1, loads.get(), "loaded user must not be loaded twice");
    }
}
