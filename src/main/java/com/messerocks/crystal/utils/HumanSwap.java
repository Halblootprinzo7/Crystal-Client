package com.messerocks.crystal.utils;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.class_310;

// Hotbar switching the way a hand does it, for Stealth's human mode: one key press per tick, never in the same
// tick as a click, and an item is only used once it has been in hand for a full tick. Switching back after a
// use waits for the next tick too. Silent swaps (select, click, select back within one tick) never happen here.
public final class HumanSwap {
   private static final class_310 mc = class_310.method_1551();
   // How long a module's claim on the hotbar keeps less important modules from switching it away.
   private static final int CLAIM_TICKS = 3;
   private static Object player;
   private static int knownSlot = -1;
   private static int selectedSince = -1;
   private static int lastSwitchTick = -1;
   private static int lastUseTick = -1;
   private static int pendingReturn = -1;
   private static int claimPriority;
   private static int claimTick = -1;

   private HumanSwap() {
   }

   // Stealth calls this first thing every tick, so a switch the player made by hand counts as well.
   public static void observe() {
      if (mc.field_1724 != null) {
         if (mc.field_1724 != player) {
            player = mc.field_1724;
            knownSlot = -1;
            selectedSince = -1;
            lastSwitchTick = -1;
            lastUseTick = -1;
            pendingReturn = -1;
            claimTick = -1;
         }

         int selected = selected();
         if (selected != knownSlot) {
            knownSlot = selected;
            selectedSince = age();
         }
      }
   }

   // True once slot has been the selected slot for at least one full tick. Otherwise starts the switch when
   // that is allowed this tick, and the caller tries again next tick.
   public static boolean ready(int slot, int priority) {
      if (mc.field_1724 == null || slot < 0 || slot > 8) {
         return false;
      } else {
         observe();
         int age = age();
         if (claimedAbove(priority)) {
            return false;
         } else {
            claimPriority = priority;
            claimTick = age;
            if (selected() == slot) {
               return selectedSince < age;
            } else {
               switchTo(slot);
               return false;
            }
         }
      }
   }

   // A more important module (a key press) is in the middle of switching or using the hotbar. Less important ones
   // hold off clicking meanwhile; otherwise their click every tick would keep that switch from ever going out.
   public static boolean claimedAbove(int priority) {
      return mc.field_1724 != null && claimTick >= 0 && priority < claimPriority && age() - claimTick <= CLAIM_TICKS;
   }

   // Selects slot if a key press is allowed this tick. True when slot is selected afterwards.
   public static boolean switchTo(int slot) {
      if (mc.field_1724 == null || slot < 0 || slot > 8) {
         return false;
      } else {
         observe();
         int age = age();
         if (selected() == slot) {
            return true;
         } else if (lastSwitchTick == age || lastUseTick == age) {
            return false;
         } else {
            InvUtils.swap(slot, false);
            knownSlot = slot;
            selectedSince = age;
            lastSwitchTick = age;
            return true;
         }
      }
   }

   // Marks this tick as one with a click in it, so no switch can follow inside the same tick.
   public static void used() {
      if (mc.field_1724 != null) {
         lastUseTick = age();
      }
   }

   public static void returnLater(int slot) {
      pendingReturn = slot;
   }

   // Stealth calls this last thing every tick: a switch back someone asked for goes out once nothing used an item
   // this tick and nobody already pressed a key.
   public static void tickReturn() {
      if (pendingReturn >= 0 && mc.field_1724 != null) {
         if (switchTo(pendingReturn)) {
            pendingReturn = -1;
         }
      }
   }

   private static int selected() {
      return mc.field_1724.method_31548().method_67532();
   }

   private static int age() {
      return mc.field_1724.field_6012;
   }
}
