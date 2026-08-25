package com.sablednah.legendquest.client;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sablednah.legendquest.network.CombatIndicatorPayload;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
    private static final float DRIFT_PX = 16.0F; // total upward drift, in GUI pixels

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
                payload.y() + RANDOM.nextDouble() * 0.2,
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
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;

        long now = System.currentTimeMillis();
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        // 26.1 dropped GameRenderer.getProjectionMatrix(fov) and put the
        // combined view-rotation-and-projection matrix on the Camera instead,
        // which is what vanilla's own projectPointToScreen now uses. Building
        // it by hand from the fov option is no longer possible, and no longer
        // necessary.
        //
        // Deliberately NOT switching to projectPointToScreen itself: it returns
        // projected coordinates with no way to tell that a point was behind the
        // camera, and the w <= 0.05 test below is what stops damage numbers
        // from things behind you being drawn mirrored across the screen.
        Matrix4f projView = camera.getViewRotationProjectionMatrix(new Matrix4f());

        GuiGraphicsExtractor g = event.getGuiGraphics();
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

            // 26.1 removed the GUI transform stack, so a crit's larger number
            // cannot be drawn by pushing a scaled pose around an ordinary text
            // call. Scale lives on the text renderer's parameters instead, and
            // opacity comes with it -- which is actually better than packing
            // alpha into the colour's high byte the way the pose version did.
            //
            // The catch is that the scale transforms the coordinate space, so
            // the anchor has to be divided by it: at 2x, screen x 400 is anchor
            // 200. Vanilla's DeathScreen does exactly this with its title.
            float scale = f.scale();
            ActiveTextCollector text = g.textRenderer();
            ActiveTextCollector.Parameters normal = text.defaultParameters();
            text.defaultParameters(normal.withScale(scale).withOpacity(alpha));
            MutableComponent line = Component.literal(f.text())
                    .withStyle(style -> style.withColor(f.color() & 0x00FFFFFF)
                            .withBold(scale > 1.0F));
            // TextAlignment.CENTER earns its keep here: the pose version had to
            // subtract half the measured string width by hand.
            text.accept(TextAlignment.CENTER, Math.round(sx / scale),
                    Math.round((sy - 4.0F) / scale), line);
            text.defaultParameters(normal);
        }
    }

    private CombatIndicators() {}
}
