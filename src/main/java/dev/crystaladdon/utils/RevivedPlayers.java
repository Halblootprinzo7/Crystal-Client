package dev.crystaladdon.utils;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_243;
import net.minecraft.class_2663;
import net.minecraft.class_310;
import net.minecraft.class_638;

public final class RevivedPlayers {
   private static final class_310 mc = class_310.method_1551();
   private static final double MOVED_SQ = 0.25;
   static final int SETTLE_TICKS = 20;
   private static final Map<Integer, RevivedPlayers.Death> deaths = new HashMap<>();
   private static WeakReference<class_638> world = new WeakReference<>(null);
   private static int tick;

   private RevivedPlayers() {
   }

   public static void init() {
   }

   @EventHandler(
      priority = 9000
   )
   private static void onTick(Pre event) {
      tick++;
      if (sameWorld()) {
         deaths.values().removeIf(deathx -> {
            class_1657 playerx = deathx.player.get();
            return playerx == null || playerx.method_31481() || playerx.method_6032() > 0.0F || mc.field_1687.method_8469(playerx.method_5628()) != playerx;
         });

         for (class_1657 player : mc.field_1687.method_18456()) {
            if (player != mc.field_1724 && !player.method_31481() && !(player.method_6032() > 0.0F)) {
               RevivedPlayers.Death death = record(player);
               if (tick - death.since < 20) {
                  death.pos = player.method_73189();
               }
            }
         }
      }
   }

   @EventHandler
   private static void onPacket(Receive event) {
      if (event.packet instanceof class_2663 packet) {
         if (packet.method_11470() == 3) {
            mc.execute(() -> {
               if (sameWorld()) {
                  if (packet.method_11469(mc.field_1687) instanceof class_1657 player && player != mc.field_1724) {
                     deaths.put(player.method_5628(), new RevivedPlayers.Death(player, tick));
                  }
               }
            });
         }
      }
   }

   public static boolean isRevived(class_1657 player) {
      if (sameWorld() && player != null) {
         if (!player.method_31481() && !(player.method_6032() > 0.0F)) {
            RevivedPlayers.Death death = record(player);
            return tick - death.since < 20 ? false : player.method_73189().method_1025(death.pos) > 0.25;
         } else {
            deaths.remove(player.method_5628());
            return false;
         }
      } else {
         return false;
      }
   }

   private static RevivedPlayers.Death record(class_1657 player) {
      RevivedPlayers.Death death = deaths.get(player.method_5628());
      if (death == null || death.player.get() != player) {
         death = new RevivedPlayers.Death(player, tick);
         deaths.put(player.method_5628(), death);
      }

      return death;
   }

   private static boolean sameWorld() {
      if (mc.field_1687 == null) {
         deaths.clear();
         world = new WeakReference<>(null);
         return false;
      } else {
         if (mc.field_1687 != world.get()) {
            deaths.clear();
            world = new WeakReference<>(mc.field_1687);
         }

         return true;
      }
   }

   static {
      MeteorClient.EVENT_BUS.subscribe(RevivedPlayers.class);
   }

   private static final class Death {
      final WeakReference<class_1657> player;
      class_243 pos;
      final int since;

      Death(class_1657 player, int since) {
         this.player = new WeakReference<>(player);
         this.pos = player.method_73189();
         this.since = since;
      }
   }
}
