package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.ClickGate;
import com.messerocks.crystal.utils.CrystalUtils;
import com.messerocks.crystal.utils.HotbarSwap;
import com.messerocks.crystal.utils.InventoryGuard;
import com.messerocks.crystal.utils.ReactionClock;
import com.messerocks.crystal.utils.RevivedPlayers;
import com.messerocks.crystal.utils.TotemRules;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1661;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_310;
import net.minecraft.class_490;

public class SmartTotem extends CrystalModule {
   private static final int RESELECT_TICKS = 3;
   private static final Object NEED = new Object();
   private static final Object SAFE = new Object();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> threshold = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("health-threshold")).description("Swap once your health minus the predicted damage drops to this."))
            .defaultValue(11.0)
            .min(0.0)
            .sliderMax(36.0)
            .build()
      );
   private final Setting<Boolean> predictCrystals = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-crystals"))
                  .description("Count the strongest crystal currently placed near you as incoming damage."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> predictRange = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("predict-range")).description("How far to look for crystals."))
               .defaultValue(6.0)
               .min(0.0)
               .sliderMax(12.0)
               .visible(this.predictCrystals::get))
            .build()
      );
   private final Setting<Boolean> predictFall = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-fall"))
                  .description("Count the fall damage you would take right now."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> restore = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("restore-offhand"))
                  .description("Put the previous offhand item back once you are safe again. An offhand that was empty keeps the totem."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Report swaps in chat."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Integer> restoreDelay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("restore-delay"))
                     .description(
                        "Consecutive safe ticks before restoring the old offhand item. Never sooner than Stealth's reaction-time after it became safe."
                     ))
                  .defaultValue(20))
               .min(0)
               .sliderMax(100)
               .visible(this.restore::get))
            .build()
      );
   private final Setting<Double> restoreEnemyRange = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("restore-enemy-range"))
                  .description(
                     "Keep the totem in the offhand while a non-friend player is this close, and count the restore delay only from when they left. 0 restores regardless."
                  ))
               .defaultValue(8.0)
               .min(0.0)
               .sliderMax(16.0)
               .visible(this.restore::get))
            .build()
      );
   private int originSlot = -1;
   private class_1799 previousOffhand = class_1799.field_8037;
   private int swapPendingUntil = -1;
   private int reselectSlot = -1;
   private int reselectAt = -1;
   private int swapSlot = -1;
   private class_1799 expectedOffhand = class_1799.field_8037;
   private Object inventoryOwner;
   private int safeTicks;
   private final ReactionClock clock = new ReactionClock();
   private final TotemRules.InventoryReach reach = new TotemRules.InventoryReach();

   public SmartTotem() {
      super(CrystalAddon.CATEGORY, "smart-totem", "Offhand totem swapping driven by predicted damage.");
   }

   public void onDeactivate() {
      if (this.reselectSlot >= 0 && this.mc.field_1724 != null && this.inventoryOwner == this.mc.field_1724 && HotbarSwap.stillOn(this.swapSlot)) {
         HotbarSwap.selectLater(this.reselectSlot, this.swapSlot, Stealth.reactionTicks());
      }

      this.originSlot = -1;
      this.previousOffhand = class_1799.field_8037;
      this.swapPendingUntil = -1;
      this.clearReselect();
      this.expectedOffhand = class_1799.field_8037;
      this.inventoryOwner = null;
      this.safeTicks = 0;
      this.clock.clear();
      this.reach.reset();
   }

   @EventHandler(
      priority = 300
   )
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         this.clock.tick();
         if (this.inventoryOwner != this.mc.field_1724) {
            this.originSlot = -1;
            this.previousOffhand = class_1799.field_8037;
            this.inventoryOwner = this.mc.field_1724;
            this.safeTicks = 0;
            this.swapPendingUntil = -1;
            this.clearReselect();
            this.expectedOffhand = class_1799.field_8037;
            this.clock.clear();
            this.reach.reset();
         }

         if (this.swapPendingUntil >= 0) {
            boolean answered = !this.expectedOffhand.method_7960() && class_1799.method_7984(this.expectedOffhand, this.mc.field_1724.method_6079());
            if (!answered && this.mc.field_1724.field_6012 < this.swapPendingUntil) {
               return;
            }

            this.swapPendingUntil = -1;
            this.expectedOffhand = class_1799.field_8037;
            InventoryGuard.offhandSettled();
            if (this.reselectSlot >= 0) {
               int delay = Math.max(1, Stealth.pace(3));
               this.reselectAt = this.mc.field_1724.field_6012 + delay;
               if (this.mc.field_1724.method_31548().method_67532() == this.swapSlot) {
                  HotbarSwap.selectLater(this.reselectSlot, this.swapSlot, delay);
               }
            }
         }

         if (this.reselectSlot >= 0 && this.reselectAt >= 0 && this.mc.field_1724.field_6012 >= this.reselectAt && this.mc.field_1755 == null) {
            this.reselect();
         }

         if ((this.mc.field_1755 == null || this.inventoryOpen()) && InventoryGuard.inventoryFree()) {
            if (this.originSlot >= 0
               && (
                  !class_1799.method_31577(this.previousOffhand, this.mc.field_1724.method_31548().method_5438(this.originSlot))
                     || !this.mc.field_1724.method_6079().method_7960() && !this.mc.field_1724.method_6079().method_31574(class_1802.field_8288)
               )) {
               this.originSlot = -1;
               this.previousOffhand = class_1799.field_8037;
            }

            boolean holdingTotem = this.mc.field_1724.method_6079().method_31574(class_1802.field_8288);
            if (!holdingTotem || this.originSlot != -1 && (Boolean)this.restore.get()) {
               boolean danger = EntityUtils.getTotalHealth(this.mc.field_1724) - this.predictedDamage() <= (Double)this.threshold.get();
               boolean unsafe = danger || this.originSlot != -1 && (Boolean)this.restore.get() && this.enemyNearby();
               this.safeTicks = unsafe ? 0 : Math.min(this.safeTicks + 1, (Integer)this.restoreDelay.get());
               if (danger) {
                  this.clock.forget(SAFE);
                  if (holdingTotem) {
                     this.clock.forget(NEED);
                     this.reach.reset();
                  } else {
                     int slot = this.totemSlot();
                     if (slot == -1) {
                        this.clock.forget(NEED);
                        this.reach.reset();
                     } else {
                        if (this.clock.ready(NEED)) {
                           this.equip(slot);
                        }
                     }
                  }
               } else {
                  this.clock.forget(NEED);
                  if ((Boolean)this.restore.get() && !unsafe && this.originSlot != -1 && (holdingTotem || this.mc.field_1724.method_6079().method_7960())) {
                     if (this.clock.ready(SAFE) && this.safeTicks >= (Integer)this.restoreDelay.get()) {
                        this.restoreOffhand();
                     }
                  } else {
                     this.clock.forget(SAFE);
                     this.reach.reset();
                  }
               }
            } else {
               this.safeTicks = 0;
               this.idle();
            }
         } else {
            this.reach.reset();
         }
      }
   }

   private void idle() {
      this.clock.forget(NEED);
      this.clock.forget(SAFE);
      this.reach.reset();
   }

   private int totemSlot() {
      class_1661 inventory = this.mc.field_1724.method_31548();
      return TotemRules.totemSource(i -> inventory.method_5438(i).method_31574(class_1802.field_8288), inventory.method_67532());
   }

   private void equip(int slot) {
      boolean reserve = this.originSlot == -1 && !this.mc.field_1724.method_6079().method_7960();
      class_1799 offhand = this.mc.field_1724.method_6079().method_7972();
      boolean keys = slot <= 8 && !this.inventoryOpen();
      if (keys) {
         if (!this.startSwap(slot)) {
            return;
         }
      } else {
         if (!this.inventoryReached(slot) || !InventoryGuard.swapToOffhand(slot)) {
            return;
         }

         this.reach.reset();
      }

      if (reserve) {
         this.originSlot = slot;
         this.previousOffhand = offhand;
      }

      if ((Boolean)this.chatInfo.get()) {
         this.info(keys ? "Totem swapped in (swap-hands)." : "Totem swapped in.", new Object[0]);
      }
   }

   private boolean inventoryOpen() {
      return this.mc.field_1755 instanceof class_490;
   }

   private void restoreOffhand() {
      if (!this.mc.field_1724.method_6115()) {
         if (this.originSlot <= 8 && !this.inventoryOpen()) {
            if (!this.startSwap(this.originSlot)) {
               return;
            }
         } else {
            if (!this.inventoryReached(this.originSlot) || !InventoryGuard.swapToOffhand(this.originSlot)) {
               return;
            }

            this.reach.reset();
         }

         this.originSlot = -1;
         this.previousOffhand = class_1799.field_8037;
         if ((Boolean)this.chatInfo.get()) {
            this.info("Offhand restored.", new Object[0]);
         }
      }
   }

   private boolean inventoryReached(int slot) {
      return this.reach.advance(slot, inventoryCouldBeOpen(2), () -> Stealth.pace(6));
   }

   static boolean inventoryCouldBeOpen(int quietTicks) {
      class_310 mc = MeteorClient.mc;
      if (!Stealth.allowsInventoryClick()) {
         return false;
      } else if (mc.field_1755 instanceof class_490) {
         return true;
      } else if (mc.field_1761 == null || mc.field_1761.method_2923()) {
         return false;
      } else {
         return !mc.field_1690.field_1886.method_1434() && !mc.field_1690.field_1904.method_1434() ? ClickGate.quietFor(quietTicks) : false;
      }
   }

   public int pendingReselect() {
      if (this.isActive() && this.mc.field_1724 != null && this.reselectSlot >= 0) {
         return stillOn(this.swapSlot) ? this.reselectSlot : -1;
      } else {
         return -1;
      }
   }

   static boolean stillOn(int slot) {
      class_310 mc = MeteorClient.mc;
      return mc.field_1724 != null && slot >= 0 ? SmartTotem.Slots.stillOn(mc.field_1724.method_31548().method_67532(), HotbarSwap.homeSlot(), slot) : false;
   }

   private boolean startSwap(int slot) {
      int selected = this.mc.field_1724.method_31548().method_67532();
      AutoBlock block = (AutoBlock)Modules.get().get(AutoBlock.class);
      int blockBack = block == null ? -1 : block.pendingReturn();
      int home = blockBack >= 0 ? blockBack : HotbarSwap.homeSlot();
      class_1799 incoming = this.mc.field_1724.method_31548().method_5438(slot).method_7972();
      if (!InventoryGuard.swapWithOffhand(slot)) {
         return false;
      } else {
         if (slot != selected && this.reselectSlot == -1) {
            this.reselectSlot = home;
         }

         this.swapSlot = slot;
         this.reselectAt = -1;
         this.expectedOffhand = incoming;
         this.swapPendingUntil = this.mc.field_1724.field_6012 + CrystalUtils.confirmTicks(EntityUtils.getPing(this.mc.field_1724));
         return true;
      }
   }

   private void reselect() {
      if (!stillOn(this.swapSlot)) {
         this.clearReselect();
      } else if (!this.mc.field_1724.method_6115()) {
         if (HotbarSwap.select(this.reselectSlot)) {
            this.clearReselect();
         }
      }
   }

   private void clearReselect() {
      this.reselectSlot = -1;
      this.reselectAt = -1;
      this.swapSlot = -1;
   }

   private boolean enemyNearby() {
      double range = (Double)this.restoreEnemyRange.get();
      if (range <= 0.0) {
         return false;
      } else {
         double rangeSq = range * range;

         for (class_1657 player : this.mc.field_1687.method_18456()) {
            if (player != this.mc.field_1724
               && !player.method_7325()
               && Friends.get().shouldAttack(player)
               && !(this.mc.field_1724.method_5858(player) > rangeSq)
               && (player.method_5805() || RevivedPlayers.isRevived(player))) {
               return true;
            }
         }

         return false;
      }
   }

   private float predictedDamage() {
      float damage = 0.0F;
      if ((Boolean)this.predictCrystals.get()) {
         double rangeSq = (Double)this.predictRange.get() * (Double)this.predictRange.get();

         for (class_1297 entity : this.mc.field_1687.method_18112()) {
            if (entity instanceof class_1511 crystal && !crystal.method_31481() && !(this.mc.field_1724.method_5858(crystal) > rangeSq)) {
               damage = Math.max(damage, DamageUtils.crystalDamage(this.mc.field_1724, crystal.method_73189()));
            }
         }
      }

      if ((Boolean)this.predictFall.get()) {
         damage += DamageUtils.fallDamage(this.mc.field_1724);
      }

      return damage;
   }

   static final class Slots {
      private Slots() {
      }

      static boolean stillOn(int selected, int home, int slot) {
         return slot >= 0 && (selected == slot || home == slot);
      }
   }
}
