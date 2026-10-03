package com.messerocks.crystal.utils;

import com.messerocks.crystal.modules.Stealth;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.class_1268;
import net.minecraft.class_1750;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_310;
import net.minecraft.class_3965;

public final class AnchorActions {
   private static final Object TURN_OWNER = new Object();
   private static final TurnProgress turn = TurnProgress.SHARED;
   public static final int MAX_CHARGES = 4;
   private static final class_310 mc = class_310.method_1551();

   private AnchorActions() {
   }

   public static int charges(class_2338 pos) {
      if (mc.field_1687 == null) {
         return -1;
      } else {
         class_2680 state = mc.field_1687.method_8320(pos);
         return !state.method_27852(class_2246.field_23152) ? -1 : (Integer)state.method_11654(class_2741.field_23187);
      }
   }

   public static boolean canPlace(class_2338 pos) {
      if (mc.field_1687 == null || mc.field_1724 == null) {
         return false;
      } else if (!BlockUtils.canPlaceBlock(pos, true, class_2246.field_23152)) {
         return false;
      } else if (mc.field_1687.method_8320(pos).method_26215() || !mc.field_1687.method_8320(pos).method_45474()) {
         return BlockUtils.getPlaceSide(pos) != null;
      } else {
         return !canReplaceAt(pos, class_1802.field_23141) ? false : !Stealth.legitPlace() || LegitPlace.forBlock(pos, VanillaLimits.blockRange()) != null;
      }
   }

   public static FindItemResult findAnchor() {
      return InvUtils.findInHotbar(new class_1792[]{class_1802.field_23141});
   }

   public static FindItemResult findGlowstone() {
      return InvUtils.findInHotbar(new class_1792[]{class_1802.field_8801});
   }

   public static boolean offhandBlocksDetonation(int charges) {
      return charges < 4 && mc.field_1724 != null && mc.field_1724.method_6079().method_31574(class_1802.field_8801);
   }

   public static boolean place(class_2338 pos, AnchorActions.Options options) {
      FindItemResult anchor = findAnchor();
      if (!anchor.found()) {
         return false;
      } else {
         class_3965 hit = placeHit(pos);
         return hit == null ? false : clickWith(hit, anchor, options);
      }
   }

