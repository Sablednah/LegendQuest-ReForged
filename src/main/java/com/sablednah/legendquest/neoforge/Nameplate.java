package com.sablednah.legendquest.neoforge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.sablednah.legendquest.LQConfig;
import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.data.CharClass;
import com.sablednah.legendquest.data.Race;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Character information floating above a player's head.
 *
 * <p><b>Why a text display and not a scoreboard team.</b> A team prefix is the
 * obvious way to decorate a name and it does reach unmodded clients — but it is
 * not scoped to the nameplate. Vanilla builds a player's rendered name from the
 * team-decorated display name in four places: the nameplate, the tab list,
 * {@code /list} and <em>chat</em>. A team therefore cannot show a stat block
 * above a head without also dragging it through every chat line, and it fights
 * LuckPerms, FTB Ranks and every chat-prefix mod for the one team slot a player
 * has. This was built with a team first and thrown away for exactly that: the
 * info tag turned up in chat, where Standards is meant to own the line.</p>
 *
 * <p>A {@code text_display} above the player touches only the space above them.
 * Chat, tab and {@code /list} are left completely alone, nothing competes for
 * it, and an unmodded client renders it because it is an ordinary vanilla
 * entity.</p>
 *
 * <p><b>It follows the player rather than riding them.</b> Mounting would be
 * neater, but {@code Entity.startRiding} refuses outright when the vehicle's
 * type cannot serialize, and {@code EntityType.PLAYER} cannot — players are
 * saved separately from the entity list. That check sits <em>before</em> the
 * {@code force} flag, so nothing can be mounted on a player server-side at all;
 * plugins that appear to manage it are sending passenger packets to clients
 * behind the server's back. So the display is repositioned each tick instead,
 * with {@code teleport_duration} set so clients interpolate between those
 * positions rather than stuttering one tick at a time.</p>
 */
public final class Nameplate {

    /** Marks our displays so they can be found again and never orphaned. */
    private static final String TAG = "legendquest.nameplate";

    /** Height above the player's feet. Clears a standing player's head. */
    private static final double Y_OFFSET = 2.35D;

    /**
     * The display entity per player. Held rather than searched for, since it is
     * no longer a passenger and so has nothing to be found through. Every read
     * revalidates it — a stale entry after a respawn or a dimension change is
     * the expected case, not an exceptional one.
     */
    private static final Map<UUID, Display.TextDisplay> PLATES = new ConcurrentHashMap<>();

    /**
     * Last rendered text per player, so the common case costs one string
     * comparison. {@link CharacterSync#send} runs once a second for every
     * player to tick mana, and rebuilding entity NBT at that rate for text
     * that has not changed would be waste.
     */
    private static final Map<UUID, String> LAST = new ConcurrentHashMap<>();

    /**
     * True for a game mode that is not playing the game.
     *
     * <p><b>Spectator is the one that matters.</b> A spectator is invisible to
     * everybody else — but a text display following them is an ordinary entity
     * and is not, so the plate hovers in mid-air announcing exactly where the
     * invisible person is. That turns the nameplate into a position leak, and
     * the people most likely to be spectating are staff watching somebody they
     * would rather not tip off.</p>
     *
     * <p>Creative is included on the same principle, less urgently: someone
     * building is not adventuring, and a floating "Dwarf Fighter | Lvl 60" over
     * a builder is noise. It is also the mode an admin flips into to fix
     * something, where the plate is in the way rather than informative.</p>
     */
    private static boolean notPlaying(ServerPlayer player) {
        return player.isSpectator() || player.isCreative();
    }

