package com.ghostslotecho.echo;

import net.minecraft.item.ItemStack;

public class EchoSlotData {
    private final ItemStack ghostStack;
    private final long createdAtMillis;
    private boolean pinned;

    public EchoSlotData(ItemStack stack, boolean pinned) {
        this.ghostStack = stack.copy();
        this.ghostStack.setCount(1);
        this.createdAtMillis = System.currentTimeMillis();
        this.pinned = pinned;
    }

    public ItemStack getGhostStack() {
        return ghostStack;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isExpired(int durationSeconds) {
        if (pinned) {
            return false;
        }
        return System.currentTimeMillis() - createdAtMillis > durationSeconds * 1000L;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || ghostStack.isEmpty()) {
            return false;
        }
        return ItemStack.areItemsAndComponentsEqual(ghostStack, stack);
    }
}
