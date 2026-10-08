package com.sablednah.legendquest.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Display;

/**
 * Vanilla's text display renderer, except that a nameplate is left out of a
 * shader pack's shadow pass: under Iris the plate otherwise casts a sun shadow
 * like any entity (Sable, via the Standards session, Complementary on 26.2).
 * Everything else, plates included in the ordinary pass, draws as vanilla.
 *
 * <p>Not by remapping Iris's shadow pipeline for text: that would change the
 * shadow of every sign and text display in the world, not just ours.</p>
 */
public class PlateRenderer extends DisplayRenderer.TextDisplayRenderer {

    public PlateRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(Display.TextDisplay entity, Frustum frustum, double x, double y, double z, float partialTicks) {
        if (ClientPlates.isPlate(entity.getId()) && IrisCompat.shadowPass()) {
            return false;
        }
        return super.shouldRender(entity, frustum, x, y, z, partialTicks);
    }
}
