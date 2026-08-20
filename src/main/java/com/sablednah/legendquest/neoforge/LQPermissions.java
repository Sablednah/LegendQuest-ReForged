package com.sablednah.legendquest.neoforge;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.sablednah.legendquest.LQRegistries;
import com.sablednah.legendquest.LegendQuest;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission nodes, LuckPerms-compatible via NeoForge's PermissionAPI.
 *
 * <p>Nodes are enumerated from the race/class registries when the API
 * gathers (server start — {@code ServerLifecycleHooks.getCurrentServer()} is
 * already valid then, and the datapack registries are loaded):</p>
 *
 * <ul>
 * <li>{@code legendquest.admin} — the /lq admin tree (or vanilla op level).</li>
 * <li>{@code legendquest.race.<path>} — may this player select the race?
 *     Defaults to open; a race with a {@code perm} field in its data defaults
 *     to <b>closed</b> until a permission manager grants the node.</li>
 * <li>{@code legendquest.class.<path>} — same for classes.</li>
 * </ul>
 *
 * <p>Entries from other namespaces get {@code legendquest.race.<ns>.<path>}.
 * Note: nodes are gathered once per server start, so a race added by
 * {@code /reload} is selectable-by-default until the next restart registers
 * its node.</p>
 */
public final class LQPermissions {

    public static final PermissionNode<Boolean> ADMIN = new PermissionNode<>(
            LegendQuest.MODID, "admin", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> false);

    /**
     * Read other players' party chat. Defaults to false <em>including for
     * ops</em>: listening in is something a server owner grants deliberately,
     * not something that arrives with an op level. Even holding it, a listener
     * still has to switch themselves on with {@code /lq party spy on}.
     */
    public static final PermissionNode<Boolean> PARTY_SPY = new PermissionNode<>(
            LegendQuest.MODID, "party.spy", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> false);

    private static final Map<Identifier, PermissionNode<Boolean>> RACE_NODES = new HashMap<>();
    private static final Map<Identifier, PermissionNode<Boolean>> CLASS_NODES = new HashMap<>();

    @SubscribeEvent
    static void onGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(ADMIN, PARTY_SPY);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LegendQuest.LOGGER.warn("Permission gathering ran without a server; race/class nodes skipped");
            return;
        }
        RACE_NODES.clear();
        CLASS_NODES.clear();
        gather(event, server, LQRegistries.RACE, "race", RACE_NODES,
                key -> server.registryAccess().lookupOrThrow(LQRegistries.RACE)
                        .get(key).map(r -> r.value().perm().isPresent()).orElse(false));
        gather(event, server, LQRegistries.CHAR_CLASS, "class", CLASS_NODES,
                key -> server.registryAccess().lookupOrThrow(LQRegistries.CHAR_CLASS)
                        .get(key).map(r -> r.value().perm().isPresent()).orElse(false));
        LegendQuest.LOGGER.info("Registered {} race and {} class permission nodes",
                RACE_NODES.size(), CLASS_NODES.size());
    }

    private static <T> void gather(PermissionGatherEvent.Nodes event, MinecraftServer server,
            ResourceKey<Registry<T>> registry, String prefix,
            Map<Identifier, PermissionNode<Boolean>> into,
            java.util.function.Function<ResourceKey<T>, Boolean> lockedByData) {
        server.registryAccess().lookupOrThrow(registry).listElements().forEach(ref -> {
            Identifier id = ref.key().identifier();
            String path = id.getNamespace().equals(LegendQuest.MODID)
                    ? prefix + "." + id.getPath()
                    : prefix + "." + id.getNamespace() + "." + id.getPath();
            boolean locked = lockedByData.apply(ref.key());
            PermissionNode<Boolean> node = new PermissionNode<>(
                    LegendQuest.MODID, path, PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> !locked);
            into.put(id, node);
            event.addNodes(node);
        });
    }

    /** May this player select this race? Open when no node exists (post-/reload additions). */
    public static boolean canSelectRace(ServerPlayer player, Identifier raceId) {
        return check(player, RACE_NODES.get(raceId));
    }

    public static boolean canSelectClass(ServerPlayer player, Identifier classId) {
        return check(player, CLASS_NODES.get(classId));
    }

    /** May this player listen in on other people's party chat? */
    public static boolean canPartySpy(ServerPlayer player) {
        return PermissionAPI.getPermission(player, PARTY_SPY);
    }

    public static boolean isAdmin(ServerPlayer player) {
        return PermissionAPI.getPermission(player, ADMIN);
    }

    private static boolean check(ServerPlayer player, PermissionNode<Boolean> node) {
        return node == null || PermissionAPI.getPermission(player, node);
    }

    /** For list displays: is this entry gated at all for this player? */
    public static Optional<Boolean> raceLocked(ServerPlayer player, Identifier raceId) {
        PermissionNode<Boolean> node = RACE_NODES.get(raceId);
        return node == null ? Optional.empty()
                : Optional.of(!PermissionAPI.getPermission(player, node));
    }

    private LQPermissions() {}
}
