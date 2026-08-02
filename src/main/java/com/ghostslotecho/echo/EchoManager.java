package com.ghostslotecho.echo;

import com.ghostslotecho.config.GhostSlotConfig;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EchoManager {
    private static final EchoManager INSTANCE = new EchoManager();

    public static class SlotKey {
        private final int inventoryHash;
        private final int slotIndex;

        public SlotKey(Inventory inventory, int slotIndex) {
            this.inventoryHash = System.identityHashCode(inventory);
            this.slotIndex = slotIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SlotKey slotKey = (SlotKey) o;
            return inventoryHash == slotKey.inventoryHash && slotIndex == slotKey.slotIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(inventoryHash, slotIndex);
        }

        public int getInventoryHash() {
            return inventoryHash;
        }
    }

    private final Map<SlotKey, EchoSlotData> echoMap = new HashMap<>();
    private final Map<SlotKey, ItemStack> previousSlotStacks = new HashMap<>();

    public static EchoManager getInstance() {
        return INSTANCE;
    }

    public boolean isAllowedSlot(Slot slot) {
        if (slot == null || slot.inventory == null) return false;

        String invClassName = slot.inventory.getClass().getName();
        // Exclude Creative Inventory search/tabs & Crafting Result/Output slots
        if (invClassName.contains("Creative") || invClassName.contains("CraftingResult") || invClassName.contains("ResultInventory")) {
            return false;
        }

        // Exclude Player Inventory Armor slots (indices 36-39) and Offhand (40)
        if (slot.inventory instanceof PlayerInventory) {
            int index = slot.getIndex();
            if (index >= 36) {
                return false;
            }
        }

        return true;
    }

    public EchoSlotData getEcho(Slot slot) {
        if (!isAllowedSlot(slot)) return null;
        SlotKey key = new SlotKey(slot.inventory, slot.getIndex());
        EchoSlotData echo = echoMap.get(key);
        if (echo != null && echo.isExpired(GhostSlotConfig.get().echoDurationSeconds)) {
            echoMap.remove(key);
            return null;
        }
        return echo;
    }

    public void togglePin(Slot slot) {
        if (!isAllowedSlot(slot)) return;
        SlotKey key = new SlotKey(slot.inventory, slot.getIndex());
        EchoSlotData echo = echoMap.get(key);
        if (echo != null) {
            echo.setPinned(!echo.isPinned());
        }
    }

    public void createPinFromStack(Slot slot, ItemStack stack) {
        if (!isAllowedSlot(slot) || stack == null || stack.isEmpty()) return;
        SlotKey key = new SlotKey(slot.inventory, slot.getIndex());
        echoMap.put(key, new EchoSlotData(stack, true));
    }

    public void updateSlot(Slot slot, ItemStack currentStack) {
        if (!isAllowedSlot(slot)) return;
        SlotKey key = new SlotKey(slot.inventory, slot.getIndex());
        ItemStack prevStack = previousSlotStacks.get(key);

        if (!currentStack.isEmpty()) {
            // Item placed in this slot -> clear ghost for this slot
            EchoSlotData currentEcho = echoMap.get(key);
            if (currentEcho != null && !currentEcho.isPinned()) {
                echoMap.remove(key);
            }

            // Clear matching unpinned ghosts if item returned to storage (even in another slot)
            clearMatchingEchoesInInventory(slot.inventory, currentStack);
        } else if (prevStack != null && !prevStack.isEmpty()) {
            // Slot just became empty in this session -> create ghost
            EchoSlotData existing = echoMap.get(key);
            boolean pinned = existing != null && existing.isPinned();
            echoMap.put(key, new EchoSlotData(prevStack, pinned));
        }

        previousSlotStacks.put(key, currentStack.copy());
    }

    private void clearMatchingEchoesInInventory(Inventory inventory, ItemStack insertedStack) {
        int invHash = System.identityHashCode(inventory);
        echoMap.entrySet().removeIf(entry -> {
            SlotKey key = entry.getKey();
            EchoSlotData data = entry.getValue();
            if (key.getInventoryHash() == invHash && !data.isPinned() && data.matches(insertedStack)) {
                return true;
            }
            return false;
        });
    }

    public void clearNonPinned() {
        echoMap.entrySet().removeIf(entry -> !entry.getValue().isPinned());
    }

    public void resetScreenState() {
        previousSlotStacks.clear();
        if (!GhostSlotConfig.get().persistOnClose) {
            clearNonPinned();
        }
    }

    /**
     * Finds the best target slot for smart quick-move of stackToMove.
     * Priority order:
     * 1. Lowest-index empty slot pinned for matching item.
     * 2. Lowest-index empty slot with unpinned matching ghost.
     */
    public int findMatchingEchoSlotId(ScreenHandler handler, int sourceSlotId, ItemStack stackToMove) {
        if (stackToMove.isEmpty() || handler == null) {
            return -1;
        }

        boolean isSourcePlayerInventory = isPlayerInventorySlot(handler, sourceSlotId);

        int firstMatchingPinnedSlot = -1;
        int firstMatchingUnpinnedSlot = -1;

        for (int i = 0; i < handler.slots.size(); i++) {
            if (i == sourceSlotId) continue;

            boolean isTargetPlayerInventory = isPlayerInventorySlot(handler, i);
            if (isSourcePlayerInventory == isTargetPlayerInventory && handler.slots.size() > 36) {
                continue;
            }

            Slot targetSlot = handler.slots.get(i);
            if (!isAllowedSlot(targetSlot)) continue;
            if (!targetSlot.getStack().isEmpty()) continue;
            if (!targetSlot.canInsert(stackToMove)) continue;

            EchoSlotData echo = getEcho(targetSlot);
            if (echo != null && echo.matches(stackToMove)) {
                if (echo.isPinned()) {
                    if (firstMatchingPinnedSlot == -1) {
                        firstMatchingPinnedSlot = i;
                    }
                } else {
                    if (firstMatchingUnpinnedSlot == -1) {
                        firstMatchingUnpinnedSlot = i;
                    }
                }
            }
        }

        if (firstMatchingPinnedSlot != -1) {
            return firstMatchingPinnedSlot;
        }
        return firstMatchingUnpinnedSlot;
    }

    /**
     * Finds a safe empty slot for items that don't match any ghost,
     * protecting slots pinned for DIFFERENT items.
     */
    public int findSafeEmptySlotId(ScreenHandler handler, int sourceSlotId, ItemStack stackToMove) {
        if (stackToMove.isEmpty() || handler == null) {
            return -1;
        }

        boolean isSourcePlayerInventory = isPlayerInventorySlot(handler, sourceSlotId);

        for (int i = 0; i < handler.slots.size(); i++) {
            if (i == sourceSlotId) continue;

            boolean isTargetPlayerInventory = isPlayerInventorySlot(handler, i);
            if (isSourcePlayerInventory == isTargetPlayerInventory && handler.slots.size() > 36) {
                continue;
            }

            Slot targetSlot = handler.slots.get(i);
            if (!isAllowedSlot(targetSlot)) continue;
            if (!targetSlot.getStack().isEmpty()) continue;
            if (!targetSlot.canInsert(stackToMove)) continue;

            EchoSlotData echo = getEcho(targetSlot);
            // Protect slots pinned for a DIFFERENT item
            if (echo != null && echo.isPinned() && !echo.matches(stackToMove)) {
                continue;
            }

            return i;
        }

        return -1;
    }

    private boolean isPlayerInventorySlot(ScreenHandler handler, int slotId) {
        int totalSlots = handler.slots.size();
        if (totalSlots > 36) {
            return slotId >= totalSlots - 36;
        }
        return false;
    }
}
