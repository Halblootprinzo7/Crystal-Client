package dev.crystaladdon.utils;

import dev.crystaladdon.modules.Stealth;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ReactionClock {
   private static final int FORGET_AFTER = 10;
   private final Map<Object, long[]> seen = new HashMap<>();
   private long now;

   public void tick() {
      this.now++;
      Iterator<long[]> it = this.seen.values().iterator();

      while (it.hasNext()) {
         if (this.now - it.next()[1] > 10L) {
            it.remove();
         }
      }
   }

   public boolean ready(Object key) {
      long[] entry = this.seen.get(key);
      if (entry == null) {
         // reaction-time 0 answers in the tick the thing is first seen, not the one after.
         long readyAt = this.now + Stealth.reactionTicks();
         this.seen.put(key, new long[]{readyAt, this.now});
         return this.now >= readyAt;
      } else {
         entry[1] = this.now;
         return this.now >= entry[0];
      }
   }

   public void saw(Object key) {
      this.ready(key);
   }

   public void forget(Object key) {
      this.seen.remove(key);
   }

   public void clear() {
      this.seen.clear();
   }
}
