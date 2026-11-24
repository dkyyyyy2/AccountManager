package accountmanager.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;

final class PlayerModelPreview {
    private static final float MODEL_SCALE = 0.0625F;
    private static final float FEET_OFFSET = -1.5078125F;

    private final ModelPlayer classicModel = new ModelPlayer(0.0F, false);
    private final ModelPlayer slimModel = new ModelPlayer(0.0F, true);

    private float defaultYaw = 20.0F;
    private float yaw = defaultYaw;
    private float pitch = 0.0F;
    private boolean dragging;
    private int lastMouseX;
    private int lastMouseY;

    PlayerModelPreview facingAway() {
        defaultYaw = 200.0F;
        yaw = defaultYaw;
        return this;
    }

    void reset() {
        yaw = defaultYaw;
        pitch = 0.0F;
        dragging = false;
    }

    boolean isDragging() {
        return dragging;
    }

    void startDrag(int mouseX, int mouseY) {
        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    void drag(int mouseX, int mouseY) {
        if (!dragging) {
            return;
        }
        yaw += (mouseX - lastMouseX) * 1.5F;
        pitch = Math.max(-45.0F, Math.min(45.0F, pitch + (mouseY - lastMouseY) * 1.0F));
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    void endDrag() {
        dragging = false;
    }

    void render(int centerX, int bottomY, int scale, ResourceLocation skin, ResourceLocation cape, boolean slim) {
        if (skin == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        ModelPlayer model = slim ? slimModel : classicModel;
        resetPose(model);

        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) centerX, (float) bottomY, 100.0F);
        GlStateManager.scale((float) (-scale), (float) scale, (float) scale);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);

        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        GlStateManager.translate(0.0F, FEET_OFFSET, 0.0F);

        mc.getTextureManager().bindTexture(skin);
        renderModel(model);

        if (cape != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0F, 0.0F, 0.125F);
            GlStateManager.rotate(6.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            mc.getTextureManager().bindTexture(cape);
            model.renderCape(MODEL_SCALE);
            GlStateManager.popMatrix();
        }

        GlStateManager.popMatrix();

        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableCull();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderModel(ModelPlayer model) {
        model.bipedHead.render(MODEL_SCALE);
        model.bipedHeadwear.render(MODEL_SCALE);
        model.bipedBody.render(MODEL_SCALE);
        model.bipedBodyWear.render(MODEL_SCALE);
        model.bipedRightArm.render(MODEL_SCALE);
        model.bipedRightArmwear.render(MODEL_SCALE);
        model.bipedLeftArm.render(MODEL_SCALE);
        model.bipedLeftArmwear.render(MODEL_SCALE);
        model.bipedRightLeg.render(MODEL_SCALE);
        model.bipedRightLegwear.render(MODEL_SCALE);
        model.bipedLeftLeg.render(MODEL_SCALE);
        model.bipedLeftLegwear.render(MODEL_SCALE);
    }

    private static void resetPose(ModelPlayer model) {
        model.isChild = false;
        model.isSneak = false;
        model.swingProgress = 0.0F;
        model.leftArmPose = ModelBiped.ArmPose.EMPTY;
        model.rightArmPose = ModelBiped.ArmPose.EMPTY;

        zero(model.bipedHead);
        zero(model.bipedHeadwear);
        zero(model.bipedBody);
        zero(model.bipedBodyWear);
        zero(model.bipedRightArm);
        zero(model.bipedRightArmwear);
        zero(model.bipedLeftArm);
        zero(model.bipedLeftArmwear);
        zero(model.bipedRightLeg);
        zero(model.bipedRightLegwear);
        zero(model.bipedLeftLeg);
        zero(model.bipedLeftLegwear);
    }

    private static void zero(ModelRenderer part) {
        part.rotateAngleX = 0.0F;
        part.rotateAngleY = 0.0F;
        part.rotateAngleZ = 0.0F;
        part.isHidden = false;
        part.showModel = true;
    }
}
