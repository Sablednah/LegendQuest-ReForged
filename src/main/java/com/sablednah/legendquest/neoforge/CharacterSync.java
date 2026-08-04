package com.sablednah.legendquest.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sablednah.legendquest.character.PlayerCharacter;
import com.sablednah.legendquest.core.SkillPhase;
import com.sablednah.legendquest.core.Stat;
import com.sablednah.legendquest.data.CharClass;
import com.sablednah.legendquest.data.Race;
import com.sablednah.legendquest.data.SkillGrant;
import com.sablednah.legendquest.network.CharacterSummaryPayload;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Builds and pushes the client character summary. Vanilla clients simply
 * never registered the channel ({@code optional()}), so sends to them are
 * dropped silently — everything important also exists as chat/action bar.
 */
public final class CharacterSync {

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, summarize(player));
    }

    private static CharacterSummaryPayload summarize(ServerPlayer player) {
        PlayerCharacter pc = CharacterService.data(player);
        var stats = CharacterService.effectiveStats(player);
        int[] statArray = new int[6];
        for (Stat stat : Stat.values()) statArray[stat.ordinal()] = stats.get(stat);

        long now = System.currentTimeMillis();
        int level = CharacterService.level(player);
        List<CharacterSummaryPayload.SkillEntry> skills = new ArrayList<>();
        SkillEngine.grants(player).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Identifier id = entry.getKey();
                    SkillGrant grant = entry.getValue();
                    var def = SkillEngine.definition(player, id);
                    if (def.isEmpty()) return;
                    long waitMs = SkillPhase.remainingMs(now, pc.lastUse(id), def.get().timing());
                    skills.add(new CharacterSummaryPayload.SkillEntry(
                            def.get().name(),
                            def.get().type().name(),
                            grant.level(),
                            grant.cost(),
                            SkillEngine.owns(player, id, grant),
                            waitMs <= 0 ? 0 : (int) (waitMs / 1000 + 1)));
                });

        return new CharacterSummaryPayload(
                CharacterService.race(player).map(Race::name).orElse("Undecided"),
                CharacterService.mainClass(player).map(CharClass::name).orElse("Citizen"),
                CharacterService.subClass(player).map(CharClass::name).orElse(""),
                level,
                CharacterService.karmaName(pc.karma()),
                (float) pc.mana(),
                (float) CharacterService.maxMana(player),
                statArray,
                pc.skillPointsSpent(),
                CharacterService.skillPointsTotal(player),
                skills);
    }

    private CharacterSync() {}
}
