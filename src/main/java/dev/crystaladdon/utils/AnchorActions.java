package dev.crystaladdon.utils;

import dev.crystaladdon.modules.Stealth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1661;
import net.minecraft.class_1742;
import net.minecraft.class_1747;
import net.minecraft.class_1750;
import net.minecraft.class_1778;
import net.minecraft.class_1781;
import net.minecraft.class_1786;
import net.minecraft.class_1790;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1826;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2596;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2885;
import net.minecraft.class_310;
import net.minecraft.class_3489;
import net.minecraft.class_3965;
import net.minecraft.class_638;
import net.minecraft.class_746;
import net.minecraft.class_8162;

public final class AnchorActions {
   private static final Object TURN_OWNER = new Object();
   private static final TurnProgress turn = TurnProgress.SHARED;
   public static final int MAX_CHARGES = 4;
   private static final class_310 mc = class_310.method_1551();
   private static final int MIN_STEP_GAP = 2;
   private static final int STEP_GAP = 3;

   private AnchorActions() {
   }

   // Ticks between two clicks of an anchor cycle. One: a hotbar switch already takes a tick of its own between two
   // clicks that need different items, so place - switch - charge - switch - detonate is five ticks, which is what a
   // quick hand does. The old two-to-four on top of that made a cycle take most of a second.
   public static int stepGap() {
      return Math.max(1, Stealth.pace(1));
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
      } else {
         return !mc.field_1687.method_8320(pos).method_26215() && mc.field_1687.method_8320(pos).method_45474()
            ? canReplaceAt(pos, class_1802.field_23141)
            : BlockUtils.getPlaceSide(pos) != null;
      }
   }

   public static FindItemResult findAnchor() {
      if (mc.field_1724 == null) {
         return new FindItemResult(-1, 0);
      } else if (mc.field_1724.method_6047().method_31574(class_1802.field_23141)) {
         return new FindItemResult(mc.field_1724.method_31548().method_67532(), mc.field_1724.method_6047().method_7947());
      } else {
         FindItemResult hotbar = InvUtils.find(stack -> stack.method_31574(class_1802.field_23141), 0, 8);
         if (hotbar.found()) {
            return hotbar;
         } else {
            return mc.field_1724.method_6079().method_31574(class_1802.field_23141)
               ? new FindItemResult(40, mc.field_1724.method_6079().method_7947())
               : hotbar;
         }
      }
   }

   public static FindItemResult findGlowstone() {
      return InvUtils.find(stack -> stack.method_31574(class_1802.field_8801), 0, 8);
   }

   public static boolean offhandBlocksDetonation(int charges) {
      return charges < 4 && mc.field_1724 != null && mc.field_1724.method_6079().method_31574(class_1802.field_8801);
   }

   public static boolean place(class_2338 pos, AnchorActions.Options options) {
      FindItemResult anchor = findAnchor();
      if (!anchor.found()) {
         return false;
      } else {
         class_3965 hit = placeHit(pos, options.rotate());
         return hit == null ? false : clickWith(hit, class_1802.field_23141, options);
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
      if (charges(pos) < 0) {
         return false;
      } else {
         return !findGlowstone().found() ? false : interact(pos, options);
      }
   }

   public static int findDetonationSlot() {
      return findDetonationSlot(-1);
   }

   public static int findDetonationSlot(int preferred) {
      if (mc.field_1724 == null) {
         return -1;
      } else {
         class_1661 inventory = mc.field_1724.method_31548();
         return pickDetonationSlot(preferred, inventory.method_67532(), AnchorActions::cleanDetonator, slot -> {
            class_1799 stack = inventory.method_5438(slot);
            return stack.method_7960() || stack.method_31573(class_3489.field_42611) || stack.method_31573(class_3489.field_42612);
         }, slot -> inventory.method_5438(slot).method_31574(class_1802.field_8801));
      }
   }

   static int pickDetonationSlot(int preferred, int selected, IntPredicate clean, IntPredicate bare, IntPredicate glowstone) {
      if (preferred >= 0 && preferred <= 8 && clean.test(preferred)) {
         return preferred;
      } else if (clean.test(selected)) {
         return selected;
      } else {
         for (int i = 0; i <= 8; i++) {
            if (bare.test(i)) {
               return i;
            }
         }

         for (int ix = 0; ix <= 8; ix++) {
            if (clean.test(ix)) {
               return ix;
            }
         }

         if (!glowstone.test(selected)) {
            return selected;
         } else {
            for (int ixx = 0; ixx <= 8; ixx++) {
               if (!glowstone.test(ixx)) {
                  return ixx;
               }
            }

            return -1;
         }
      }
   }

   private static boolean cleanDetonator(int slot) {
      class_1799 stack = mc.field_1724.method_31548().method_5438(slot);
      class_1792 item = stack.method_7909();
      return !stack.method_31574(class_1802.field_8801)
         && !(item instanceof class_1747)
         && !(item instanceof class_1786)
         && !(item instanceof class_1778)
         && !(item instanceof class_1826)
         && !(item instanceof class_1742)
         && !(item instanceof class_1790)
         && !(item instanceof class_1781)
         && !(item instanceof class_8162);
   }

   public static boolean detonate(class_2338 pos, int charges, AnchorActions.Options options) {
      return detonate(pos, charges, options, -1);
   }

   public static boolean detonate(class_2338 pos, int charges, AnchorActions.Options options, int preferredSlot) {
      if (offhandBlocksDetonation(charges)) {
         return false;
      } else if (charges(pos) <= 0) {
         return false;
      } else {
         int slot = findDetonationSlot(preferredSlot);
         return slot < 0 ? false : interactMainHand(pos, slot, preferredSlot, options);
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

   public static class_3965 hitResultFor(class_2338 pos, boolean rotate) {
      double reach = VanillaLimits.blockRange();
      if (rotate) {
         // Within a burst the anchor was usually just placed along this tick's look, which runs through it: charging
         // and detonating along the same look lets them go out in the same tick instead of waiting for a new turn.
         double[] look = turn.lookThisTick();
         if (look != null) {
            class_3965 same = LegitPlace.along(look[0], look[1], reach);
            if (same != null && same.method_17777().equals(pos)) {
               return same;
            }
         }

         LegitPlace.Result legit = LegitPlace.forExistingBlock(pos, reach);
         return legit == null ? null : legit.hit();
      } else {
         class_3965 hit = LegitPlace.along(LegitPlace.currentYaw(), LegitPlace.currentPitch(), reach);
         return hit != null && hit.method_17777().equals(pos) ? hit : null;
      }
   }

   public static boolean viewLandsOn(class_2338 pos) {
      return mc.field_1724 != null && mc.field_1687 != null && hitResultFor(pos, false) != null;
   }

   public static boolean sneakBlocksInteraction() {
      return mc.field_1724 == null ? false : sneakBlocksInteraction(mc.field_1724.method_6047());
   }

   public static boolean sneakBlocksInteraction(class_1799 mainHand) {
      return mc.field_1724 == null ? false : mc.field_1724.method_5715() && (!mainHand.method_7960() || !mc.field_1724.method_6079().method_7960());
   }

   public static boolean sneakBlocksCharge() {
      return mc.field_1724 != null && mc.field_1724.method_5715();
   }

   public static boolean sneakBlocksDetonation() {
      return sneakBlocksDetonation(-1);
   }

   public static boolean sneakBlocksDetonation(int preferred) {
      if (mc.field_1724 == null) {
         return false;
      } else {
         int slot = findDetonationSlot(preferred);
         return sneakBlocksInteraction(slot < 0 ? mc.field_1724.method_6047() : mc.field_1724.method_31548().method_5438(slot));
      }
   }

   private static boolean interactMainHand(class_2338 pos, int hotbarSlot, int preferred, AnchorActions.Options options) {
      if (sneakBlocksInteraction(mc.field_1724.method_31548().method_5438(hotbarSlot))) {
         return false;
      } else {
         class_3965 hitResult = hitResultFor(pos, options.rotate());
         if (hitResult == null) {
            return false;
         } else {
            double yaw = Rotations.getYaw(hitResult.method_17784());
            double pitch = Rotations.getPitch(hitResult.method_17784());
            BooleanSupplier action = () -> {
               if (charges(pos) <= 0) {
                  return false;
               } else {
                  class_3965 click = confirmed(hitResult, yaw, pitch, options);
                  if (click == null) {
                     return false;
                  } else {
                     int slot = findDetonationSlot(preferred);
                     if (slot >= 0 && !offhandBlocksDetonation(charges(pos))) {
                        return sneakBlocksInteraction(mc.field_1724.method_31548().method_5438(slot))
                           ? false
                           : send(click, slot == mc.field_1724.method_31548().method_67532() ? -1 : slot, class_1268.field_5808, options);
                     } else {
                        return false;
                     }
                  }
               }
            };
            return run(action, hitResult.method_17784(), options, hotbarSlot);
         }
      }
   }

   private static boolean run(BooleanSupplier action, class_243 aim, AnchorActions.Options options, int slot) {
      if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1761 == null) {
         return false;
      } else if (!options.rotate()) {
         return action.getAsBoolean();
      } else {
         double wantedYaw = Rotations.getYaw(aim);
         double wantedPitch = Rotations.getPitch(aim);
         Object owner = options.turnOwner() != null ? options.turnOwner() : TURN_OWNER;
         if (!turn.wouldReach(owner, wantedYaw, wantedPitch, options.rotationPriority())) {
            turn.turnTo(owner, wantedYaw, wantedPitch, options.rotationPriority(), null);
            if (!turn.lastCallBlocked()) {
               preselect(slot, options);
            }

            return false;
         } else {
            class_746 player = mc.field_1724;
            class_638 world = mc.field_1687;
            return turn.turnTo(owner, wantedYaw, wantedPitch, options.rotationPriority(), () -> {
               if (mc.field_1724 == player && mc.field_1687 == world && mc.field_1761 != null) {
                  action.getAsBoolean();
               }
            });
         }
      }
   }

   public static void resetTurn() {
      turn.reset(TURN_OWNER);
   }

   public static void resetTurn(Object owner) {
      turn.reset(owner);
   }

   private static boolean interact(class_2338 pos, AnchorActions.Options options) {
      if (sneakBlocksCharge()) {
         return false;
      } else {
         class_3965 hit = hitResultFor(pos, options.rotate());
         return hit != null && clickWith(hit, class_1802.field_8801, options);
      }
   }

   public static boolean clickWith(class_3965 hitResult, class_1792 expected, AnchorActions.Options options) {
      if (mc.field_1724 == null) {
         return false;
      } else {
         AnchorActions.Grip planned = gripFor(expected, hitResult);
         if (planned == null) {
            return false;
         } else {
            boolean usingAnchor = charges(hitResult.method_17777()) >= 0;
            class_2338 destination = usingAnchor
               ? null
               : new class_1750(mc.field_1724, class_1268.field_5808, new class_1799(expected), hitResult).method_8037().method_10062();
            double yaw = Rotations.getYaw(hitResult.method_17784());
            double pitch = Rotations.getPitch(hitResult.method_17784());
            BooleanSupplier action = () -> {
               if (usingAnchor && sneakBlocksCharge()) {
                  return false;
               } else {
                  class_3965 click = confirmed(hitResult, yaw, pitch, options, destination, expected);
                  if (click == null) {
                     return false;
                  } else {
                     AnchorActions.Grip grip = gripFor(expected, click);
                     return grip == null ? false : send(click, grip.slot(), grip.hand(), options);
                  }
               }
            };
            return run(action, hitResult.method_17784(), options, planned.slot());
         }
      }
   }

   // Whether item can actually be used for this click: in the main hand, in the hotbar, or in the offhand with a main
   // hand that would not take the click itself.
   public static boolean canGrip(class_1792 item, class_3965 hit) {
      return gripFor(item, hit) != null;
   }

   private static AnchorActions.Grip gripFor(class_1792 item, class_3965 hit) {
      if (mc.field_1724 == null) {
         return null;
      } else if (mc.field_1724.method_6047().method_31574(item)) {
         return new AnchorActions.Grip(class_1268.field_5808, -1);
      } else if (mc.field_1724.method_6079().method_31574(item) && !InventoryGuard.offhandInFlight() && VanillaClick.reaches(hit, class_1268.field_5810)) {
         return new AnchorActions.Grip(class_1268.field_5810, -1);
      } else {
         FindItemResult hotbar = InvUtils.find(stack -> stack.method_31574(item), 0, 8);
         return hotbar.found() ? new AnchorActions.Grip(class_1268.field_5808, hotbar.slot()) : null;
      }
   }

   private static boolean send(class_3965 click, int slot, class_1268 hand, AnchorActions.Options options) {
      if (!ClickGate.canUse() || Stealth.handsBusy() || Stealth.paused()) {
         return false;
      } else if (InventoryGuard.offhandInFlight()) {
         return false;
      } else {
         boolean swap = slot >= 0 && slot != mc.field_1724.method_31548().method_67532();
         if (!options.ready().getAsBoolean()) {
            if (swap) {
               preselect(slot, options);
            }

            return false;
         } else {
            HotbarSwap silent = null;
            if (swap) {
               if (options.visibleSwap()) {
                  if (!HotbarSwap.select(slot)) {
                     return false;
                  }
               } else {
                  silent = HotbarSwap.silently(slot);
                  if (!silent.ready()) {
                     return false;
                  }
               }
            }

            if (ClickGate.slotChangedThisTick()) {
               if (silent != null) {
                  silent.back();
               }

               return false;
            } else if (!options.gate().getAsBoolean()) {
               if (silent != null) {
                  silent.back();
               }

               return false;
            } else {
               int usesBefore = ClickGate.usesThisTick();
               class_1268 acted = VanillaClick.use(click, options.swing());
               if (silent != null) {
                  silent.back();
               }

               // Anchor-optimizer mods take the detonation click over and remove the anchor on the client at once; the
               // click still reaches the server, but vanilla's result never comes back. A click that left counts.
               boolean wentOut = acted == null && ClickGate.usesThisTick() > usesBefore;
               if (acted != hand && !wentOut) {
                  return false;
               } else {
                  options.onSent().run();
                  return true;
               }
            }
         }
      }
   }

   private static void preselect(int slot, AnchorActions.Options options) {
      if (mc.field_1724 != null && options.visibleSwap()) {
         if (slot >= 0 && slot <= 8 && slot != mc.field_1724.method_31548().method_67532()) {
            if (Stealth.canUse() && !Stealth.handsBusy() && !Stealth.paused() && !InventoryGuard.offhandInFlight()) {
               if (options.ready().getAsBoolean() || options.dueSoon()) {
                  HotbarSwap.select(slot);
               }
            }
         }
      }
   }

   private static class_3965 confirmed(class_3965 planned, double yaw, double pitch, AnchorActions.Options options) {
      return confirmed(planned, yaw, pitch, options, null, null);
   }

   private static class_3965 confirmed(class_3965 planned, double yaw, double pitch, AnchorActions.Options options, class_2338 destination, class_1792 item) {
      if (!options.rotate()) {
         yaw = LegitPlace.currentYaw();
         pitch = LegitPlace.currentPitch();
      }

      if (destination != null) {
         return LegitPlace.confirmPlacement(destination, item, yaw, pitch, VanillaLimits.blockRange());
      } else {
         return charges(planned.method_17777()) < 0 ? null : LegitPlace.confirmCrystal(planned.method_17777(), yaw, pitch, VanillaLimits.blockRange());
      }
   }

   public static class_3965 placeHit(class_2338 pos) {
      return placeHit(pos, true);
   }

   public static class_3965 placeHit(class_2338 pos, boolean rotate) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         class_2680 state = mc.field_1687.method_8320(pos);
         double reach = VanillaLimits.blockRange();
         if (!state.method_26215() && state.method_45474() && !canReplaceAt(pos, class_1802.field_23141)) {
            return null;
         } else if (rotate) {
            LegitPlace.Result legit = LegitPlace.forBlock(pos, reach);
            return legit == null ? null : legit.hit();
         } else {
            return LegitPlace.confirmPlacement(pos, class_1802.field_23141, LegitPlace.currentYaw(), LegitPlace.currentPitch(), reach);
         }
      } else {
         return null;
      }
   }

   static final class ClientTicks {
      private static int now;

      private ClientTicks() {
      }

      static void start() {
      }

      static int now() {
         return now;
      }

      @EventHandler(
         priority = 10000
      )
      private static void onTick(Pre event) {
         now++;
      }

      static {
         MeteorClient.EVENT_BUS.subscribe(AnchorActions.ClientTicks.class);
      }
   }

   private record Grip(class_1268 hand, int slot) {
   }

   public record Options(
      boolean rotate,
      boolean swing,
      boolean visibleSwap,
      int rotationPriority,
      BooleanSupplier gate,
      Runnable onSent,
      Object turnOwner,
      BooleanSupplier ready,
      BooleanSupplier ahead
   ) {
      public Options(boolean rotate, boolean swing, boolean visibleSwap, int rotationPriority, BooleanSupplier gate, Runnable onSent) {
         this(rotate, swing, visibleSwap, rotationPriority, gate, onSent, null, () -> true, null);
      }

      public Options(boolean rotate, boolean swing, boolean visibleSwap, int rotationPriority, BooleanSupplier gate) {
         this(rotate, swing, visibleSwap, rotationPriority, gate, () -> {});
      }

      public Options(boolean rotate, boolean swing, boolean visibleSwap, int rotationPriority) {
         this(rotate, swing, visibleSwap, rotationPriority, () -> true);
      }

      public AnchorActions.Options withOwner(Object owner) {
         return new AnchorActions.Options(
            this.rotate, this.swing, this.visibleSwap, this.rotationPriority, this.gate, this.onSent, owner, this.ready, this.ahead
         );
      }

      public AnchorActions.Options withReady(BooleanSupplier ready) {
         return new AnchorActions.Options(
            this.rotate, this.swing, this.visibleSwap, this.rotationPriority, this.gate, this.onSent, this.turnOwner, ready, this.ahead
         );
      }

      public AnchorActions.Options withAhead(BooleanSupplier ahead) {
         return new AnchorActions.Options(
            this.rotate, this.swing, this.visibleSwap, this.rotationPriority, this.gate, this.onSent, this.turnOwner, this.ready, ahead
         );
      }

      public boolean dueSoon() {
         return (this.ahead != null ? this.ahead : this.ready).getAsBoolean();
      }
   }

   public static final class Refill {
      private final TotemRules.InventoryReach reach = new TotemRules.InventoryReach();
      private int askedAt = Integer.MIN_VALUE;

      public Refill() {
         AnchorActions.ClientTicks.start();
      }

      public boolean tick(class_1792... items) {
         int now = AnchorActions.ClientTicks.now();
         if (this.askedAt == now) {
            return false;
         } else {
            if (this.askedAt != now - 1) {
               this.reach.reset();
            }

            this.askedAt = now;
            AnchorActions.Refill.Move move = null;

            for (class_1792 item : items) {
               move = plan(item);
               if (move != null) {
                  break;
               }
            }

            if (move == null) {
               this.reach.reset();
               return false;
            } else if (!this.reach.advance(move, InventoryGuard.couldBeOpen(2), () -> Stealth.pace(6))) {
               return false;
            } else if (!InventoryGuard.swapToHotbar(move.from(), move.hotbar())) {
               return false;
            } else {
               this.reach.reset();
               return true;
            }
         }
      }

      public void reset() {
         this.reach.reset();
         this.askedAt = Integer.MIN_VALUE;
      }

      private static AnchorActions.Refill.Move plan(class_1792 item) {
         if (AnchorActions.mc.field_1724 == null) {
            return null;
         } else if (AnchorActions.mc.field_1724.field_7512 != AnchorActions.mc.field_1724.field_7498) {
            return null;
         } else if (InvUtils.find(stack -> stack.method_31574(item), 0, 8).found()) {
            return null;
         } else {
            FindItemResult inInventory = InvUtils.find(stack -> stack.method_31574(item), 9, 35);
            if (!inInventory.found()) {
               return null;
            } else {
               for (int i = 0; i <= 8; i++) {
                  if (AnchorActions.mc.field_1724.method_31548().method_5438(i).method_7960()) {
                     return new AnchorActions.Refill.Move(item, inInventory.slot(), i);
                  }
               }

               return null;
            }
         }
      }

      private record Move(class_1792 item, int from, int hotbar) {
      }
   }

   public static final class Sightings {
      private static final int OWN_MEMORY = 100;
      private final ReactionClock reactions = new ReactionClock();
      private final Map<AnchorActions.Sightings.Key, Integer> own = new HashMap<>();
      private final AnchorActions.WorthMemory<AnchorActions.Sightings.Key> worth = new AnchorActions.WorthMemory<>();
      private int tickedAt = Integer.MIN_VALUE;
      private int pacedGap;
      private int pacedAt = Integer.MIN_VALUE;

      public Sightings() {
         AnchorActions.ClientTicks.start();
      }

      private static int now() {
         return AnchorActions.ClientTicks.now();
      }

      public void tick() {
         int now = now();
         this.tickedAt = now;
         this.reactions.tick();
         this.worth.settle(key -> this.reactions.forget(new AnchorActions.Sightings.Worth(key)));
         this.own.values().removeIf(readyAt -> now - readyAt > 100);
         this.own.keySet().removeIf(key -> AnchorActions.charges(key.pos()) < 0);
      }

      public void clear() {
         this.reactions.clear();
         this.own.clear();
         this.worth.clear();
         this.pacedAt = Integer.MIN_VALUE;
      }

      public static AnchorActions.Sightings.State stateAt(class_2338 pos) {
         int charges = AnchorActions.charges(pos);
         return charges < 0 ? AnchorActions.Sightings.State.Air : (charges == 0 ? AnchorActions.Sightings.State.Anchor : AnchorActions.Sightings.State.Loaded);
      }

      public boolean ready(class_2338 pos) {
         AnchorActions.Sightings.Key key = this.key(pos);
         Integer readyAt = this.own.get(key);
         return readyAt != null ? now() >= readyAt : this.reactions.ready(key);
      }

      public boolean noticed(class_2338 pos) {
         AnchorActions.Sightings.Key key = this.key(pos);
         return this.own.containsKey(key) ? true : this.reactions.ready(key);
      }

      private AnchorActions.Sightings.Key key(class_2338 pos) {
         AnchorActions.Sightings.Key key = new AnchorActions.Sightings.Key(pos.method_10062(), stateAt(pos));
         if (key.state() != AnchorActions.Sightings.State.Air) {
            this.reactions.forget(new AnchorActions.Sightings.Key(key.pos(), AnchorActions.Sightings.State.Air));
         }

         return key;
      }

      public boolean reacted(Object event) {
         return this.reactions.ready(event);
      }

      public boolean worth(class_2338 pos) {
         return this.worth.worth(this.key(pos), key -> this.reactions.ready(new AnchorActions.Sightings.Worth(key)));
      }

      public boolean pickable(class_2338 pos) {
         boolean worthIt = this.worth(pos);
         boolean seen = stateAt(pos) == AnchorActions.Sightings.State.Air || this.ready(pos);
         return worthIt && seen;
      }

      public boolean settled(class_2338 pos) {
         return this.settledBy(pos, now());
      }

      public boolean settledSoon(class_2338 pos) {
         return this.settledBy(pos, now() + 1);
      }

      private boolean settledBy(class_2338 pos, int tick) {
         class_2338 at = pos.method_10062();

         for (AnchorActions.Sightings.State state : AnchorActions.Sightings.State.values()) {
            Integer readyAt = this.own.get(new AnchorActions.Sightings.Key(at, state));
            if (readyAt != null && tick < readyAt) {
               return false;
            }
         }

         return true;
      }

      public void pace(int gap) {
         this.pacedGap = gap;
         this.pacedAt = now();
      }

      public void onSent(class_2596<?> packet) {
         if (packet instanceof class_2885 interact && AnchorActions.mc.field_1687 != null) {
            class_3965 hit = interact.method_12543();
            class_2338 clicked = hit.method_17777().method_10062();
            int gap = this.pacedAt == now() ? this.pacedGap : AnchorActions.stepGap();
            this.markOwn(clicked, gap);
            this.markOwn(clicked.method_10093(hit.method_17780()), gap);
            if (AnchorActions.charges(clicked) >= 0) {
               this.reactions.forget(new AnchorActions.Sightings.Key(clicked, AnchorActions.Sightings.State.Air));
            }
         }
      }

      private void markOwn(class_2338 pos, int gap) {
         AnchorActions.Sightings.State state = stateAt(pos);
         if (state != AnchorActions.Sightings.State.Air) {
            AnchorActions.Sightings.Key key = new AnchorActions.Sightings.Key(pos, state);
            int now = now();
            this.own.put(key, now + gap);
            if (this.tickedAt == now) {
               this.worth.ownDoing(key);
            } else {
               this.worth.ownDoingAhead(key);
            }
         }
      }

      private record Key(class_2338 pos, AnchorActions.Sightings.State state) {
      }

      public static enum State {
         Air,
         Anchor,
         Loaded;
      }

      private record Worth(AnchorActions.Sightings.Key key) {
      }
   }

   static final class WorthMemory<K> {
      private final Set<K> known = new HashSet<>();
      private final Set<K> now = new HashSet<>();
      private final Set<K> clocked = new HashSet<>();
      private final Set<K> ahead = new HashSet<>();

      void ownDoing(K key) {
         this.known.add(key);
         this.now.add(key);
      }

      void ownDoingAhead(K key) {
         this.ahead.add(key);
      }

      boolean worth(K key, Predicate<K> clock) {
         this.now.add(key);
         if (this.known.contains(key)) {
            return true;
         } else {
            this.clocked.add(key);
            return clock.test(key);
         }
      }

      void settle(Consumer<K> forget) {
         this.known.retainAll(this.now);
         this.clocked.removeIf(keyx -> {
            if (this.now.contains(keyx)) {
               return false;
            } else {
               forget.accept((K)keyx);
               return true;
            }
         });
         this.now.clear();

         for (K key : this.ahead) {
            this.ownDoing(key);
         }

         this.ahead.clear();
      }

      void clear() {
         this.known.clear();
         this.now.clear();
         this.clocked.clear();
         this.ahead.clear();
      }
   }
}
