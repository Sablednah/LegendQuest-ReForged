package com.sablednah.legendquest.yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.sablednah.legendquest.LegendQuest;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The YAML front door: serves {@code config/legendquest/**\/*.yml} as a
 * datapack. Admins edit YAML; the registry loader sees JSON.
 *
 * <p><b>{@code /reload} re-opens this pack but does not apply the edit.</b>
 * The conversion below runs again and logs the new file count, which reads
 * exactly like success — but race/class/skill/feat are datapack registries,
 * frozen at world load, and {@code MinecraftServer.reloadResources} rebuilds
 * recipes, advancements, functions and tags without ever re-running the
 * registry loader. Content changes land on RESTART. See
 * {@code LQServerEvents.onDatapackSync}, which tells the op so at the moment
 * they reload.</p>
 *
 * <p>Conversion happens eagerly when the pack opens. A file that is not valid
 * YAML is skipped with a loud log line — it never reaches the registry
 * loader, whose own failure mode (vanilla behaviour) would stop the world
 * loading. Note this protects against YAML typos only; a file that parses
 * but breaks the schema still hard-fails the load, by design.</p>
 */
public final class YamlConfigPack implements PackResources {

    public static final String PACK_ID = "legendquest_yaml";

    /** config subfolder → registry path segment. */
    private static final Map<String, String> FOLDERS = Map.of(
            "races", "race",
            "classes", "class",
            "skills", "skill",
            "feats", "feat");

    private static final PackLocationInfo LOCATION = new PackLocationInfo(
            PACK_ID,
            Component.literal("LegendQuest YAML configs"),
            PackSource.BUILT_IN,
            Optional.empty());

    private final Map<Identifier, byte[]> resources = new HashMap<>();

    private YamlConfigPack() {
        Path root = FMLPaths.CONFIGDIR.get().resolve(LegendQuest.MODID);
        ensureScaffold(root);
        for (var entry : FOLDERS.entrySet()) {
            Path dir = root.resolve(entry.getKey());
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")
                                || f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yaml"))
                        .forEach(file -> convert(entry.getValue(), file));
            } catch (IOException e) {
                LegendQuest.LOGGER.error("Could not scan {}", dir, e);
            }
        }
        if (!resources.isEmpty()) {
            LegendQuest.LOGGER.info("YAML front door: serving {} definition(s) from {}",
                    resources.size(), root);
        }
    }

    private void convert(String registrySegment, Path file) {
        String base = file.getFileName().toString();
        base = base.substring(0, base.lastIndexOf('.')).toLowerCase(Locale.ROOT).replace(' ', '_');
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = YamlToJson.parse(reader);
            Identifier id = Identifier.fromNamespaceAndPath(LegendQuest.MODID,
                    LegendQuest.MODID + "/" + registrySegment + "/" + base + ".json");
            resources.put(id, json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Loud skip: a bad YAML file must not take the world down.
            LegendQuest.LOGGER.error("[YAML] {} is not valid YAML and was SKIPPED: {}",
                    file, e.getMessage());
        }
    }

    /**
     * The one thing this folder most needs to say, and the one thing it used to
     * say wrongly. Named as a remedy rather than a problem: an admin whose edit
     * "did nothing" wants the next action, not a diagnosis.
     */
    private static final String RESTART_ADVICE = """

            RESTART THE SERVER after editing. Races, classes, skills
            and feats are frozen registries -- like vanilla's own
            enchantments, they load once when the world starts, so
            /reload will NOT pick up a change made here.
            (messages.yml is different: text is not a registry, and
            /reload does apply it.)
            """;

    /** The sentence shipped before this was known to be false. */
    private static final String STALE_ADVICE = "overrides it. Run /reload after editing.\n";

    /**
     * Repair the advice in a README written before we knew better.
     *
     * <p>Every server that has ever run LegendQuest already has this file, and
     * it is only written when missing — so leaving it alone would mean the
     * correction reached new installs and nobody else, which is the wrong half
     * of the audience. One sentence is replaced rather than the whole file
     * rewritten: an admin who has added notes of their own keeps them.</p>
     */
    private static void correctStaleReloadAdvice(Path readme) throws IOException {
        String existing = Files.readString(readme);
        if (!existing.contains(STALE_ADVICE)) return;
        Files.writeString(readme, existing.replace(STALE_ADVICE, "overrides it.\n" + RESTART_ADVICE));
        LegendQuest.LOGGER.info("Corrected the out-of-date /reload advice in {}", readme);
    }

    private static void ensureScaffold(Path root) {
        try {
            for (String folder : FOLDERS.keySet()) {
                Files.createDirectories(root.resolve(folder));
            }
            Path readme = root.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        LegendQuest ReForged — YAML content folder
                        ==========================================
                        Drop race/class/skill definitions here as YAML:
                          races/<name>.yml    -> registry id legendquest:<name>
                          classes/<name>.yml  -> registry id legendquest:<name>
                          skills/<name>.yml   -> registry id legendquest:<name>

                        The same schema also works as JSON in any datapack at
                        data/<pack>/legendquest/{race,class,skill}/<name>.json.
                        A YAML file here with the same name as a built-in entry
                        overrides it.
                        """ + RESTART_ADVICE);
            } else {
                correctStaleReloadAdvice(readme);
            }
        } catch (IOException e) {
            LegendQuest.LOGGER.error("Could not create config scaffold under {}", root, e);
        }
    }

    // --- PackResources ---

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        if (type != PackType.SERVER_DATA) return null;
        byte[] bytes = resources.get(id);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String pathPrefix, ResourceOutput output) {
        if (type != PackType.SERVER_DATA || !LegendQuest.MODID.equals(namespace)) return;
        for (var entry : resources.entrySet()) {
            if (entry.getKey().getPath().startsWith(pathPrefix)) {
                output.accept(entry.getKey(), () -> new ByteArrayInputStream(entry.getValue()));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of(LegendQuest.MODID) : Set.of();
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> section) throws IOException {
        return null; // metadata is supplied to the Pack constructor directly
    }

    @Override
    public PackLocationInfo location() {
        return LOCATION;
    }

    @Override
    public void close() {}

    // --- Pack plumbing ---

    /** The Pack served to the repository; opens a fresh conversion each (re)load. */
    public static Pack makePack() {
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new YamlConfigPack();
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new YamlConfigPack();
            }
        };
        Pack.Metadata metadata = new Pack.Metadata(
                Component.literal("Races, classes and skills from config/legendquest/*.yml"),
                PackCompatibility.COMPATIBLE,
                FeatureFlagSet.of(),
                List.of(),
                true); // hidden from the pack-selection UI; always on
        // required=true keeps it enabled; TOP so YAML overrides built-in defaults.
        PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.TOP, false);
        return new Pack(LOCATION, supplier, metadata, selection);
    }
}
