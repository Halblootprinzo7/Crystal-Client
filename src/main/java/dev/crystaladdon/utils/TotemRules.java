package dev.crystaladdon.utils;

import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

public final class TotemRules {
   public static final double ALWAYS_HEALTH = 20.0;
   static final double CURSOR_TOLERANCE = 2.0;
   public static final int INVENTORY_REACH_TICKS = 6;
   public static final int MIN_GLIDE_TICKS = 6;

   private TotemRules() {
   }

   public static boolean healthAllows(double threshold, double totalHealth) {
      return threshold >= 20.0 || totalHealth <= threshold;
   }

   public static int totemSource(IntPredicate isTotem, int selected) {
      for (int i = 0; i <= 8; i++) {
         if (i != selected && isTotem.test(i)) {
            return i;
         }
      }

      for (int ix = 9; ix <= 35; ix++) {
         if (isTotem.test(ix)) {
            return ix;
         }
      }

      return selected >= 0 && selected <= 8 && isTotem.test(selected) ? selected : -1;
   }

   public static double guiToWindow(double gui, int scaledSize, int windowSize) {
      return scaledSize <= 0 ? gui : gui * windowSize / scaledSize;
   }

   public static TotemRules.WarpOutcome warpOutcome(double mouseX, double mouseY, double targetX, double targetY, double returnX, double returnY) {
      if (near(mouseX, targetX) && near(mouseY, targetY)) {
         return TotemRules.WarpOutcome.ARRIVED;
      } else {
         return mouseX == returnX && mouseY == returnY ? TotemRules.WarpOutcome.UNREPORTED : TotemRules.WarpOutcome.USER_MOVED;
      }
   }

   public static boolean samePoint(double ax, double ay, double bx, double by) {
      return near(ax, bx) && near(ay, by);
   }

   private static boolean near(double a, double b) {
      return Math.abs(a - b) <= 2.0;
   }

   public static int glideTicks(double distance, double width) {
      double bits = Math.log(Math.max(0.0, distance) / Math.max(1.0, width) + 1.0) / Math.log(2.0);
      return Math.max(6, (int)Math.ceil(2.0 + 2.0 * bits));
   }

   public static double easeReach(double t) {
      double s = Math.max(0.0, Math.min(1.0, t));
      return s * s * s * (10.0 - s * (15.0 - 6.0 * s));
   }

   public static double[] glidePoint(double sx, double sy, double tx, double ty, double curve, double t) {
      double s = easeReach(t);
      double dx = tx - sx;
      double dy = ty - sy;
      double bow = curve * Math.sin(Math.PI * s);
      return new double[]{sx + dx * s - dy * bow, sy + dy * s + dx * bow};
   }

   public static final class InventoryReach {
      private int open;
      private int needed = -1;
      private Object target;

      public boolean advance(Object move, boolean canOpen, IntSupplier ticksNeeded) {
         if (!Objects.equals(move, this.target)) {
            this.reset();
            this.target = move;
         }

         if (!canOpen) {
            this.open = 0;
            return false;
         } else {
            if (this.needed < 0) {
               this.needed = Math.max(1, ticksNeeded.getAsInt());
            }

            if (this.open >= this.needed) {
               return true;
            } else {
               this.open++;
               return false;
            }
         }
      }

      public void reset() {
         this.open = 0;
         this.needed = -1;
         this.target = null;
      }
   }

   public static enum WarpOutcome {
      ARRIVED,
      UNREPORTED,
      USER_MOVED;
   }
}
