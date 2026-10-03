package com.messerocks.crystal.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_2813;
import net.minecraft.class_2824;
import net.minecraft.class_2828;
import net.minecraft.class_2846;
import net.minecraft.class_2868;
import net.minecraft.class_2879;
import net.minecraft.class_2885;
import net.minecraft.class_2886;
import net.minecraft.class_2846.class_2847;

public final class ClickGate {
   private static int tick;
   private static int useTick = -1;
   private static int attackTick = -1;
   private static int clickTick = -1;
   private static int slotTick = -1;
   private static int inventoryTick = -1;
   private static final int AFTER_INVENTORY = 4;
   private static int worldTick = -1;
   private static float sentYaw = Float.NaN;
   private static float sentPitch = Float.NaN;

   private ClickGate() {
   }

   public static void init() {
   }

   public static boolean canUse() {
      return useTick != tick && slotTick != tick && !inventoryRecently();
   }

   public static boolean canAttack() {
      return attackTick != tick && useTick != tick && slotTick != tick && !inventoryRecently();
   }

   public static boolean canClickInventory() {
      return clickTick != tick && slotTick != tick;
   }

   public static boolean quietFor(int ticks) {
      return worldTick < 0 || tick - worldTick >= ticks;
   }

   private static boolean inventoryRecently() {
      return inventoryTick >= 0 && tick - inventoryTick < 4;
   }

   public static boolean canSwitchSlot() {
      return clickTick != tick && slotTick != tick && !inventoryRecently();
   }

   public static boolean clickedThisTick() {
      return clickTick == tick;
   }

   public static boolean slotChangedThisTick() {
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
   }

   @EventHandler(
      priority = -10000
   )
   private static void onPacketSent(Send event) {
      noteWorldAction(event);
      if (event.packet instanceof class_2824 packet) {
         if ("ATTACK".equals(String.valueOf(((IPlayerInteractEntityC2SPacket)packet).meteor$getType()))) {
            attackTick = tick;
         } else {
            useTick = tick;
         }

         clickTick = tick;
      } else if (event.packet instanceof class_2885 || event.packet instanceof class_2886) {
         useTick = tick;
         clickTick = tick;
      } else if (event.packet instanceof class_2813) {
         inventoryTick = tick;
      } else if (event.packet instanceof class_2868) {
         slotTick = tick;
      } else if (event.packet instanceof class_2846 action) {
         if (action.method_12363() == class_2847.field_12969) {
            slotTick = tick;
         } else if (action.method_12363() == class_2847.field_12974) {
            useTick = tick;
            attackTick = tick;
            clickTick = tick;
         }
      }
   }

   static {
      MeteorClient.EVENT_BUS.subscribe(ClickGate.class);
   }
}
