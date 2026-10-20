package com.sablednah.legendquest.client;

import com.sablednah.legendquest.network.PlatesPayload;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Which text displays are nameplates, as the server last said. Replaced whole on each payload. */
public final class ClientPlates {

    private static volatile java.util.Set<Integer> ids = java.util.Set.of();

    private ClientPlates() {
    }

    public static void accept(PlatesPayload payload) {
        java.util.Set<Integer> next = new java.util.HashSet<>();
        for (int id : payload.ids()) {
            next.add(id);
        }
        ids = java.util.Set.copyOf(next);
    }

    /**
     * Entity ids start again in every world, so last world's plates would name
     * some other entity in the next one (and lose it its shadow under a shader).
     */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ids = java.util.Set.of();
    }

    static boolean isPlate(int entityId) {
        return ids.contains(entityId);
    }
}
