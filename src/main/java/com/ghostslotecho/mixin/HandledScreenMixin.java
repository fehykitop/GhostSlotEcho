package com.ghostslotecho.mixin;

import com.ghostslotecho.GhostSlotEchoMod;
import com.ghostslotecho.config.GhostSlotConfig;
import com.ghostslotecho.config.GhostSlotConfigScreen;
import com.ghostslotecho.echo.EchoManager;
import com.ghostslotecho.echo.EchoSlotData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen {

    @Shadow @Nullable protected Slot focusedSlot;
    @Shadow protected T handler;

    @Shadow protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    @Unique
    private boolean ghostslotecho$isHandlingSmartMove = false;

    protected HandledScreenMixin(net.minecraft.text.Text title) {
        super(title);
    }

    @Inject(method = "handledScreenTick", at = @At("HEAD"))
    private void onHandledScreenTick(CallbackInfo ci) {
        if ((Object) this instanceof CreativeInventoryScreen) return;

        if (this.handler != null) {
            for (Slot slot : this.handler.slots) {
                if (EchoManager.getInstance().isAllowedSlot(slot)) {
                    EchoManager.getInstance().updateSlot(slot, slot.getStack());
                }
            }
        }
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void onDrawSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        if ((Object) this instanceof CreativeInventoryScreen) return;
        if (!EchoManager.getInstance().isAllowedSlot(slot)) return;

        if (slot.getStack().isEmpty()) {
            EchoSlotData echo = EchoManager.getInstance().getEcho(slot);
            if (echo != null && !echo.getGhostStack().isEmpty()) {
                float opacity = GhostSlotConfig.get().ghostOpacity;

                context.getMatrices().push();

                // 1. Draw item icon
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);

                context.drawItem(echo.getGhostStack(), slot.x, slot.y);

                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                // 2. Translate Z forward so overlay & star render IN FRONT of 3D block models (Z = 300)
                context.getMatrices().translate(0.0F, 0.0F, 300.0F);

                // 3. Blend slot grey fill (0x8B8B8B) over 3D blocks & items to desaturate them into slot background
                int alphaInt = (int) ((1.0f - opacity) * 220);
                int slotGreyMask = (alphaInt << 24) | 0x8B8B8B;
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, slotGreyMask);

                // 4. Render pinned Star ★ ON TOP of item icon and overlay mask
                if (echo.isPinned()) {
                    context.drawText(this.textRenderer, "★", slot.x + 10, slot.y + 0, 0xFFFFD700, true);
                }

                RenderSystem.disableBlend();
                context.getMatrices().pop();
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof CreativeInventoryScreen) return;

        if (GhostSlotConfig.get().enablePinning && this.focusedSlot != null) {
            Slot slot = this.focusedSlot;
            if (!EchoManager.getInstance().isAllowedSlot(slot)) return;

            boolean isMiddleClick = (button == 2);
            boolean isShiftRightClick = (button == 1 && Screen.hasShiftDown());

            if (isMiddleClick || isShiftRightClick) {
                EchoSlotData echo = EchoManager.getInstance().getEcho(slot);
                if (echo != null) {
                    EchoManager.getInstance().togglePin(slot);
                    cir.setReturnValue(true);
                    cir.cancel();
                } else if (!slot.getStack().isEmpty()) {
                    EchoManager.getInstance().createPinFromStack(slot, slot.getStack());
                    cir.setReturnValue(true);
                    cir.cancel();
                }
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (GhostSlotEchoMod.openConfigKeyBinding != null && GhostSlotEchoMod.openConfigKeyBinding.matchesKey(keyCode, scanCode)) {
            if (this.client != null) {
                this.client.setScreen(GhostSlotConfigScreen.create(this));
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void onMouseClickInject(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (ghostslotecho$isHandlingSmartMove) return;
        if ((Object) this instanceof CreativeInventoryScreen) return;

        if (GhostSlotConfig.get().enableSmartQuickMove && actionType == SlotActionType.QUICK_MOVE && slot != null && !slot.getStack().isEmpty()) {
            if (!EchoManager.getInstance().isAllowedSlot(slot)) return;

            ItemStack stackToMove = slot.getStack();
            
            // 1. First search for a matching ghost slot (pinned matching slots in order, then unpinned)
            int targetSlotId = EchoManager.getInstance().findMatchingEchoSlotId(this.handler, slotId, stackToMove);

            // 2. If no matching ghost slot, find a safe empty slot that is NOT pinned for a different item
            if (targetSlotId == -1) {
                targetSlotId = EchoManager.getInstance().findSafeEmptySlotId(this.handler, slotId, stackToMove);
            }

            if (targetSlotId != -1 && targetSlotId < this.handler.slots.size()) {
                Slot targetSlot = this.handler.slots.get(targetSlotId);

                ghostslotecho$isHandlingSmartMove = true;
                try {
                    this.onMouseClick(slot, slotId, 0, SlotActionType.PICKUP);
                    this.onMouseClick(targetSlot, targetSlotId, 0, SlotActionType.PICKUP);
                    if (!this.handler.getCursorStack().isEmpty()) {
                        this.onMouseClick(slot, slotId, 0, SlotActionType.PICKUP);
                    }
                } finally {
                    ghostslotecho$isHandlingSmartMove = false;
                }

                ci.cancel();
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        EchoManager.getInstance().resetScreenState();
    }
}