    /** Rebuilds the plate if anything visible changed. Cheap when it has not. */
    public static void refresh(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // Dead counts as "no plate" for the same reason switched-off does. This
        // check belongs HERE and not only in follow(): the per-second character
        // sync calls refresh() for every online player, so clearing the plate
        // in follow() alone just meant rebuilding it a fraction of a second
        // later, for as long as the death screen stayed up. Two paths decide
        // whether a plate exists, and both have to agree.
        if (!LQConfig.NAMEPLATE_ENABLED.get() || CharacterService.data(player).nameplateHidden()
                || !player.isAlive() || notPlaying(player)) {
            clear(player);
            return;
        }

        String text = (render("nameplate.prefix", player) + render("nameplate.suffix", player)).trim();
        // Nothing to say: no empty entity hanging over their head.
        if (text.isEmpty()) {
            clear(player);
            return;
        }

        Display.TextDisplay display = live(player);
        if (display == null) {
            display = EntityType.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
            if (display == null) return;
            display.snapTo(player.getX(), player.getY() + Y_OFFSET, player.getZ(), 0.0F, 0.0F);
            display.addTag(TAG);
            apply(display, level, text);
            // Tracked BEFORE it joins the level, because joining fires the
            // event that reaps untracked plates -- add it first and we would
            // reap our own the instant we made it.
            PLATES.put(player.getUUID(), display);
            level.addFreshEntity(display);
            LAST.put(player.getUUID(), text);
            return;
        }

        if (text.equals(LAST.get(player.getUUID()))) return;
        LAST.put(player.getUUID(), text);
        apply(display, level, text);
    }

    /**
     * Keeps every plate over its owner. Runs every tick, unlike
     * {@link #refresh}: a position updated once a second would trail a walking
     * player by whole blocks.
     */
    public static void follow(net.minecraft.server.MinecraftServer server) {
        if (PLATES.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Display.TextDisplay display = live(player);
            if (display == null) continue;
            // A dead player stays in the player list until they click respawn,
            // so without this the plate carries on tracking their corpse: a
            // name badge hovering over a body, reading as "here I am, fine" at
            // the exact moment neither is true.
            //
            // Cleared rather than hidden, and it costs one pass: clear() drops
            // the PLATES entry, so live() returns null on every tick after and
            // this branch is never reached again. The plate returns by itself
            // on respawn, when PlayerRespawnEvent refreshes the character and
            // CharacterSync rebuilds it.
            // Also here, not only in refresh(): refresh runs once a second, and
            // a plate that lingers even that long over someone who just went
            // spectator is a second of telling everybody where they are.
            if (!player.isAlive() || notPlaying(player)) {
                clear(player);
                continue;
            }
            display.snapTo(player.getX(), player.getY() + Y_OFFSET, player.getZ(), 0.0F, 0.0F);
        }
    }

    /**
     * Removes any nameplate that is not one of ours, whenever one loads.
     *
     * <p><b>Why orphans happen at all.</b> {@link #PLATES} lives in memory, but
     * the display is an ordinary entity and is saved to the region file like
     * any other. So a plate that outlives its bookkeeping — a crash between
     * spawning one and shutting down, a player logging out while their plate
     * sat in a chunk nobody was near — is invisible to us the moment the server
     * restarts. The map is empty; the entity is not. One was found still
     * hovering after several restarts and two Minecraft version migrations.</p>
     *
     * <p>Hooking the moment an entity <em>joins a level</em> catches them
     * wherever they are: chunk load, world load, migration. The test is simply
     * "is this one of the plates I am currently tracking" — after a restart
     * nothing is, so every survivor is swept as its chunk comes in.</p>
     *
     * <p>This makes the plate self-cleaning rather than merely tidied-up-after,
     * which matters because the old sweep in {@link #clear} only looked within
     * eight blocks of the player being cleared, and an orphan is by definition
     * somewhere nobody is looking.</p>
     */
    public static void reapIfOrphan(Entity entity) {
        if (!(entity instanceof Display.TextDisplay display)) return;
        if (!display.entityTags().contains(TAG)) return;
        if (PLATES.containsValue(display)) return;
        display.discard();
    }

    /** Drops every plate we are holding. Called as the server stops. */
    public static void clearAll() {
        for (Display.TextDisplay display : PLATES.values()) {
            display.discard();
        }
        PLATES.clear();
        LAST.clear();
    }

    /** Removes the plate. Called on logout, and when it is switched off. */
    public static void clear(ServerPlayer player) {
        LAST.remove(player.getUUID());
        Display.TextDisplay tracked = PLATES.remove(player.getUUID());
        if (tracked != null) tracked.discard();
        // Belt and braces: any plate of ours nearby that we have lost track of.
        // A crash between spawning one and shutting down would otherwise leave
        // it floating in the world for good, and they are invisible to us once
        // the map entry is gone.
        if (player.level() instanceof ServerLevel level) {
            for (Entity entity : level.getEntities(
                    EntityTypeTest.forClass(Display.TextDisplay.class),
                    player.getBoundingBox().inflate(8.0D),
                    e -> e.entityTags().contains(TAG))) {
                entity.discard();
            }
        }
    }

