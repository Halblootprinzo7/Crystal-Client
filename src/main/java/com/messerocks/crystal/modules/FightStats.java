package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import java.util.UUID;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_2663;

public class FightStats extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("range")).description("How close a player has to be to count as the opponent."))
            .defaultValue(16.0)
            .min(0.0)
            .sliderMax(32.0)
            .build()
      );
   private final Setting<Integer> fightTimeout = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("fight-timeout"))
                  .description("Seconds without an opponent before the fight counts as over and gets summarised."))
               .defaultValue(8))
            .min(1)
            .sliderMax(60)
            .build()
      );
   private final Setting<Boolean> summary = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("summary-in-chat"))
                  .description("Print the totals when a fight ends."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> liveHud = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("live-readout"))
                  .description("Show running totals in the module list while a fight is on."))
               .defaultValue(true))
            .build()
      );
   private class_1657 opponent;
   private UUID opponentId;
   private float lastOpponentHealth = -1.0F;
   private float lastOwnHealth = -1.0F;
   private float dealt;
   private float taken;
   private int theirPops;
   private int myPops;
   private int idleTicks;
   private int fightTicks;

   public FightStats() {
      super(CrystalAddon.CATEGORY, "fight-stats", "Totals up damage, pops and how long a fight took.");
   }

   public void onActivate() {
      this.reset();
   }

   public void onDeactivate() {
      if (this.isFighting() && (Boolean)this.summary.get()) {
         this.printSummary();
      }

      this.reset();
   }

   private void reset() {
      this.opponent = null;
      this.opponentId = null;
      this.lastOpponentHealth = -1.0F;
      this.lastOwnHealth = -1.0F;
      this.dealt = this.taken = 0.0F;
      this.theirPops = this.myPops = 0;
      this.idleTicks = 0;
      this.fightTicks = 0;
   }

   private boolean isFighting() {
      return this.opponentId != null && (this.dealt > 0.0F || this.taken > 0.0F || this.theirPops > 0 || this.myPops > 0);
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         class_1657 nearest = TargetUtils.getPlayerTarget((Double)this.range.get(), SortPriority.LowestDistance);
         if (nearest != null) {
            if (this.opponentId != null && !nearest.method_5667().equals(this.opponentId) && this.isFighting()) {
               if ((Boolean)this.summary.get()) {
                  this.printSummary();
               }

               this.reset();
            }

            this.opponent = nearest;
            this.opponentId = nearest.method_5667();
            this.idleTicks = 0;
            this.fightTicks++;
            this.trackOpponentHealth(nearest);
         } else if (this.opponentId != null && ++this.idleTicks > (Integer)this.fightTimeout.get() * 20) {
            if (this.isFighting() && (Boolean)this.summary.get()) {
               this.printSummary();
            }

            this.reset();
         }

         this.trackOwnHealth();
      }
   }

   private void trackOpponentHealth(class_1657 player) {
      float health = EntityUtils.getTotalHealth(player);
      if (this.lastOpponentHealth >= 0.0F && health < this.lastOpponentHealth) {
         this.dealt = this.dealt + (this.lastOpponentHealth - health);
      }

      this.lastOpponentHealth = health;
   }

   private void trackOwnHealth() {
      float health = EntityUtils.getTotalHealth(this.mc.field_1724);
      if (this.lastOwnHealth >= 0.0F && health < this.lastOwnHealth) {
         this.taken = this.taken + (this.lastOwnHealth - health);
      }

      this.lastOwnHealth = health;
   }

   @EventHandler
   private void onPacket(Receive event) {
      if (event.packet instanceof class_2663 packet) {
         if (packet.method_11470() == 35) {
            if (this.mc.field_1687 != null) {
               if (packet.method_11469(this.mc.field_1687) instanceof class_1657 player) {
                  if (player == this.mc.field_1724) {
                     this.myPops++;
                  } else if (player.method_5667().equals(this.opponentId)) {
                     this.theirPops++;
                  }
               }
            }
         }
      }
   }

   private void printSummary() {
      String name = this.opponent != null ? this.opponent.method_5477().getString() : "opponent";
      double seconds = this.fightTicks / 20.0;
      this.info(
         "Fight with %s over %.0fs: dealt %.1f (%d pops), took %.1f (%d pops).",
         new Object[]{name, seconds, this.dealt, this.theirPops, this.taken, this.myPops}
      );
      if (this.taken > 0.0F) {
         this.info("Trade ratio %.2f to 1.", new Object[]{this.dealt / this.taken});
      }
   }

   public String getInfoString() {
      return this.liveHud.get() && this.isFighting() ? String.format("%.0f/%.0f %d-%d", this.dealt, this.taken, this.theirPops, this.myPops) : null;
   }
}
