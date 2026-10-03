package com.messerocks.crystal.utils;

import com.messerocks.crystal.modules.Stealth;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_310;

// Reaction time for automatic modules in Stealth's human mode. Something new (a target, a crystal, a free spot)
// only becomes actionable once it has been there for the reaction time. Something that stays around, or comes
// back within half a second, is already known and needs no new reaction - the same spot hit again and again.
public final class ReactionTimer {
   private static final class_310 mc = class_310.method_1551();
   private static final int FORGET_TICKS = 10;
   private final Map<Object, ReactionTimer.Seen> seen = new HashMap<>();
   private int lastSweep = -1;

   public boolean ready(Object key) {
      long delay = Stealth.reactionNanos();
      if (delay <= 0L || mc.field_1724 == null) {
         return true;
      } else {
         int age = mc.field_1724.field_6012;
         long now = System.nanoTime();
         this.sweep(age);
         ReactionTimer.Seen entry = this.seen.get(key);
         if (entry == null) {
            entry = new ReactionTimer.Seen(now, delay);
            this.seen.put(key, entry);
         }

         entry.lastSeen = age;
         return now - entry.firstSeen >= entry.delay;
      }
   }

   public void clear() {
      this.seen.clear();
      this.lastSweep = -1;
   }

   private void sweep(int age) {
      if (age != this.lastSweep) {
         this.lastSweep = age;
         this.seen.values().removeIf(entry -> age - entry.lastSeen > FORGET_TICKS || age < entry.lastSeen);
      }
   }

   private static final class Seen {
      final long firstSeen;
      final long delay;
      int lastSeen;

      Seen(long firstSeen, long delay) {
         this.firstSeen = firstSeen;
         this.delay = delay;
      }
   }
}
