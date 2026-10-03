package dev.crystaladdon.modules;

import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.CrystalModule;
import dev.crystaladdon.utils.HealthLedger;
import dev.crystaladdon.utils.RevivedPlayers;
import java.util.UUID;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1294;
import net.minecraft.class_1657;
import net.minecraft.class_1934;
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
                  .description(
                     "Seconds without the opponent in range before the fight counts as over and gets summarised. A kill or your own death ends it at once."
                  ))
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
   private static final int SWITCH_GRACE_TICKS = 40;
   private UUID opponentId;
   private String opponentName;
   private final HealthLedger opponentLedger = new HealthLedger();
   private final HealthLedger ownLedger = new HealthLedger();
   private float dealt;
   private float taken;
   private int theirPops;
   private int myPops;
   private int missingTicks;
   private long tick;
   private long fightStartTick = -1L;
   private long lastContactTick;

   public FightStats() {
      super(CrystalAddon.CATEGORY, "fight-stats", "Totals up damage, pops and how long a fight took.");
   }

   public void onActivate() {
      this.reset();
   }

   public void onDeactivate() {
      this.endFight();
   }

   private void reset() {
      this.opponentId = null;
      this.opponentName = null;
      this.opponentLedger.clear();
      this.ownLedger.clear();
      this.dealt = this.taken = 0.0F;
      this.theirPops = this.myPops = 0;
      this.missingTicks = 0;
      this.fightStartTick = -1L;
      this.lastContactTick = 0L;
   }

   private void endFight() {
      if (this.isFighting() && (Boolean)this.summary.get()) {
         this.printSummary();
      }

      this.reset();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.reset();
   }

   private boolean isFighting() {
      return this.opponentId != null && this.fightStartTick >= 0L;
   }

   private void contact() {
      if (this.fightStartTick < 0L) {
         this.fightStartTick = this.tick;
      }

      this.lastContactTick = this.tick;
   }

   private void bookDealt(float amount) {
      if (!(amount <= 0.0F)) {
         this.dealt += amount;
         this.contact();
      }
   }

   private void bookTaken(float amount) {
      if (!(amount <= 0.0F)) {
         this.taken += amount;
         this.contact();
      }
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         this.tick++;
         if (this.sessionChanged()) {
            this.endFight();
         }

         if (this.opponentId != null) {
            this.bookTaken(
               this.ownLedger
                  .sample(this.mc.field_1724.method_6032(), this.mc.field_1724.method_6067(), !this.mc.field_1724.method_6059(class_1294.field_5898))
            );
         }

         if (!this.mc.field_1724.method_29504() && !(this.mc.field_1724.method_6032() <= 0.0F)) {
            class_1657 current = this.opponentId == null ? null : this.mc.field_1687.method_18470(this.opponentId);
            if (current != null && (current.method_29504() || current.method_6032() <= 0.0F) && !RevivedPlayers.isRevived(current)) {
               this.bookDealt(this.opponentLedger.sample(current.method_6032(), current.method_6067(), true));
               this.endFight();
               current = null;
            }

            if (current != null && !this.stillOpponent(current)) {
               current = null;
            }

            if (current != null) {
               this.missingTicks = 0;
               this.bookDealt(this.opponentLedger.sample(current.method_6032(), current.method_6067(), true));
            } else {
               class_1657 candidate = TargetInfo.findPlayerTarget((Double)this.range.get(), SortPriority.LowestDistance);
               if (this.opponentId != null) {
                  this.missingTicks++;
                  boolean replace = candidate != null && this.missingTicks > 40;
                  if (!replace && this.missingTicks <= (Integer)this.fightTimeout.get() * 20) {
                     return;
                  }

                  this.endFight();
               }

               if (candidate != null) {
                  this.opponentId = candidate.method_5667();
                  this.opponentName = candidate.method_5477().getString();
                  this.opponentLedger.sample(candidate.method_6032(), candidate.method_6067(), true);
                  this.ownLedger.sample(this.mc.field_1724.method_6032(), this.mc.field_1724.method_6067(), true);
               }
            }
         } else {
            this.endFight();
         }
      }
   }

   private boolean stillOpponent(class_1657 player) {
      if (!PlayerUtils.isWithin(player, (Double)this.range.get())) {
         return false;
      } else if (player.method_6032() <= 0.0F && !RevivedPlayers.isRevived(player)) {
         return false;
      } else if (!Friends.get().shouldAttack(player)) {
         return false;
      } else if (player instanceof FakePlayerEntity fakePlayer) {
         return !fakePlayer.noHit;
      } else {
         class_1934 mode = EntityUtils.getGameMode(player);
         return mode == null || mode == class_1934.field_9215;
      }
   }

   @EventHandler
   private void onPacket(Receive event) {
      if (event.packet instanceof class_2663 packet) {
         byte status = packet.method_11470();
         if (status == 35 || status == 3) {
            this.receiveOnClient(event, () -> this.onStatus(packet, status));
         }
      }
   }

   private void onStatus(class_2663 packet, byte status) {
      if (this.opponentId != null) {
         if (packet.method_11469(this.mc.field_1687) instanceof class_1657 player) {
            boolean self = player == this.mc.field_1724;
            if (self || player.method_5667().equals(this.opponentId)) {
               HealthLedger ledger = self ? this.ownLedger : this.opponentLedger;
               float health = player.method_6032();
               float absorption = player.method_6067();
               if (status == 35) {
                  float hit = ledger.pop(health, absorption);
                  if (self) {
                     this.myPops++;
                     this.taken += hit;
                  } else {
                     this.theirPops++;
                     this.dealt += hit;
                  }

                  this.contact();
               } else {
                  float hit = ledger.death(health, absorption);
                  if (self) {
                     this.bookTaken(hit);
                  } else {
                     this.bookDealt(hit);
                  }

                  this.endFight();
               }
            }
         }
      }
   }

   private void printSummary() {
      String name = this.opponentName != null ? this.opponentName : "opponent";
      double seconds = (this.lastContactTick - this.fightStartTick) / 20.0;
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
