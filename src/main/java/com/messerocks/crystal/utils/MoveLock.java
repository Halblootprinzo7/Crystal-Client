package com.messerocks.crystal.utils;

final class MoveLock {
   private int movedTick = Integer.MIN_VALUE;
   private int movedAge = Integer.MIN_VALUE;

   boolean tryClaim(int tick, int age) {
      if (this.movedTick == tick && age - this.movedAge >= 0 && age - this.movedAge < 2) {
         return false;
      } else {
         this.movedTick = tick;
         this.movedAge = age;
         return true;
      }
   }

   void reset() {
      this.movedTick = Integer.MIN_VALUE;
      this.movedAge = Integer.MIN_VALUE;
   }
}
