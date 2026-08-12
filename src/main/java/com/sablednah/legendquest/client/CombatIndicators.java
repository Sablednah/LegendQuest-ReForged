package com.sablednah.legendquest.client;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sablednah.legendquest.network.CombatIndicatorPayload;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Floating combat words — "Kapow!", "Miss!", "Clang!" — popped near the mob
 * when your swing lands (or doesn't). Sixties Batman, tabletop heart.
 *
 * <p>Rendering uses the MobHealth recipe: once per frame in the GUI pass,
 * rebuild the exact view matrix Minecraft uses, combine with the projection,
 * and project the (jittered) world position to screen pixels. Each word
 * drifts upward and fades out over its short life.</p>
 */
public final class CombatIndicators {

    private static final long LIFE_MS = 1300;
    private static final float DRIFT_PX = 26.0F; // total upward drift, in GUI pixels

    private static final Random RANDOM = new Random();

    private record Floater(double x, double y, double z, String text, int color,
            float scale, long born) {}

    private static final CopyOnWriteArrayList<Floater> ACTIVE = new CopyOnWriteArrayList<>();

    private static final String[] MISS_WORDS = {"Miss!", "Dodged!", "Evaded!", "Whiff!", "Parried!"};
    private static final String[] HIT_WORDS = {"Kapow!", "Wham!", "Thwack!", "Bam!", "Pow!", "Sock!", "Zok!"};
    private static final String[] CRIT_WORDS = {"CRITICAL!", "KA-POW!", "SMASH!", "KRUNCH!", "BOOM!"};
    private static final String[] FUMBLE_WORDS = {"Clang!", "Clunk!", "Fumble!", "Thud..."};

    private static final int[] MISS_COLORS = {0xFFC8C8C8, 0xFFAAB4C8, 0xFFE0E0E0};
    private static final int[] HIT_COLORS = {0xFFFFD23C, 0xFFFF8C28, 0xFFFF5A3C, 0xFFFFE96E};
    private static final int[] CRIT_COLORS = {0xFFFF3C28, 0xFFFFAA00, 0xFFFF64DC};
    private static final int[] FUMBLE_COLORS = {0xFF9696AA, 0xFF78B4DC};

    /** Called from the network handler (client thread). */
    public static void accept(CombatIndicatorPayload payload) {
        String[] words;
        int[] colors;
        float scale = 1.0F;
        switch (payload.kind()) {
            case CombatIndicatorPayload.MISS -> { words = MISS_WORDS; colors = MISS_COLORS; }
            case CombatIndicatorPayload.CRIT -> { words = CRIT_WORDS; colors = CRIT_COLORS; scale = 1.6F; }
            case CombatIndicatorPayload.FUMBLE -> { words = FUMBLE_WORDS; colors = FUMBLE_COLORS; }
            default -> { words = HIT_WORDS; colors = HIT_COLORS; }
        }
        ACTIVE.add(new Floater(
                payload.x() + (RANDOM.nextDouble() - 0.5) * 0.7,
                payload.y() + 0.1 + RANDOM.nextDouble() * 0.4,
                payload.z() + (RANDOM.nextDouble() - 0.5) * 0.7,
                words[RANDOM.nextInt(words.length)],
                colors[RANDOM.nextInt(colors.length)],
                scale,
                System.currentTimeMillis()));
        if (ACTIVE.size() > 40) ACTIVE.removeFirst(); // swarm fights stay sane
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        long now = System.currentTimeMillis();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        Quaternionf viewRotation = camera.rotation().conjugate(new Quaternionf());
        Matrix4f projView = mc.gameRenderer.getProjectionMatrix(mc.options.fov().get())
                .mul(new Matrix4f().rotation(viewRotation), new Matrix4f());

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();

        Iterator<Floater> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Floater f = it.next();
            float age = (now - f.born()) / (float) LIFE_MS;
            if (age >= 1.0F) {
                ACTIVE.remove(f); // CopyOnWriteArrayList: iterator can't remove
                continue;
            }

            Vector4f clip = new Vector4f((float) (f.x() - camPos.x),
                    (float) (f.y() - camPos.y), (float) (f.z() - camPos.z), 1.0F);
            projView.transform(clip);
            if (clip.w <= 0.05F) continue; // behind the camera
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (ndcX < -1.2F || ndcX > 1.2F || ndcY < -1.2F || ndcY > 1.2F) continue;

            float sx = (ndcX * 0.5F + 0.5F) * screenW;
            float sy = (1.0F - (ndcY * 0.5F + 0.5F)) * screenH;
            // Ease-out drift: fast pop, slowing as it rises and fades.
            float rise = 1.0F - (1.0F - age) * (1.0F - age);
            sy -= rise * DRIFT_PX;
            // Opaque for the first 40% of life, then fade to nothing.
            float alpha = age < 0.4F ? 1.0F : 1.0F - (age - 0.4F) / 0.6F;
            int a = Math.max(8, Math.round(alpha * 255));
            int color = (a << 24) | (f.color() & 0x00FFFFFF);

            g.pose().pushMatrix();
            g.pose().translate(sx, sy);
            g.pose().scale(f.scale(), f.scale());
            String text = f.scale() > 1.0F ? "§l" + f.text() : f.text();
            g.drawString(font, text, -font.width(text) / 2, -4, color);
            g.pose().popMatrix();
        }
    }

    private CombatIndicators() {}
}
