package com.github.standobyte.jojo.client.ui.hud;

import static com.github.standobyte.jojo.client.ui.hud.BarsRenderer.BARS_WIDTH_PX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.standobyte.jojo.JojoMod;
import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.action.Action;
import com.github.standobyte.jojo.action.ActionConditionResult;
import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.client.ClientUtil;
import com.github.standobyte.jojo.client.InputHandler;
import com.github.standobyte.jojo.client.resources.CustomResources;
import com.github.standobyte.jojo.client.ui.hud.ActionsModeConfig.SelectedTargetIcon;
import com.github.standobyte.jojo.client.ui.screen.hamon.HamonScreen;
import com.github.standobyte.jojo.client.ui.screen.hamon.HamonStatsTabGui;
import com.github.standobyte.jojo.entity.stand.StandEntity;
import com.github.standobyte.jojo.init.ModNonStandPowers;
import com.github.standobyte.jojo.network.PacketManager;
import com.github.standobyte.jojo.network.packets.fromclient.ClClickActionPacket;
import com.github.standobyte.jojo.power.IPower;
import com.github.standobyte.jojo.power.IPower.ActionType;
import com.github.standobyte.jojo.power.IPower.PowerClassification;
import com.github.standobyte.jojo.power.nonstand.INonStandPower;
import com.github.standobyte.jojo.power.nonstand.type.HamonData;
import com.github.standobyte.jojo.power.nonstand.type.HamonData.Exercise;
import com.github.standobyte.jojo.power.stand.IStandManifestation;
import com.github.standobyte.jojo.power.stand.IStandPower;
import com.github.standobyte.jojo.power.stand.StandUtil;
import com.github.standobyte.jojo.util.Container;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.AttackIndicatorStatus;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ColorHelper;
import net.minecraft.util.HandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.KeybindTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.GameType;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ActionsOverlayGui extends AbstractGui {
    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("textures/gui/widgets.png");
    private static final ResourceLocation OVERLAY_LOCATION = new ResourceLocation(JojoMod.MOD_ID, "textures/gui/overlay.png");
    
    private static ActionsOverlayGui instance = null;
    private final Minecraft mc;
    private int tickCount;
    
    public final ActionsModeConfig<INonStandPower> nonStandUiMode = new ActionsModeConfig<>(PowerClassification.NON_STAND);
    public final ActionsModeConfig<IStandPower> standUiMode = new ActionsModeConfig<>(PowerClassification.STAND);
    @Nullable
    private ActionsModeConfig<?> currentMode = null;
    
    private final ElementTransparency modeSelectorTransparency = new ElementTransparency(40, 10);
    private final ElementTransparency energyBarTransparency = new ElementTransparency(40, 10);
    private final ElementTransparency staminaBarTransparency = new ElementTransparency(40, 10);
    private final ElementTransparency resolveBarTransparency = new ElementTransparency(40, 10);
    private final ImmutableMap<Exercise, ElementTransparency> exerciseBarsTransparency = Arrays.stream(Exercise.values())
            .collect(Maps.toImmutableEnumMap(ex -> ex, ex -> new ElementTransparency(40, 10)));
    
    private final BarsRenderer verticalBars = new VerticalBarsRenderer(this, 
            energyBarTransparency, staminaBarTransparency, resolveBarTransparency);
    private final BarsRenderer horizontalBars = new HorizontalBarsRenderer(this, 
            energyBarTransparency, staminaBarTransparency, resolveBarTransparency);
    private ElementTransparency[] tickingTransparencies = new ElementTransparency[] {
            modeSelectorTransparency,
            energyBarTransparency,
            staminaBarTransparency,
            resolveBarTransparency,
            exerciseBarsTransparency.get(Exercise.MINING),
            exerciseBarsTransparency.get(Exercise.RUNNING),
            exerciseBarsTransparency.get(Exercise.SWIMMING),
            exerciseBarsTransparency.get(Exercise.MEDITATION)
    };
    
    private boolean attackSelection;
    private boolean abilitySelection;
    
    private ActionsOverlayGui(Minecraft mc) {
        this.mc = mc;
    }

    public static void init(Minecraft mc) {
        if (instance == null) {
            instance = new ActionsOverlayGui(mc);
            MinecraftForge.EVENT_BUS.register(instance);
        }
    }
    
    public static ActionsOverlayGui getInstance() {
        return instance;
    }
    
    public void tick() {
        if (!mc.isPaused()) {
            for (ElementTransparency element : tickingTransparencies) {
                element.tick();
            }
        }
        
        INonStandPower power = nonStandUiMode.getPower();
        if (power != null) {
            if (power.getEnergy() < power.getMaxEnergy()) {
                energyBarTransparency.reset();
            }
        }
        IStandPower standPower = standUiMode.getPower();
        if (standPower != null) {
            if (standPower.getStamina() < standPower.getMaxStamina()) {
                staminaBarTransparency.reset();
            }
            if (standPower.getResolve() > 0) {
                resolveBarTransparency.reset();
            }
        }
        
        tickStandUnsummonCheck();
        
        tickCount++;
    }
    
    public void onHamonExerciseValueChanged(Exercise exercise) {
        exerciseBarsTransparency.get(exercise).reset();
    }
    
    public boolean isActive() {
        return currentMode != null;
    }
    
    public boolean noActionSelected(ActionType actionType) {
        return !isActive() || currentMode.getSelectedSlot(actionType) < 0;
    }
    
    @Nullable
    public IPower<?, ?> getCurrentPower() {
        if (currentMode == null) {
            return null;
        }
        return currentMode.getPower();
    }
    
    @Nullable
    public PowerClassification getCurrentMode() {
        if (currentMode == null) {
            return null;
        }
        return currentMode.powerClassification;
    }
    
    
    
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void render(RenderGameOverlayEvent.Pre event) {
        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR || mc.options.hideGui) {
            return;
        }
        
        MatrixStack matrixStack = event.getMatrixStack();
        float partialTick = event.getPartialTicks();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        PositionConfig hotbarsPosConfig = JojoModConfig.CLIENT.hotbarsPosition.get();
        PositionConfig barsPosConfig = JojoModConfig.CLIENT.barsPosition.get();
        boolean showModeSelector = false;
        updateWarnings(currentMode);
        updateElementPositions(barsPosConfig, hotbarsPosConfig, screenWidth, screenHeight);

        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            RenderSystem.enableRescaleNormal();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            
            renderBars(matrixStack, barsPosition, barsRenderer, partialTick);
            
            if (showModeSelector) {
                renderModeSelector(matrixStack, modeSelectorPosition, partialTick);
            }
            
            if (nonStandUiMode != null && nonStandUiMode.getPower() != null) {
                nonStandUiMode.getPower().getTypeSpecificData(ModNonStandPowers.HAMON.get()).ifPresent(hamon -> {
                    renderHamonExerciseBars(matrixStack, hamonExerciseBarsPosition, hamon, partialTick);
                });
            }
            
            ActionsModeConfig<?> modeIcon = currentMode != null ? currentMode : standUiMode;
            float standAlpha = 1.0F;
            if (modeIcon == standUiMode && modeIcon != null) {
                IStandPower stand = standUiMode.getPower();
                if (stand != null && stand.isActive() && stand.getStandManifestation() instanceof StandEntity) {
                    standAlpha = ((StandEntity) stand.getStandManifestation()).getAlpha(partialTick);
                }
            }
            renderPowerIcon(matrixStack, hotbarsPosition, modeIcon, standAlpha);
            
            RenderSystem.disableRescaleNormal();
            RenderSystem.disableBlend();
        }
        else if (event.getType() == RenderGameOverlayEvent.ElementType.TEXT) {
            RenderSystem.enableRescaleNormal();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

            if (showModeSelector) {
                drawModeSelectorNames(matrixStack, modeSelectorPosition, partialTick);
            }
            
            drawBarsText(matrixStack, barsRenderer, partialTick);
            
            RenderSystem.disableRescaleNormal();
            RenderSystem.disableBlend();
        }
        
        if (currentMode != null) {
            if (currentMode.getPower() == null || !currentMode.getPower().hasPower()) {
                JojoMod.getLogger().warn("Failed rendering {} HUD", currentMode.powerClassification);
                currentMode = null;
                return;
            }
            
            ActionTarget target = ActionTarget.fromRayTraceResult(InputHandler.getInstance().mouseTarget);
            
            if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
                RenderSystem.enableRescaleNormal();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                
                renderActionsHotbar(matrixStack, hotbarsPosition, ActionType.ATTACK, currentMode, target, partialTick);
                renderActionsHotbar(matrixStack, hotbarsPosition, ActionType.ABILITY, currentMode, target, partialTick);
                
                renderWarningIcons(matrixStack, warningsPosition, warningLines);
                
                renderLeapIcon(matrixStack, currentMode, screenWidth, screenHeight);
                if (currentMode == standUiMode) {
                    renderStandComboIcon(matrixStack, standUiMode.getPower(), screenWidth, screenHeight);
                }
                
                RenderSystem.disableRescaleNormal();
                RenderSystem.disableBlend();
            }
            else if (event.getType() == RenderGameOverlayEvent.ElementType.TEXT) {
                drawPowerName(matrixStack, hotbarsPosition, currentMode);
                
                drawHotbarText(matrixStack, hotbarsPosition, ActionType.ATTACK, currentMode, target);
                drawHotbarText(matrixStack, hotbarsPosition, ActionType.ABILITY, currentMode, target);
                
                drawWarningText(matrixStack, warningsPosition, warningLines);
            }
        }
    }



    private static final int HOTBARS_ELEMENT_HEIGHT_PX = 86;
    
    private final ElementPosition barsPosition = new ElementPosition();
    private BarsRenderer barsRenderer;
    private final ElementPosition hotbarsPosition = new ElementPosition();
    private final ElementPosition warningsPosition = new ElementPosition();
    private List<ITextComponent> warningLines = new ArrayList<>();
    private final ElementPosition standStrengthPosition = new ElementPosition();
    private final ElementPosition modeSelectorPosition = new ElementPosition();
    private final ElementPosition hamonExerciseBarsPosition = new ElementPosition();
    
    private void updateElementPositions(PositionConfig barsConfig, PositionConfig hotbarsConfig, int screenWidth, int screenHeight) {
        int halfWidth = screenWidth / 2;
        int halfHeight = screenHeight / 2;
        barsRenderer = barsConfig.barsOrientation == BarsOrientation.HORIZONTAL ? horizontalBars : verticalBars;
        barsPosition.x = barsConfig.getXPos(screenWidth);
        barsPosition.y = barsConfig.getYPos(screenHeight, 
                barsConfig.barsOrientation == BarsOrientation.HORIZONTAL ? BARS_WIDTH_PX : VerticalBarsRenderer.BAR_HEIGHT);
        if (isActive() && (hotbarsConfig == PositionConfig.TOP_LEFT && barsConfig == PositionConfig.LEFT
                || hotbarsConfig == PositionConfig.TOP_RIGHT && barsConfig == PositionConfig.RIGHT)) {
            barsPosition.y = Math.max(hotbarsPosition.y + HOTBARS_ELEMENT_HEIGHT_PX + INDENT + VerticalBarsRenderer.ICON_HEIGHT, barsPosition.y);
        }
        barsPosition.alignment = barsConfig.aligment;
        
        hotbarsPosition.x = hotbarsConfig.getXPos(screenWidth);
        hotbarsPosition.y = hotbarsConfig.getYPos(screenHeight, 
                HOTBARS_ELEMENT_HEIGHT_PX);
        if (isActive()) {
            if (barsConfig == hotbarsConfig) {
                switch (barsConfig.barsOrientation) {
                case HORIZONTAL:
                    hotbarsPosition.y += BARS_WIDTH_PX + INDENT;
                    break;
                case VERTICAL:
                    hotbarsPosition.x += hotbarsConfig.aligment == Alignment.RIGHT ? -(BARS_WIDTH_PX + INDENT) : BARS_WIDTH_PX + INDENT;
                    break;
                }
            }
            else if (barsConfig == PositionConfig.TOP_LEFT && hotbarsConfig == PositionConfig.LEFT
                    || barsConfig == PositionConfig.TOP_RIGHT && hotbarsConfig == PositionConfig.RIGHT) {
                hotbarsPosition.y = Math.max(barsPosition.y + BARS_WIDTH_PX + INDENT, hotbarsPosition.y);
            }
        }
        hotbarsPosition.alignment = hotbarsConfig.aligment;
        
        warningsPosition.x = hotbarsPosition.x;
        warningsPosition.y = hotbarsPosition.y + 92;
        warningsPosition.alignment = hotbarsPosition.alignment;
        if (hotbarsConfig == PositionConfig.TOP_LEFT && barsConfig == PositionConfig.LEFT) {
            warningsPosition.x += 32;
        }
        else if (hotbarsConfig == PositionConfig.TOP_RIGHT && barsConfig == PositionConfig.RIGHT) {
            warningsPosition.x -= 22;
        }
        if (warningsPosition.alignment == Alignment.RIGHT) {
            warningsPosition.x -= 16;
        }
        
        standStrengthPosition.x = hotbarsPosition.x;
        standStrengthPosition.y = hotbarsPosition.y + 92 + warningLines.size() * 16;
        standStrengthPosition.alignment = hotbarsPosition.alignment;
        if (hotbarsConfig == PositionConfig.TOP_LEFT && barsConfig == PositionConfig.LEFT) {
            standStrengthPosition.x += 32;
        }
        else if (hotbarsConfig == PositionConfig.TOP_RIGHT && barsConfig == PositionConfig.RIGHT) {
            standStrengthPosition.x -= 22;
        }
        
        modeSelectorPosition.x = hotbarsConfig.aligment == Alignment.LEFT ? halfWidth + 9 : halfWidth - 29;
        modeSelectorPosition.y = halfHeight - 31;
        modeSelectorPosition.alignment = hotbarsConfig.aligment;

        hamonExerciseBarsPosition.x = 10;
        hamonExerciseBarsPosition.y = screenHeight - 5;
        for (ElementTransparency bar : exerciseBarsTransparency.values()) {
            if (bar.shouldRender()) {
                hamonExerciseBarsPosition.y -= 9;
            }
        }
        hamonExerciseBarsPosition.alignment = Alignment.LEFT;
    }
    
    private void updateWarnings(@Nullable ActionsModeConfig<?> mode) {
        warningLines.clear();
        if (mode != null) {
            appendWarnings(warningLines, mode, ActionType.ATTACK);
            appendWarnings(warningLines, mode, ActionType.ABILITY);
        }
    }
    
    private <P extends IPower<P, ?>> void appendWarnings(List<ITextComponent> list, ActionsModeConfig<P> powerMode, ActionType actionType) {
        Action<P> action = powerMode.getSelectedAction(actionType, mc.player.isShiftKeyDown());
        if (action != null) {
            action.appendWarnings(list, powerMode.getPower(), mc.player);
        }
    }
    
    

    private void renderBars(MatrixStack matrixStack, ElementPosition pos, BarsRenderer renderer, float partialTick) {
        int x = pos.x;
        int y = pos.y;
        if (renderer != null) {
            mc.getTextureManager().bind(OVERLAY_LOCATION);
            renderer.render(matrixStack, x, y, pos.alignment, 
                    currentMode != null ? currentMode.powerClassification : null, nonStandUiMode.getPower(), standUiMode.getPower(), 
                    tickCount, partialTick);
        }
    }
    
    private void drawBarsText(MatrixStack matrixStack, BarsRenderer renderer, float partialTick) {
        if (renderer != null) {
            renderer.drawTextAfterRender(matrixStack, getCurrentMode(), nonStandUiMode.getPower(), 
                    standUiMode.getPower(), tickCount, partialTick, mc.font, this);
        }
    }

    
    
    private <P extends IPower<P, ?>> void renderActionsHotbar(MatrixStack matrixStack, 
            ElementPosition position, ActionType actionType, ActionsModeConfig<P> mode, ActionTarget target, float partialTick) {
        P power = mode.getPower();
        if (power.hasPower()) {
            List<Action<P>> actions = power.getActions(actionType);
            if (actions.size() > 0) {
                mc.getTextureManager().bind(WIDGETS_LOCATION);
                int x = position.x;
                int y = position.y + 16 + 2 * 2 + mc.font.lineHeight;
                if (actionType == ActionType.ABILITY) {
                    y += getHotbarsYDiff();
                }
                if (position.alignment == Alignment.RIGHT) {
                    x -= actions.size() * 20 + 2;
                }
                int selected = mode.getSelectedSlot(actionType);
                boolean shift = mc.player.isShiftKeyDown();
                float alpha = selected < 0 ? 0.5F : 1.0F;
                // hotbar
                renderHotbar(matrixStack, x, y, actions.size(), alpha);
                // action icons
                x += 3;
                y += 3;
                for (int i = 0; i < actions.size(); i++) {
                    Action<P> action = power.getAction(actionType, i, shift);
                    renderActionIcon(matrixStack, actionType, mode, action, target, x + 20 * i, y, partialTick, i == selected, alpha);
                }
                // target type icon
                if (selected >= 0 && selected < actions.size()) {
                    SelectedTargetIcon icon = mode.getTargetIcon(actionType);
                    if (icon != null) {
                        int[] tex = icon.getIconTex();
                        if (tex != null) {
                            mc.getTextureManager().bind(OVERLAY_LOCATION);
                            RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha);
                            int texX = tex[0];
                            int texY = tex[1];
                            int iconX = x + 20 * selected + 10;
                            int iconY = y - 4;
                            matrixStack.pushPose();
                            matrixStack.scale(0.5F, 0.5F, 1F);
                            matrixStack.translate(iconX, iconY, 0);
                            blit(matrixStack, iconX, iconY, texX, texY, 32, 32);
                            matrixStack.popPose();
                        }
                    }
                }
                // highlight when hotbar key is pressed
                boolean highlightSelection = actionType == ActionType.ATTACK ? attackSelection : abilitySelection;
                if (highlightSelection) {
                    int highlightAlpha = (int) (ClientUtil.getHighlightAlpha(tickCount + partialTick, 40F, 40F, 0.25F, 0.5F) * 255F);
                    RenderSystem.disableDepthTest();
                    RenderSystem.disableTexture();
                    BufferBuilder bufferBuilder = Tessellator.getInstance().getBuilder();
                    if (selected >= 0) {
                        fillRect(bufferBuilder, x + selected * 20 - 4, y - 4, 24, 23, 255, 255, 255, highlightAlpha);
                    }
                    else {
                        fillRect(bufferBuilder, x - 3, y - 3, actions.size() * 20 + 2, 22, 255, 255, 255, highlightAlpha);
                    }
                    RenderSystem.enableTexture();
                    RenderSystem.enableDepthTest();
                }
                
                RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }
    
    private int getHotbarsYDiff() {
        return 2 * 2 + 22 + mc.font.lineHeight;
    }
    
    private void renderHotbar(MatrixStack matrixStack, int x, int y, int slots, float alpha) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha);
        int hotbarLength = 20 * slots + 1;
        blit(matrixStack, x, y, 0, 0, hotbarLength, 22);
        blit(matrixStack, x + hotbarLength, y, 181, 0, 1, 22);
    }
    
    private <P extends IPower<P, ?>> void renderActionIcon(MatrixStack matrixStack, ActionType actionType, ActionsModeConfig<P> mode, 
            Action<P> action, ActionTarget target, int x, int y, float partialTick, boolean isSelected, float hotbarAlpha) {
        P power = mode.getPower();
        
        if (action != null) {
            TextureAtlasSprite textureAtlasSprite = CustomResources.getActionSprites().getSprite(action, power);
            mc.getTextureManager().bind(textureAtlasSprite.atlas().location());
            
            ActionConditionResult result = actionAvailability(action, mode, actionType, target, isSelected);
            if (!result.isPositive()) {
            	if (!result.isHighlighted()) {
            		RenderSystem.color4f(0.2F, 0.2F, 0.2F, 0.5F * hotbarAlpha);
            	}
            	else {
            		RenderSystem.color4f(0.75F, 0.75F, 0.75F, 0.75F * hotbarAlpha);
            	}
                blit(matrixStack, x, y, 0, 16, 16, textureAtlasSprite);
                // cooldown
                float ratio = power.getCooldownRatio(action, partialTick);
                if (ratio > 0) {
                    RenderSystem.disableDepthTest();
                    RenderSystem.disableTexture();
                    BufferBuilder bufferBuilder = Tessellator.getInstance().getBuilder();
                    fillRect(bufferBuilder, x, y + 16.0F * (1.0F - ratio), 16, 16.0F * ratio, 255, 255, 255, 127);
                    RenderSystem.enableTexture();
                    RenderSystem.enableDepthTest();
                }
            } else {
                RenderSystem.color4f(1.0F, 1.0F, 1.0F, hotbarAlpha);
                blit(matrixStack, x, y, 0, 16, 16, textureAtlasSprite);
            }
            // learning bar
            float learningProgress = power.getLearningProgressRatio(action);
            if (learningProgress >= 0 && learningProgress < 1) {
                RenderSystem.disableDepthTest();
                RenderSystem.disableTexture();
                RenderSystem.disableAlphaTest();
                RenderSystem.disableBlend();
                int barX = x + 2;
                int barY = y + 13;
                fillRect(Tessellator.getInstance().getBuilder(), barX, barY, 13, 2, 0, 0, 0, 255);
                fillRect(Tessellator.getInstance().getBuilder(), barX, barY, Math.round(learningProgress * 13.0F), 1, 0, 255, 0, 255);
                RenderSystem.enableBlend();
                RenderSystem.enableAlphaTest();
                RenderSystem.enableTexture();
                RenderSystem.enableDepthTest();
            }
            // selected slot
            if (isSelected) {
                mc.getTextureManager().bind(WIDGETS_LOCATION);
                boolean greenSelection = action.greenSelection(power, result);
                if (greenSelection) {
                    RenderSystem.color4f(0.0F, 1.0F, 0.0F, 1.0F);
                }
                else {
                    RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                }
                blit(matrixStack, x - 4, y - 4, 0, 22, 24, 22);
            }
        }
    }
    
    private <P extends IPower<P, ?>> ActionConditionResult actionAvailability(Action<P> action, ActionsModeConfig<P> mode, 
            ActionType hotbar, ActionTarget mouseTarget, boolean isSelected) {
        P power = mode.getPower();
        Container<ActionTarget> targetContainer = new Container<>(mouseTarget);
        if (isSelected) {
        	ActionConditionResult targetCheck = power.checkTarget(action, targetContainer);
            mode.getTargetIcon(hotbar).update(action.getTargetRequirement(), targetCheck.isPositive());
            if (!targetCheck.isPositive()) {
                return targetCheck;
            }
        }
    	return power.checkRequirements(action, targetContainer, !isSelected);
    }
    
    private void fillRect(BufferBuilder bufferBuilder, int x, double y, int width, double height, int red, int green, int blue, int alpha) {
        bufferBuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(x + 0 , y + 0, 0.0D).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(x + 0 , y + height, 0.0D).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(x + width , y + height, 0.0D).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(x + width , y + 0, 0.0D).color(red, green, blue, alpha).endVertex();
        Tessellator.getInstance().end();
    }
    
    public void setHotbarButtonsDows(boolean attack, boolean ability) {
        this.attackSelection = attack;
        this.abilitySelection = ability;
    }
    
    private <P extends IPower<P, ?>> void drawHotbarText(MatrixStack matrixStack, ElementPosition position, ActionType actionType, @Nonnull ActionsModeConfig<P> mode, ActionTarget target) {
        P power = mode.getPower();
        int x = position.x;
        int y = position.y + 16 + 3;
        if (actionType == ActionType.ABILITY) {
            y += getHotbarsYDiff();
        }
        Action<P> action = mode.getSelectedAction(actionType, mc.player.isShiftKeyDown());
        if (action != null) {
            // action name
            String translationKey = action.getTranslationKey(power, target);
            ITextComponent actionName = action.getTranslatedName(power, translationKey);
            if (action.getHoldDurationMax(power) > 0) {
                actionName = new TranslationTextComponent("jojo.overlay.hold", actionName);
            }
            if (action.hasShiftVariation()) {
                Action<P> shiftVar = action.getShiftVariationIfPresent().getVisibleAction(power);
                if (shiftVar != null) {
                    actionName = new TranslationTextComponent("jojo.overlay.shift", actionName, 
                            new KeybindTextComponent(mc.options.keyShift.getName()), 
                            shiftVar.getNameShortened(power, shiftVar.getTranslationKey(power, target)));
                }
            }
            actionName = new TranslationTextComponent(
                    actionType == ActionType.ATTACK ? "jojo.overlay.action.attack" : "jojo.overlay.action.ability", actionName);
            
            int width = mc.font.width(actionName);
            RenderSystem.pushMatrix();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            
            drawBackdrop(matrixStack, x, y, width, position.alignment, null, 1);
            drawString(matrixStack, mc.font, actionName, x, y, position.alignment, currentMode.getPower().getType().getColor());
            
            RenderSystem.disableBlend();
            RenderSystem.popMatrix();
        }

        y += 10;
        if (position.alignment == Alignment.RIGHT) {
            x -= 20 * power.getActions(actionType).size();
        }
        Action<P> heldAction = power.getHeldAction();
        int slot = -1;
        if (heldAction != null) {
            slot = power.getActions(actionType).indexOf(
                    heldAction.isShiftVariation() ? heldAction.getBaseVariation() : heldAction);
            if (slot > -1) {
                drawHoldDuration(matrixStack, x, y, slot, position.alignment, heldAction, power, power.getHeldActionTicks());
            }
        }
        else if (action != null) {
            slot = mode.getSelectedSlot(actionType);
            if (slot > -1) {
                drawHoldDuration(matrixStack, x, y, slot, position.alignment, action, power, 0);
            }
        }
    }
    
    private <P extends IPower<P, ?>> void drawHoldDuration(MatrixStack matrixStack, int x, int y, 
            int slot, Alignment hotbarAlignment, Action<P> action, P power, int ticksHeld) {
        int ticksToFire = action.getHoldDurationToFire(power);
        if (ticksToFire > 0) {
            x += slot * 20 + 20;
            if (hotbarAlignment == Alignment.RIGHT) {
                x -= 2;
            }
            ticksToFire = Math.max(ticksToFire - ticksHeld, 0);
            int seconds = ticksToFire == 0 ? 0 : (ticksToFire - 1) / 20 + 1;
            int color = ticksToFire == 0 ? 0x00FF00 : ticksHeld == 0 ? 0x808080 : 0xFFFFFF;
            ClientUtil.drawRightAlignedString(matrixStack, mc.font, String.valueOf(seconds), x, y + 12, color);
        }
    }
    
    
    
    public boolean isSelectedActionHeld(ActionType actionType) {
        return getSelectedActionHoldDuration(actionType, currentMode) > 0;
    }
    
    private <P extends IPower<P, ?>> int getSelectedActionHoldDuration(ActionType actionType, @Nonnull ActionsModeConfig<P> mode) {
        Action<P> action = mode.getSelectedAction(actionType, mc.player.isShiftKeyDown());
        if (action != null) {
            return action.getHoldDurationMax(mode.getPower());
        }
        return 0;
    }
    
    
    
    private void renderPowerIcon(MatrixStack matrixStack, ElementPosition position, @Nullable ActionsModeConfig<?> mode, float alpha) {
        int x = position.x;
        if (position.alignment == Alignment.RIGHT) {
            x -= 16;
        }
        int y = position.y;
        if (mode != null && mode.getPower() != null && mode.getPower().isActive()) {
            if (alpha < 1.0F) {
                RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha);
            }
            
            mc.getTextureManager().bind(mode.getPower().getType().getIconTexture());
            blit(matrixStack, x, y, 0, 0, 16, 16, 16, 16);
            
            if (alpha < 1.0F) {
                RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }
    
    private void drawPowerName(MatrixStack matrixStack, ElementPosition position, @Nonnull ActionsModeConfig<?> mode) {
        int x = position.x + (position.alignment == Alignment.RIGHT ? -19 : 19);
        int y = position.y + (16 - mc.font.lineHeight) / 2;
        IPower<?, ?> power = currentMode.getPower();
        ITextComponent name = new TranslationTextComponent(power.getType().getTranslationKey());
        drawBackdrop(matrixStack, x, y, mc.font.width(name), position.alignment, null, 1.0F);
        drawString(matrixStack, mc.font, name, x, y, position.alignment, power.getType().getColor());
    }



    private void renderWarningIcons(MatrixStack matrixStack, ElementPosition position, List<ITextComponent> warningLines) {
        mc.getTextureManager().bind(OVERLAY_LOCATION);
        int x = position.x;
        int y = position.y - 4;
        for (int line = 0; line < warningLines.size(); line++) {
            blit(matrixStack, x, y, 132, 240, 16, 16);
            y += 16;
        }
    }
    
    private void drawWarningText(MatrixStack matrixStack, ElementPosition position, List<ITextComponent> warningLines) {
        int x = position.x;
        int y = position.y;
        switch (position.alignment) {
        case LEFT:
            x += 18;
            break;
        case RIGHT:
            x -= 2;
            break;
        }
        for (ITextComponent line : warningLines) {
            drawBackdrop(matrixStack, x, y, mc.font.width(line), position.alignment, null, 1.0F);
            drawString(matrixStack, mc.font, line, x, y, position.alignment, 0xFFFFFF);
            y += 16;
        }
    }
    
    public void drawStandRemoteRange(MatrixStack matrixStack, float distance, float damageFactor) {
        int x = standStrengthPosition.x;
        int y = standStrengthPosition.y;
        Alignment alignment = standStrengthPosition.alignment;
        ITextComponent distanceString = new StringTextComponent(String.format("%.2f m", distance));
        drawBackdrop(matrixStack, x, y, mc.font.width(distanceString), alignment, null, 0);
        drawString(matrixStack, mc.font, distanceString, x, y, alignment, 0xFFFFFF);
        if (damageFactor < 1) {
            y += 12;
            ITextComponent strength = new TranslationTextComponent("jojo.overlay.stand_strength", String.format("%.2f%%", damageFactor * 100F));
            drawBackdrop(matrixStack, x, y, mc.font.width(strength), alignment, null, 0);
            drawString(matrixStack, mc.font, strength, x, y, alignment, 0xFF4040);
        }
    }
    
    
    
    private final List<ActionsModeConfig<?>> modes = Collections.unmodifiableList(Arrays.asList(
            null,
            nonStandUiMode,
            standUiMode
            ));
    private void renderModeSelector(MatrixStack matrixStack, ElementPosition position, float partialTick) {
        if (modeSelectorTransparency.shouldRender()) {
            mc.getTextureManager().bind(WIDGETS_LOCATION);
            int x = position.x;
            int y = position.y;
            matrixStack.pushPose();
            matrixStack.translate(x, y, 0);
            matrixStack.mulPose(Vector3f.ZP.rotationDegrees(90));
            matrixStack.translate(-x, -y - 22, 0);
            renderHotbar(matrixStack, x, y, modes.size(), modeSelectorTransparency.getAlpha(partialTick));
            int selectedMode = modes.indexOf(currentMode);
            if (selectedMode > -1) {
                blit(matrixStack, x + selectedMode * 20 - 1, y - 1, 0, 22, 24, 24);
            }
            matrixStack.popPose();
            renderModeSelectorIcons(matrixStack, x + 3, y + 3, partialTick);
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
    
    
    
    private void renderModeSelectorIcons(MatrixStack matrixStack, int x, int y, float partialTick) {
        for (ActionsModeConfig<?> mode : modes) {
            if (mode != null) {
                IPower<?, ?> power = mode.getPower();
                if (power.hasPower()) {
                    mc.getTextureManager().bind(power.getType().getIconTexture());
                    blit(matrixStack, x, y, 0, 0, 16, 16, 16, 16);
                }
            }
            y += 20;
        }
    }
    
    private void drawModeSelectorNames(MatrixStack matrixStack, ElementPosition position, float partialTick) {
        if (modeSelectorTransparency.shouldRender()) {
            int x = position.x + (position.alignment == Alignment.LEFT ? 26 : -4);
            int y = position.y + (22 - mc.font.lineHeight) / 2;
            for (ActionsModeConfig<?> mode : modes) {
                ITextComponent name = getModeNameForSelector(mode);
                if (name != null) {
                    int color = getModeColor(mode);
                    drawBackdrop(matrixStack, x, y, mc.font.width(name), position.alignment, modeSelectorTransparency, partialTick);
                    drawString(matrixStack, mc.font, name, x, y, position.alignment, modeSelectorTransparency.makeTextColorTranclucent(color, partialTick));
                }
                y += 22;
            }
        }
    }
    
    @Nullable
    private ITextComponent getModeNameForSelector(ActionsModeConfig<?> mode) {
        ITextComponent name;
        if (mode == null) {
            if (currentMode == null) {
                return null;
            }
            name = new TranslationTextComponent("jojo.overlay.mode_deselect");
        }
        else {
            IPower<?, ?> power = mode.getPower();
            if (!power.hasPower()) {
                return null;
            }
            name = new TranslationTextComponent(power.getType().getTranslationKey());
        }
        ITextComponent keyName = getKeyName(mode);
        if (keyName != null) {
            name = new TranslationTextComponent("jojo.overlay.mode_key", keyName, name);
        }
        return name;
    }

    private Map<ActionsModeConfig<?>, Supplier<KeyBinding>> modeKeys = ImmutableMap.of(
            nonStandUiMode, () -> InputHandler.getInstance().nonStandMode,
            standUiMode, () -> InputHandler.getInstance().standMode);
    @Nullable
    private ITextComponent getKeyName(ActionsModeConfig<?> mode) {
        if (mode == currentMode) {
            return null;
        }
        if (mode == null) {
            mode = currentMode;
        }
        Supplier<KeyBinding> keySupplier = modeKeys.get(mode);
        if (keySupplier == null || keySupplier.get() == null) {
            return null;
        }
        return new KeybindTextComponent(keySupplier.get().getName());
    }
    
    private int getModeColor(ActionsModeConfig<?> mode) {
        if (mode == null) {
            return 0xFFFFFF;
        }
        return mode.getPower().getType().getColor();
    }
    
    
    
    private void renderLeapIcon(MatrixStack matrixStack, @Nonnull ActionsModeConfig<?> mode, int screenWidth, int screenHeight) {
        IPower<?, ?> power = mode.getPower();
        if (power.isLeapUnlocked()) {
            mc.getTextureManager().bind(OVERLAY_LOCATION);
            boolean rightSide = mc.player.getMainArm() == HandSide.RIGHT;
            int iconX = rightSide ? screenWidth / 2 + 91 + 6 : screenWidth / 2 - 91 - 22;
            if (mc.options.attackIndicator == AttackIndicatorStatus.HOTBAR) {
                if (rightSide) {
                    iconX += 20;
                }
                else {
                    iconX -= 20;
                }
            }
            int iconY = screenHeight - 20;
            
            float iconFill = power.getLeapCooldownPeriod() != 0 ? 
                    1F - (float) power.getLeapCooldown() / (float) power.getLeapCooldownPeriod() : 1;
            boolean translucent = !InputHandler.getInstance().canPlayerLeap();
            
            renderFilledIcon(matrixStack, iconX, iconY, translucent, iconFill, 96, 238, 18, 18, 0xFFFFFF);
        }
    }
    
    private void renderStandComboIcon(MatrixStack matrixStack, IStandPower standPower, int screenWidth, int screenHeight) {
        if (standPower.isActive() && StandUtil.isComboUnlocked(standPower) && standPower.getType().usesStandComboMechanic()) {
            mc.getTextureManager().bind(OVERLAY_LOCATION);
            IStandManifestation stand = standPower.getStandManifestation();
            if (stand instanceof StandEntity) {
                int x = screenWidth / 2 + (modeSelectorPosition.alignment == Alignment.LEFT ? -24 : 6);
                int y = screenHeight / 2 - 9;
                float combo = ((StandEntity) stand).getComboMeter();
                int color = ((StandEntity) stand).willHeavyPunchCombo() ? 0x00FF00 : 0xFFFFFF;
                renderFilledIcon(matrixStack, x, y, false, combo, 96, 216, 18, 18, color);
            }
        }
    }
    
    private void renderFilledIcon(MatrixStack matrixStack, int x, int y, boolean translucent, float fill, 
            int texX, int texY, int texWidth, int texHeight, int color) {
        blit(matrixStack, x, y, texX, texY, texWidth, texHeight);
        float[] rgb = ClientUtil.rgb(color);
        float alpha = 1.0F;
        if (translucent) {
            for (int i = 0; i < 3; i++) {
                rgb[i] *= 0.5F;
            }
            alpha = 0.75F;
        }
        RenderSystem.color4f(rgb[0], rgb[1], rgb[2], alpha);
        int px = (int) (18F * fill);
        blit(matrixStack, x, y + texHeight - px, texX + texWidth, texY + texHeight - px, texWidth, px);
        if (translucent) {
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderHamonExerciseBars(MatrixStack matrixStack, ElementPosition position, HamonData hamon, float partialTick) {
        mc.getTextureManager().bind(HamonScreen.WINDOW);
        int x = position.x;
        int y = position.y;
        for (Exercise exercise : Exercise.values()) {
            ElementTransparency transparency = exerciseBarsTransparency.get(exercise);
            if (transparency.shouldRender()) {
                HamonStatsTabGui.drawExerciseBar(this, matrixStack, x, y, hamon, exercise, transparency.getAlpha(partialTick));
                y += 9;
            }
        }
    }
    
    
    
    void drawString(MatrixStack matrixStack, FontRenderer font, ITextComponent text, int x, int y, Alignment alignment, int color) {
        if (alignment == Alignment.RIGHT) {
            x -= font.width(text);
        }
        drawString(matrixStack, font, text, x, y, color);
    }
    
    void drawBackdrop(MatrixStack matrixStack, int x, int y, int width, Alignment alignment, 
            @Nullable ElementTransparency transparency, float partialTick) {
        int backdropColor = mc.options.getBackgroundColor(0.0F);
        if (backdropColor != 0) {
            if (alignment == Alignment.RIGHT) {
                x -= width;
            }
            fill(matrixStack, x - 2, y - 2, x + width + 2, y + mc.font.lineHeight + 2, ColorHelper.PackedColor.multiply(backdropColor, 
                            transparency != null ? transparency.makeTextColorTranclucent(0xFFFFFF, partialTick) : 0xFFFFFFFF));
        }
    }
    
    
    
    static class ElementTransparency {
        private final int ticksMax;
        private final int ticksStartFadeAway;
        private int ticks;
        
        private ElementTransparency(int ticksMax, int ticksStartFadeAway) {
            this.ticksMax = ticksMax;
            this.ticksStartFadeAway = ticksStartFadeAway;
            this.ticks = 0;
        }
        
        private void reset() {
            ticks = ticksMax;
        }
        
        boolean shouldRender() {
            return ticks > 0;
        }
        
        int makeTextColorTranclucent(int color, float partialTick) {
            return addAlpha(color, getAlpha(partialTick));
        }
        
        private static final float MIN_ALPHA = 1F / 63F;
        float getAlpha(float partialTick) {
            if (ticks >= ticksStartFadeAway) {
                return 1F;
            }
            float alpha = Math.max((ticks - partialTick) / (float) ticksStartFadeAway, MIN_ALPHA);
            return alpha;
        }
        
        private void tick() {
            if (ticks > 0) {
                ticks--;
            }
        }
        
        static int addAlpha(int color, float alpha) {
            return color | ((int) (255F * alpha)) << 24 & -0x1000000;
        }
    }
    
    
    
    public boolean setMode(@Nullable PowerClassification power) {
        if (power == null) {
            return setPowerMode(null);
        }
        else {
            ActionsModeConfig<?> chosenMode = null;
            switch (power) {
            case NON_STAND:
                chosenMode = nonStandUiMode;
                break;
            case STAND:
                chosenMode = standUiMode;
                break;
            }
            return setPowerMode(chosenMode);
        }
    }
    
    private final PowerClassification[] modesOrder = {
            null,
            PowerClassification.NON_STAND,
            PowerClassification.STAND
    };
    public void scrollMode() {
        int iCurrent = 0;
        int modes = modesOrder.length;
        for (; iCurrent < modes && getCurrentMode() != modesOrder[iCurrent]; iCurrent++);
        for (int i = 1; i <= modes || !setMode(modesOrder[(iCurrent + i) % modes]); i++);
    }
    
    private boolean setPowerMode(@Nullable ActionsModeConfig<?> mode) {
        if (mode != null && currentMode != mode) {
            if (mode.getPower().hasPower()) {
                modeSelectorTransparency.reset();
                if (currentMode != null) {
                    if (mode != nonStandUiMode) {
                        energyBarTransparency.reset();
                    }
                    else if (mode != standUiMode) {
                        staminaBarTransparency.reset();
                        resolveBarTransparency.reset();
                    }
                }
                currentMode = mode;
                return true;
            }
            return false;
        }
        else {
            modeSelectorTransparency.reset();
            if (currentMode == nonStandUiMode) {
                energyBarTransparency.reset();
            }
            else if (currentMode == standUiMode) {
                staminaBarTransparency.reset();
                resolveBarTransparency.reset();
            }
            currentMode = null;
            return true;
        }
    }

    public void onStandSummon() {
        if (currentMode != standUiMode) {
            setPowerMode(standUiMode);
            standUiMode.autoOpened = true;
        }
    }

    private boolean switchOffStandHud = false;
    public void onStandUnsummon() {
    	switchOffStandHud = true;
    }
    
    private void tickStandUnsummonCheck() {
    	if (currentMode == standUiMode && standUiMode.autoOpened) {
    		IStandPower standPower = standUiMode.getPower();
    		if (standPower == null || !standPower.hasPower() || !standPower.isActive()) {
    			if (switchOffStandHud) {
    				setMode(null);
    				standUiMode.autoOpened = false;
    			}
                switchOffStandHud = false;
    		}
    	}
    	else {
    		switchOffStandHud = false;
    	}
    }
    
    
    
    public void selectAction(ActionType hotbar, int slot) {
        if (currentMode != null) {
            currentMode.setSelectedSlot(hotbar, slot);
        }
    }


    public void scrollAction(ActionType hotbar, boolean backwards) {
        if (currentMode != null) {
            scrollAction(currentMode, hotbar, backwards);
        }
    }

    private static final IntBinaryOperator INC = (i, n) -> (i + 2) % (n + 1) - 1;
    private static final IntBinaryOperator DEC = (i, n) -> (i + n + 1) % (n + 1) - 1;
    private <P extends IPower<P, ?>> void scrollAction(ActionsModeConfig<P> mode, ActionType hotbar, boolean backwards) {
        P power = mode.getPower();
        List<Action<P>> actions = power.getActions(hotbar);
        if (actions.size() == 0) {
            return;
        }
        int startingIndex = mode.getSelectedSlot(hotbar);
        IntBinaryOperator operator = backwards ? DEC : INC;
        int i;
        for (i = operator.applyAsInt(startingIndex, actions.size()); 
             i > -1 && i % actions.size() != startingIndex && actions.get(i).getVisibleAction(power) == null;
             i = operator.applyAsInt(i, actions.size())) {
        }
        mode.setSelectedSlot(hotbar, i);
    }
    

    
    public boolean onClick(ActionType mouseButton, boolean shift) {
        return onClick(mouseButton, shift, currentMode != null ? currentMode.getSelectedSlot(mouseButton) : -1);
    }
    
    public boolean onClick(ActionType mouseButton, boolean shift, int slot) {
        if (currentMode != null) {
            return clickAction(currentMode, mouseButton, shift, slot);
        }
        return false;
    }
    
    private <P extends IPower<P, ?>> boolean clickAction(ActionsModeConfig<P> mode, ActionType actionType, boolean shift, int index) {
        P power = mode.getPower();
        if (power != null) {
            Action<P> action = power.getAction(actionType, index, shift);
            if (action != null) {
                if (power.getHeldAction() != null && action.getHoldDurationMax(power) > 0) {
                    return true;
                }
                RayTraceResult target = InputHandler.getInstance().mouseTarget;
                ClClickActionPacket packet = ClClickActionPacket.withRayTraceResult(power.getPowerClassification(), actionType, shift, index, target);
                if (action.validateInput()) packet.validateInput(action);
                PacketManager.sendToServer(packet);
                ActionTarget actionTarget = ActionTarget.fromRayTraceResult(target);
                boolean actionWentOff = power.clickAction(power.getAction(actionType, index, shift), shift, actionTarget);
                return actionWentOff;
            }
        }
        return false;
    }

    @Nullable
    public Action<?> getSelectedAction(ActionType type) {
        if (currentMode == null) {
            return null;
        }
        return currentMode.getSelectedAction(type, mc.player.isShiftKeyDown());
    }
    
    
    
    public void updatePowersCache() {
        setMode(null);
        standUiMode.setPower(IStandPower.getPlayerStandPower(mc.player));
        standUiMode.autoOpened = false;
        nonStandUiMode.setPower(INonStandPower.getPlayerNonStandPower(mc.player));
        nonStandUiMode.autoOpened = false;
    }

    
    
    private class ElementPosition {
        private int x;
        private int y;
        private Alignment alignment;
    }

    private static final int INDENT = 4;
    public enum PositionConfig {
        TOP_LEFT(Alignment.LEFT, BarsOrientation.HORIZONTAL, 
                screenWidth -> INDENT, (screenHeight, elementHeight) -> INDENT),
        TOP_RIGHT(Alignment.RIGHT, BarsOrientation.HORIZONTAL, 
                screenWidth -> screenWidth - INDENT, (screenHeight, elementHeight) -> INDENT),
        LEFT(Alignment.LEFT, BarsOrientation.VERTICAL, 
                screenWidth -> INDENT, (screenHeight, elementHeight) -> (screenHeight - elementHeight) / 2),
        RIGHT(Alignment.RIGHT, BarsOrientation.VERTICAL, 
                screenWidth -> screenWidth - INDENT, (screenHeight, elementHeight) -> (screenHeight - elementHeight) / 2);
        
        final Alignment aligment;
        final BarsOrientation barsOrientation;
        private final IntUnaryOperator xPos;
        private final IntBinaryOperator yPos;
        
        private PositionConfig(Alignment alignment, BarsOrientation barsOrientation, 
                IntUnaryOperator xPos, IntBinaryOperator yPos) {
            this.aligment = alignment;
            this.barsOrientation = barsOrientation;
            this.xPos = xPos;
            this.yPos = yPos;
        }
        
        int getXPos(int screenWidth) {
            return xPos.applyAsInt(screenWidth);
        }
        
        int getYPos(int screenHeight, int elementHeight) {
            return yPos.applyAsInt(screenHeight, elementHeight);
        }
    }
    
    enum Alignment {
        LEFT,
        RIGHT
    }
    
    enum BarsOrientation {
        VERTICAL,
        HORIZONTAL
    }
}