   public static boolean canReplaceAt(class_2338 pos, class_1792 item) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         class_2680 state = mc.field_1687.method_8320(pos);
         if (!state.method_26215() && state.method_45474()) {
            class_3965 hit = new class_3965(class_243.method_24953(pos), class_2350.field_11036, pos, false);
            class_1750 context = new class_1750(mc.field_1724, class_1268.field_5808, new class_1799(item), hit);
            return context.method_7716() && context.method_8037().equals(pos);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean charge(class_2338 pos, AnchorActions.Options options) {
      FindItemResult glowstone = findGlowstone();
      return !glowstone.found() ? false : interact(pos, glowstone, options);
   }

   public static int findDetonationSlot() {
      if (mc.field_1724 == null) {
         return -1;
      } else {
         int selected = mc.field_1724.method_31548().method_67532();
         if (!mc.field_1724.method_31548().method_5438(selected).method_31574(class_1802.field_8801)) {
            return selected;
         } else {
            for (int i = 0; i <= 8; i++) {
               if (!mc.field_1724.method_31548().method_5438(i).method_31574(class_1802.field_8801)) {
                  return i;
               }
            }

            return -1;
         }
      }
   }

   public static boolean detonate(class_2338 pos, int charges, AnchorActions.Options options) {
      if (offhandBlocksDetonation(charges)) {
         return false;
      } else {
         int slot = findDetonationSlot();
         return slot < 0 ? false : interactMainHand(pos, slot, options);
      }
   }

   public static String instantCycle(class_2338 pos, AnchorActions.Options options) {
      if (mc.field_1724 == null) {
         return "no player";
      } else {
         int existing = charges(pos);
         boolean needsPlace = existing < 0;
         boolean needsCharge = existing <= 0;
         int assumed = needsCharge ? 1 : existing;
         if (needsPlace && !findAnchor().found()) {
            return "no respawn anchor in the hotbar or hands";
         } else if (needsCharge && !findGlowstone().found()) {
            return "no glowstone block in the hotbar or hands";
         } else if (needsPlace && Stealth.legitPlace()) {
            return "placing a fresh anchor in a single tick is not legit for an anticheat - set Execution to Predicted";
         } else if (offhandBlocksDetonation(assumed)) {
            return "glowstone sits in your offhand, which charges the anchor instead of setting it off";
         } else if (findDetonationSlot() < 0) {
            return "every hotbar slot holds glowstone, nothing left to click with in the main hand";
         } else {
            AnchorActions.Options noRotate = new AnchorActions.Options(false, options.swing(), options.visibleSwap(), options.rotationPriority());
            Runnable body = () -> {
               if (needsPlace) {
                  place(pos, noRotate);
               }

               if (needsCharge) {
                  charge(pos, noRotate);
               }

               detonate(pos, assumed, noRotate);
            };
            class_243 aim;
            if (needsPlace) {
               aim = class_243.method_24953(pos);
            } else {
               class_3965 reachable = hitResultFor(pos);
               if (reachable == null) {
                  return "no face of the anchor is reachable from where you are looking";
               }

               aim = reachable.method_17784();
            }

            run(body, aim, options);
            return null;
         }
      }
   }

   public static String describe(class_2338 pos) {
      if (mc.field_1724 == null) {
         return "no player";
      } else {
         int charges = charges(pos);
         FindItemResult anchor = findAnchor();
         FindItemResult glowstone = findGlowstone();
         return String.format(
               "%d %d %d | charges %s | anchor slot %s | glowstone slot %s | offhand %s",
               pos.method_10263(),
               pos.method_10264(),
               pos.method_10260(),
               charges < 0 ? "none (no anchor)" : String.valueOf(charges),
               anchor.found() ? String.valueOf(anchor.slot()) : "missing",
               glowstone.found() ? String.valueOf(glowstone.slot()) : "missing",
               mc.field_1724.method_6079().method_7960() ? "empty" : mc.field_1724.method_6079().method_7909().toString()
            )
            + String.format(" | detonate slot %s", findDetonationSlot() < 0 ? "none" : String.valueOf(findDetonationSlot()));
      }
   }

   private static class_3965 hitResultFor(class_2338 pos) {
      if (Stealth.legitPlace()) {
         LegitPlace.Result legit = LegitPlace.forExistingBlock(pos, VanillaLimits.blockRange());
         return legit == null ? null : legit.hit();
      } else {
         class_2350 side = BlockUtils.getDirection(pos);
         if (side == null) {
            side = class_2350.field_11036;
         }

         class_243 hitPos = class_243.method_24953(pos).method_1031(side.method_10148() * 0.5, side.method_10164() * 0.5, side.method_10165() * 0.5);
         return new class_3965(hitPos, side, pos, false);
      }
   }

   private static boolean interactMainHand(class_2338 pos, int hotbarSlot, AnchorActions.Options options) {
      class_3965 hitResult = hitResultFor(pos);
      if (hitResult == null) {
         return false;
      } else {
         double yaw = Rotations.getYaw(hitResult.method_17784());
         double pitch = Rotations.getPitch(hitResult.method_17784());
         Runnable action = () -> {
            class_3965 click = confirmed(hitResult, yaw, pitch, options);
            if (click != null) {
               boolean swapped = false;
               if (hotbarSlot != mc.field_1724.method_31548().method_67532()) {
                  InvUtils.swap(hotbarSlot, !options.visibleSwap());
                  swapped = !options.visibleSwap();
               }

               BlockUtils.interact(click, class_1268.field_5808, options.swing());
               if (swapped) {
                  InvUtils.swapBack();
               }
            }
         };
         return run(action, hitResult.method_17784(), options);
      }
   }

   private static boolean run(Runnable action, class_243 aim, AnchorActions.Options options) {
      if (!options.rotate()) {
         if (!options.gate().getAsBoolean()) {
            return false;
         } else {
            action.run();
            return true;
         }
      } else {
         double wantedYaw = Rotations.getYaw(aim);
         double wantedPitch = Rotations.getPitch(aim);
         if (!turn.wouldReach(TURN_OWNER, wantedYaw, wantedPitch)) {
            turn.turnTo(TURN_OWNER, wantedYaw, wantedPitch, options.rotationPriority(), action);
            return false;
         } else {
            return !options.gate().getAsBoolean() ? false : turn.turnTo(TURN_OWNER, wantedYaw, wantedPitch, options.rotationPriority(), action);
         }
      }
   }

   public static void resetTurn() {
      turn.reset(TURN_OWNER);
   }

   private static boolean interact(class_2338 pos, FindItemResult item, AnchorActions.Options options) {
      class_3965 hit = hitResultFor(pos);
      return hit != null && clickWith(hit, item, options);
   }

   public static boolean clickWith(class_3965 hitResult, FindItemResult item, AnchorActions.Options options) {
      double yaw = Rotations.getYaw(hitResult.method_17784());
      double pitch = Rotations.getPitch(hitResult.method_17784());
      Runnable action = () -> {
         class_3965 click = confirmed(hitResult, yaw, pitch, options);
         if (click != null) {
            class_1268 hand = item.getHand();
            boolean swapped = false;
            if (hand == null) {
               InvUtils.swap(item.slot(), !options.visibleSwap());
               swapped = !options.visibleSwap();
               hand = class_1268.field_5808;
            }

            BlockUtils.interact(click, hand, options.swing());
            if (swapped) {
               InvUtils.swapBack();
            }
         }
      };
      return run(action, hitResult.method_17784(), options);
   }

   private static class_3965 confirmed(class_3965 planned, double yaw, double pitch, AnchorActions.Options options) {
      return options.rotate() && Stealth.legitPlace() ? LegitPlace.confirm(planned, yaw, pitch, VanillaLimits.blockRange()) : planned;
   }

   public static class_3965 placeHit(class_2338 pos) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         class_2680 state = mc.field_1687.method_8320(pos);
         if (!state.method_26215() && state.method_45474()) {
            if (!canReplaceAt(pos, class_1802.field_23141)) {
               return null;
            } else {
               LegitPlace.Result replaceable = LegitPlace.forBlock(pos, VanillaLimits.blockRange());
               return replaceable == null ? null : replaceable.hit();
            }
         } else if (Stealth.legitPlace()) {
            LegitPlace.Result legit = LegitPlace.forBlock(pos, VanillaLimits.blockRange());
            return legit == null ? null : legit.hit();
         } else {
            class_2350 side = BlockUtils.getPlaceSide(pos);
            if (side == null) {
               return null;
            } else {
               class_243 hitPos = class_243.method_24953(pos).method_1031(side.method_10148() * 0.5, side.method_10164() * 0.5, side.method_10165() * 0.5);
               return new class_3965(hitPos, side.method_10153(), pos.method_10093(side), false);
            }
         }
      } else {
         return null;
      }
   }

   public static boolean refillHotbar(class_1792 item) {
      if (mc.field_1724 == null) {
         return false;
      } else if (mc.field_1724.field_7512 != mc.field_1724.field_7498) {
         return false;
      } else if (InvUtils.findInHotbar(new class_1792[]{item}).found()) {
         return false;
      } else {
         FindItemResult inInventory = InvUtils.find(stack -> stack.method_31574(item), 9, 35);
         if (!inInventory.found()) {
            return false;
         } else {
            int free = -1;

            for (int i = 0; i <= 8; i++) {
               if (mc.field_1724.method_31548().method_5438(i).method_7960()) {
                  free = i;
                  break;
               }
            }

            if (free == -1) {
               return false;
            } else {
               InvUtils.move().from(inInventory.slot()).toHotbar(free);
               return true;
            }
         }
      }
   }

   public record Options(boolean rotate, boolean swing, boolean visibleSwap, int rotationPriority, BooleanSupplier gate) {
      public Options(boolean rotate, boolean swing, boolean visibleSwap, int rotationPriority) {
         this(rotate, swing, visibleSwap, rotationPriority, () -> true);
      }
   }
}