    /**
     * This player's plate if it still exists in their current world, else null
     * after dropping the stale entry. Death, respawn and dimension changes all
     * arrive here as "removed, or somewhere else", and are handled by simply
     * building a new one next refresh.
     */
    private static Display.TextDisplay live(ServerPlayer player) {
        Display.TextDisplay display = PLATES.get(player.getUUID());
        if (display == null) return null;
        if (display.isRemoved() || display.level() != player.level()) {
            display.discard();
            PLATES.remove(player.getUUID());
            LAST.remove(player.getUUID());
            return null;
        }
        return display;
    }

    /**
     * Configures the display through NBT. Every setter on {@code TextDisplay}
     * is private, so loading save data is the supported way in — the same path
     * a {@code /summon} takes.
     */
    private static void apply(Display.TextDisplay display, ServerLevel level, String text) {
        CompoundTag tag = new CompoundTag();
        tag.put("text", ComponentSerialization.CODEC
                .encodeStart(NbtOps.INSTANCE, Feedback.colored(text))
                .getOrThrow());
        // Always face the reader, and only turn the text -- "center" billboards
        // both axes, so the plate does not tip over when you look down on it.
        tag.putString("billboard", "center");
        tag.putBoolean("see_through", false);
        tag.putBoolean("shadow", false);
        tag.putString("alignment", "center");
        // The plate is moved every tick; without an interpolation window the
        // client would snap it between positions and it would visibly stutter
        // alongside a smoothly-walking player. Two ticks is enough to bridge
        // one move without the text lagging behind its owner.
        tag.putInt("teleport_duration", 2);
        // load() is whole-entity deserialisation, not a partial update: it reads
        // the core fields too, and anything absent from the tag is reset to its
        // default. A tag carrying only display settings therefore teleports the
        // entity to 0,0,0 and drops its tags. Both are restored afterwards
        // rather than serialised in, so this stays a "change the text" call.
        double x = display.getX();
        double y = display.getY();
        double z = display.getZ();

        display.load(TagValueInput.create(
                ProblemReporter.DISCARDING, level.registryAccess(), tag));

        display.snapTo(x, y, z, 0.0F, 0.0F);
        display.addTag(TAG);
    }

    /**
     * True when a rendered template would show nothing — empty, whitespace, or
     * nothing but colour codes.
     *
     * <p>The last case is the one that matters. A template like
     * {@code " &6{title}"} is exactly right for a character who has a title and
     * leaves a dangling {@code &6} for one who does not, because {@code trim()}
     * removes the space and has no opinion about the code still attached to it.
     * Harmless to look at and wrong in principle: a line that renders to pure
     * formatting is not a line.</p>
     */
    private static boolean rendersToNothing(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()
                    && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(text.charAt(i + 1)) >= 0) {
                i++;
                continue;
            }
            if (!Character.isWhitespace(c)) return false;
        }
        return true;
    }

    /**
     * Fills one of the two templates. Every placeholder is always substituted,
     * including the ones that resolve to nothing: an owner who writes
     * {@code {title}} into the format on a pack that defines no titles should
     * get an empty gap, not the literal text "{title}".
     *
     * <p>And if what comes back is only punctuation-free formatting, it comes
     * back as nothing at all — see {@link #rendersToNothing}. That is what lets
     * the shipped suffix carry {@code {title}} without every untitled character
     * paying for it.</p>
     */
    private static String render(String key, ServerPlayer player) {
        String template = Lang.get(key);
        if (template.isBlank()) return "";
        PlayerCharacter pc = CharacterService.data(player);
        String filled = Lang.fmt(key,
                "name", player.getName().getString(),
                "race", CharacterService.race(player).map(Race::name)
                        .orElseGet(() -> Lang.get("msg.stats.undecided")),
                "class", CharacterService.mainClass(player).map(CharClass::name)
                        .orElseGet(() -> Lang.get("msg.stats.citizen")),
                "sub_class", CharacterService.subClass(player).map(CharClass::name).orElse(""),
                "level", CharacterService.level(player),
                "karma", CharacterService.karmaName(pc.karma()),
                "title", CharacterService.classTitle(player),
                "epithet", CharacterService.karmaEpithet(pc.karma()));
        return rendersToNothing(filled) ? "" : filled;
    }

    private Nameplate() {}
}
