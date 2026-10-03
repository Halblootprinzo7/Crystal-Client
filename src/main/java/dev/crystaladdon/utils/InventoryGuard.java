package dev.crystaladdon.utils;

import dev.crystaladdon.modules.Stealth;
import java.lang.ref.WeakReference;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2815;
import net.minecraft.class_2846;
import net.minecraft.class_310;
import net.minecraft.class_490;
import net.minecraft.class_2846.class_2847;

public final class InventoryGuard {
   private static final class_310 mc = class_310.method_1551();
   private static final MoveLock lock = new MoveLock();
   private static WeakReference<Object> player = new WeakReference<>(null);
   private static WeakReference<Object> world = new WeakReference<>(null);
   private static int tick;
   private static final int MOVE_GAP = 10;
   private static int nextMoveTick = Integer.MIN_VALUE;
   private static final int QUIET_BEFORE_MOVE = 6;
   private static int offhandSwapUntil = -1;
   private static WeakReference<Object> offhandSwapPlayer = new WeakReference<>(null);

   private InventoryGuard() {
   }

   @EventHandler(
      priority = 10000
   )
   private static void onTick(Pre event) {
      tick++;
   }

   public static boolean canMove() {
      return inventoryFree() && Stealth.allowsInventoryClick() && ClickGate.canClickInventory() && tick >= nextMoveTick && couldBeOpen();
   }

   private static boolean couldBeOpen() {
      return couldBeOpen(6);
   }

   public static boolean couldBeOpen(int quietTicks) {
      if (mc.field_1724 == null || mc.field_1761 == null) {
         return false;
      } else if (mc.field_1755 instanceof class_490) {
         return true;
      } else if (!Stealth.allowsInventoryClick()) {
         return false;
      } else if (mc.field_1761.method_2923()) {
         return false;
      } else {
         return !mc.field_1690.field_1886.method_1434() && !mc.field_1690.field_1904.method_1434() ? ClickGate.quietFor(quietTicks) : false;
      }
   }

   public static boolean swapToHotbar(int from, int hotbar) {
      return hotbar >= 0 && hotbar <= 8 ? swapClick(from, hotbar) : false;
   }

   public static boolean swapToOffhand(int from) {
      return swapClick(from, 40);
   }

   private static boolean swapClick(int from, int button) {
      if (from < 0 || from == 40) {
         return false;
      } else if (!claimMove()) {
         return false;
      } else {
         int id = SlotUtils.indexToId(from);
         mc.field_1761.method_2906(mc.field_1724.field_7498.field_7763, id, button, class_1713.field_7791, mc.field_1724);
         moved();
         return true;
      }
   }

   public static boolean inventoryFree() {
      return mc.field_1724 != null
         && mc.field_1687 != null
         && mc.field_1761 != null
         && mc.field_1724.field_7512 == mc.field_1724.field_7498
         && mc.field_1724.field_7512.method_34255().method_7960();
   }

   public static void moved() {
      if (mc.field_1724 != null && mc.method_1562() != null && mc.field_1755 == null) {
         mc.method_1562().method_52787(new class_2815(mc.field_1724.field_7498.field_7763));
      }
   }

   public static boolean offhandInFlight() {
      return mc.field_1724 != null && offhandSwapPlayer.get() == mc.field_1724 && tick < offhandSwapUntil;
   }

   public static void offhandSettled() {
      offhandSwapUntil = -1;
   }

   public static boolean swapWithOffhand(int slot) {
      if (mc.field_1724 == null || mc.field_1687 == null || mc.method_1562() == null) {
         return false;
      } else if (slot < 0 || slot > 8 || mc.field_1755 != null || mc.field_1724.method_7325()) {
         return false;
      } else if (mc.field_1724.field_7512 != mc.field_1724.field_7498) {
         return false;
      } else if (!ClickGate.canSwitchSlot()) {
         return false;
      } else if (!claim()) {
         return false;
      } else {
         if (slot != mc.field_1724.method_31548().method_67532()) {
            InvUtils.swap(slot, false);
         }

         mc.method_1562().method_52787(new class_2846(class_2847.field_12969, class_2338.field_10980, class_2350.field_11033));
         offhandSwapPlayer = new WeakReference<>(mc.field_1724);
         offhandSwapUntil = tick + CrystalUtils.confirmTicks(EntityUtils.getPing(mc.field_1724));
         return true;
      }
   }

   public static boolean claimMove() {
      return !canMove() ? false : claim();
   }

   public static boolean canMoveIn(class_1703 handler) {
      return mc.field_1724 != null && mc.field_1687 != null && mc.field_1761 != null && handler != null
         ? mc.field_1724.field_7512 == handler && handler.method_34255().method_7960()
         : false;
   }

   public static boolean claimMoveIn(class_1703 handler) {
      return !canMoveIn(handler) ? false : claim();
   }

   private static boolean claim() {
      if (player.get() != mc.field_1724 || world.get() != mc.field_1687) {
         player = new WeakReference<>(mc.field_1724);
         world = new WeakReference<>(mc.field_1687);
         lock.reset();
         nextMoveTick = Integer.MIN_VALUE;
      }

      if (!lock.tryClaim(tick, mc.field_1724.field_6012)) {
         return false;
      } else {
         nextMoveTick = tick + Stealth.paceAtLeast(5, 10);
         return true;
      }
   }

   static {
      MeteorClient.EVENT_BUS.subscribe(InventoryGuard.class);
   }
}
