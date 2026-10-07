package dev.crystaladdon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import dev.crystaladdon.modules.Stealth;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_2813;
import net.minecraft.class_2824;
import net.minecraft.class_2828;
import net.minecraft.class_2846;
import net.minecraft.class_2868;
import net.minecraft.class_2879;
import net.minecraft.class_2885;
import net.minecraft.class_2886;
import net.minecraft.class_310;
import net.minecraft.class_2846.class_2847;

public final class ClickGate {
   private static final class_310 mc = class_310.method_1551();
   private static int tick;
   private static int useTick = -1;
   private static int attackTick = -1;
   private static int clickTick = -1;
   private static int slotTick = -1;
   private static int inventoryTick = -1;
   // Clicks of each kind (right, left, slot change) this tick may carry. 1 is one finger on each button; a module whose
   // speed setting is above 20 a second raises it for the tick it acts in, so its clicks can go out back to back.
   private static int burst = 1;
   private static int uses;
   private static int attacks;
   private static int slotChanges;
   private static boolean inClick;
   private static boolean clickCounted;
   private static final int AFTER_INVENTORY = 4;
   private static int worldTick = -1;
   private static float sentYaw = Float.NaN;
   private static float sentPitch = Float.NaN;

   private ClickGate() {
   }

   public static void init() {
   }

   public static boolean canUse() {
      return canUse(burst);
   }

   // As canUse, for a clicker with its own allowance: the player's own presses pass 1, so the burst a module raised
   // for its speed above 20 never lets one of yours follow its clicks in the same tick.
   public static boolean canUse(int burstFor) {
      return usesThisTick() < burstFor && !slotChangedThisTick(burstFor) && !inventoryRecently();
   }

   public static boolean canAttack() {
      return canAttack(burst);
   }

   // Vanilla handles every attack press before the use presses, so with one click of each a tick an attack never
   // follows a use. See canUse(int) for burstFor.
   public static boolean canAttack(int burstFor) {
      return attacksThisTick() < burstFor && (useTick != tick || burstFor > 1) && !slotChangedThisTick(burstFor) && !inventoryRecently();
   }

   // Up to this many clicks of each kind in the current tick (1 to 3). Only ever raises the limit; it drops back to 1
   // when the next tick starts.
   public static void allowBurst(int clicks) {
      burst = Math.max(burst, Math.max(1, Math.min(3, clicks)));
   }

   // allowBurst for one actor's own clicks only: returns the burst as it was, for restoreBurst to put back once that
   // actor's clicks of this pass are out (or did not go out). Raised for the rest of the tick, a module's allowance for
   // its speed above 20 would also let every other actor of the tick - an offhand swap, a refill, the player's own
   // presses - click or switch after it, in an order vanilla never sends.
   public static int raiseBurst(int clicks) {
      int before = burst;
      allowBurst(clicks);
      return before;
   }

   public static void restoreBurst(int before) {
      burst = Math.max(1, before);
   }

   public static int burst() {
      return burst;
   }

   // Right clicks that went out this tick. A click is counted once however many packets it took - vanilla sends a
   // block click and then an item use for the same press when the block does nothing.
   public static int usesThisTick() {
      return useTick == tick ? uses : 0;
   }

   public static int attacksThisTick() {
      return attackTick == tick ? attacks : 0;
   }

   private static int slotChangesThisTick() {
      return slotTick == tick ? slotChanges : 0;
   }

   static void clickStart() {
      inClick = true;
      clickCounted = false;
   }

   static void clickEnd() {
      inClick = false;
   }

   // Clicks per tick a speed setting asks for: 20 a second is one every tick, anything above needs more than one.
   public static int perTick(double perSecond) {
      return Math.max(1, Math.min(3, (int)Math.ceil(perSecond / 20.0 - 1.0E-9)));
   }

   public static boolean canClickInventory() {
      return clickTick != tick && slotTick != tick;
   }

   public static boolean quietFor(int ticks) {
      return worldTick < 0 || tick - worldTick >= ticks;
   }

   // Stealth's after-inventory sets how long; never past AFTER_INVENTORY, and never less than the inventory click's
   // own tick.
   private static boolean inventoryRecently() {
      if (inventoryTick < 0 || tick - inventoryTick >= AFTER_INVENTORY) {
         return false;
      } else {
         return tick - inventoryTick < Math.max(1, Math.min(AFTER_INVENTORY, Stealth.afterInventoryTicks()));
      }
   }

