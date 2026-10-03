package com.messerocks.crystal.utils;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1657;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_638;

public final class RevivedPlayers {
   private static final class_310 mc = class_310.method_1551();
   private static final double MOVED_SQ = 0.25;
   private static final Map<Integer, class_243> deathPos = new HashMap<>();
   private static class_638 world;

   private RevivedPlayers() {
   }

   public static boolean isRevived(class_1657 player) {
      if (mc.field_1687 != world) {
         deathPos.clear();
         world = mc.field_1687;
      }

      if (player == null) {
         return false;
      } else if (!player.method_31481() && !(player.method_6032() > 0.0F)) {
         class_243 died = deathPos.computeIfAbsent(player.method_5628(), id -> player.method_73189());
         return player.method_73189().method_1025(died) > 0.25;
      } else {
         deathPos.remove(player.method_5628());
         return false;
      }
   }
}
