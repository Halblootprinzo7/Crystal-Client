package com.messerocks.crystal.utils;

import com.messerocks.crystal.modules.Stealth;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_310;

public final class HotbarSwap {
   private static final class_310 mc = class_310.method_1551();
   private final int from;
   private final int othersPrevious;
   private final boolean swapped;
   private final boolean ready;
   private static int pendingFrom = -1;
   private static int pendingTo = -1;
   private static int pendingOthersPrevious = -1;
   private static int quietTicks;
   private static int quietNeeded;
   private static final int QUIET_BEFORE_BACK = 4;
   private static int laterSlot = -1;
   private static int laterExpected = -1;
   private static int laterDue;
   private static int laterGiveUp;
   private static int lateTicks;

   private HotbarSwap(int from, int othersPrevious, boolean swapped, boolean ready) {
      this.from = from;
      this.othersPrevious = othersPrevious;
      this.swapped = swapped;
      this.ready = ready;
   }

   public static HotbarSwap silently(int slot) {
      int previous = InvUtils.previousSlot;
      if (mc.field_1724 == null) {
         return new HotbarSwap(-1, previous, false, false);
      } else {
         int selected = mc.field_1724.method_31548().method_67532();
         boolean continuing = pendingFrom >= 0 && selected == pendingTo;
         boolean adopting = !continuing && laterSlot >= 0 && selected == laterExpected;
         int origin = continuing ? pendingFrom : (adopting ? laterSlot : selected);
         int othersPrevious = continuing ? pendingOthersPrevious : previous;
         if (slot == selected) {
            return new HotbarSwap(origin, othersPrevious, continuing, true);
         } else if (!ClickGate.canSwitchSlot()) {
            return new HotbarSwap(origin, othersPrevious, continuing, false);
         } else {
            boolean swapped = InvUtils.swap(slot, false);
            if (swapped && adopting) {
               laterSlot = -1;
            }

            return new HotbarSwap(origin, othersPrevious, swapped || continuing || adopting, swapped);
         }
      }
   }

   public static boolean select(int slot) {
      if (mc.field_1724 != null && slot >= 0 && slot <= 8) {
         if (mc.field_1724.method_31548().method_67532() == slot) {
            return true;
         } else if (!ClickGate.canSwitchSlot()) {
            return false;
         } else {
            pendingFrom = -1;
            pendingTo = -1;
            return InvUtils.swap(slot, false);
         }
      } else {
         return false;
      }
   }

   public static int homeSlot() {
      if (mc.field_1724 == null) {
         return -1;
      } else {
         int selected = mc.field_1724.method_31548().method_67532();
         int base = pendingFrom >= 0 && selected == pendingTo ? pendingFrom : selected;
         return laterSlot >= 0 && base == laterExpected ? laterSlot : base;
      }
   }

   public static boolean stillOn(int slot) {
      if (mc.field_1724 != null && slot >= 0) {
         int selected = mc.field_1724.method_31548().method_67532();
         return selected == slot || pendingFrom == slot && selected == pendingTo;
      } else {
         return false;
      }
   }

   public static void selectLater(int slot, int expected, int delay) {
      if (slot >= 0 && slot <= 8 && slot != expected) {
         if (laterSlot < 0 || laterExpected != expected) {
            laterSlot = slot;
            laterExpected = expected;
            laterDue = lateTicks + 1 + Math.max(1, delay);
            laterGiveUp = laterDue + 40;
         }
      }
   }

   public boolean swapped() {
      return this.swapped;
   }

   public boolean ready() {
      return this.ready;
   }

   public static void syncSelected() {
      if (mc.field_1761 != null) {
         ((IClientPlayerInteractionManager)mc.field_1761).meteor$syncSelected();
      }
   }

   public void back() {
      if (this.swapped && mc.field_1724 != null) {
         pendingFrom = this.from;
         pendingTo = mc.field_1724.method_31548().method_67532();
         pendingOthersPrevious = this.othersPrevious;
         quietTicks = 0;
         quietNeeded = Stealth.paceAtLeast(2, 4);
      }
   }

   private static void runLater() {
      if (laterSlot >= 0) {
         if (mc.field_1724 == null) {
            laterSlot = -1;
         } else {
            int selected = mc.field_1724.method_31548().method_67532();
            boolean through = pendingFrom >= 0 && pendingFrom == laterExpected && selected == pendingTo;
            if (lateTicks <= laterGiveUp && (selected == laterExpected || through)) {
               if (lateTicks >= laterDue && ClickGate.canSwitchSlot()) {
                  if (mc.field_1755 == null) {
                     if (!mc.field_1724.method_6115() || mc.field_1724.method_6058() != class_1268.field_5808) {
                        InvUtils.swap(laterSlot, false);
                        laterSlot = -1;
                        if (through) {
                           pendingFrom = -1;
                           pendingTo = -1;
                        }
                     }
                  }
               }
            } else {
               laterSlot = -1;
            }
         }
      }
   }

   @EventHandler(
      priority = -200
   )
   private static void onTickLate(Pre event) {
      lateTicks++;
      runLater();
      if (pendingFrom >= 0) {
         if (mc.field_1724 == null) {
            pendingFrom = -1;
            pendingTo = -1;
         } else if (mc.field_1724.method_31548().method_67532() != pendingTo || mc.field_1724.method_29504()) {
            pendingFrom = -1;
            pendingTo = -1;
         } else if (ClickGate.canSwitchSlot() && mc.field_1755 == null) {
            // Hand the slot back at once when the player is about to click themselves. Vanilla's use tries the main
            // hand first, so a right-click meant for the off-hand crystals would otherwise place the borrowed item
            // (Sword Place's obsidian) a second time; ClickGate holds that click one tick and it goes out after.
            if (++quietTicks >= quietNeeded || TurnProgress.ownClickPending()) {
               InvUtils.swap(pendingFrom, false);
               InvUtils.previousSlot = pendingOthersPrevious;
               pendingFrom = -1;
               pendingTo = -1;
            }
         } else {
            quietTicks = 0;
         }
      }
   }

   static {
      MeteorClient.EVENT_BUS.subscribe(HotbarSwap.class);
   }
}