   public static boolean canSwitchSlot() {
      return canSwitchSlot(burst);
   }

   // See canUse(int) for burstFor: a slot change of no module's burst - a silent loan handed back - passes 1.
   public static boolean canSwitchSlot(int burstFor) {
      if (inventoryRecently()) {
         return false;
      } else {
         // In a burst a switch may follow a click in the same tick: place an anchor, take the glowstone, charge it. One
         // switch for each click of the burst, not one more: a switch after the burst's last click is vanilla's number
         // key coming after a mouse button within one tick, which it never handles in that order.
         return burstFor > 1 ? slotChangesThisTick() < burstFor : clickTick != tick && slotTick != tick;
      }
   }

   public static boolean clickedThisTick() {
      return clickTick == tick;
   }

   // Whether a click has to wait for the slot that was just selected. Vanilla reads the number keys before the mouse
   // buttons in the same tick, so a switch and a click in one tick is something a player can do; Stealth's
   // same-tick-switch (on by default) allows it, otherwise the click waits a tick after the switch.
   public static boolean slotChangedThisTick() {
      return slotChangedThisTick(burst);
   }

   private static boolean slotChangedThisTick(int burstFor) {
      return slotTick == tick && burstFor <= 1 && !Stealth.sameTickSwitch();
   }

   // A slot change went out this tick, whatever same-tick-switch and the burst say.
   public static boolean slotSwitchedThisTick() {
      return slotTick == tick;
   }

   private static void noteWorldAction(Send event) {
      if (event.packet instanceof class_2828 move) {
         if (!move.method_36172()) {
            return;
         }

         float yaw = move.method_12271(sentYaw);
         float pitch = move.method_12270(sentPitch);
         boolean turned = Float.isNaN(sentYaw) || yaw != sentYaw || pitch != sentPitch;
         sentYaw = yaw;
         sentPitch = pitch;
         if (turned) {
            worldTick = tick;
         }
      } else if (event.packet instanceof class_2824
         || event.packet instanceof class_2885
         || event.packet instanceof class_2886
         || event.packet instanceof class_2868
         || event.packet instanceof class_2846
         || event.packet instanceof class_2879) {
         worldTick = tick;
      }
   }

   @EventHandler(
      priority = 10001
   )
   private static void onTickStart(Pre event) {
      tick++;
      burst = 1;
      // A number key or the scroll wheel changes the selected slot without a packet; vanilla only sends it later in
      // this tick. Sending it now marks slotTick, so no module clicks in the same tick as that switch.
      if (mc.field_1724 != null && mc.field_1761 != null) {
         ((IClientPlayerInteractionManager)mc.field_1761).meteor$syncSelected();
      }
   }

   @EventHandler(
      priority = -10000
   )
   private static void onPacketSent(Send event) {
      noteWorldAction(event);
      if (event.packet instanceof class_2824 packet) {
         if ("ATTACK".equals(String.valueOf(((IPlayerInteractEntityC2SPacket)packet).meteor$getType()))) {
            noteAttack();
         } else {
            noteUse();
         }

         clickTick = tick;
      } else if (event.packet instanceof class_2885 || event.packet instanceof class_2886) {
         noteUse();
         clickTick = tick;
      } else if (event.packet instanceof class_2813) {
         inventoryTick = tick;
      } else if (event.packet instanceof class_2868) {
         noteSlotChange();
      } else if (event.packet instanceof class_2846 action) {
         if (action.method_12363() == class_2847.field_12969) {
            noteSlotChange();
         } else if (action.method_12363() == class_2847.field_12974) {
            noteUse();
            noteAttack();
            clickTick = tick;
         }
      }
   }

   private static void noteUse() {
      if (inClick && clickCounted) {
         return;
      } else {
         clickCounted = inClick;
         uses = useTick == tick ? uses + 1 : 1;
         useTick = tick;
      }
   }

   private static void noteAttack() {
      attacks = attackTick == tick ? attacks + 1 : 1;
      attackTick = tick;
   }

   private static void noteSlotChange() {
      slotChanges = slotTick == tick ? slotChanges + 1 : 1;
      slotTick = tick;
   }

   static {
      MeteorClient.EVENT_BUS.subscribe(ClickGate.class);
   }
}
