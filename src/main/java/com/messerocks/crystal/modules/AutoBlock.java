package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.BlastShield;
import com.messerocks.crystal.utils.ComboDetector;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.mixininterface.IMinecraftClient;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_10707;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_243;
import net.minecraft.class_9334;

public class AutoBlock extends CrystalModule {
   private final SettingGroup sgCombo = this.settings.getDefaultGroup();
   private final SettingGroup sgLive = this.settings.createGroup("Live crystal");
   private final SettingGroup sgArm = this.settings.createGroup("Pre-arm");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Integer> comboHits = this.sgCombo
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("combo-hits"))
                  .description(
                     "How many crystals have to go off near you inside the window before it counts as a combo. Two means the shield is up from the second crystal onwards - the first one can never be blocked."
                  ))
               .defaultValue(2))
            .min(1)
            .sliderRange(1, 6)
            .build()
      );
   private final Setting<Integer> comboWindow = this.sgCombo
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("combo-window"))
                  .description("Ticks the pops have to fall within to count as one combo. 40 is two seconds."))
               .defaultValue(40))
            .min(5)
            .sliderRange(10, 100)
            .build()
      );
   private final Setting<Double> comboRange = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("combo-range"))
               .description("How close a crystal has to go off to count as aimed at you."))
            .defaultValue(8.0)
            .min(1.0)
            .sliderRange(2.0, 16.0)
            .build()
      );
   private final Setting<Integer> hold = this.sgCombo
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("hold-after-last-pop"))
                  .description("Ticks to keep blocking after the last crystal went off, so a pause in the rhythm does not catch you mid-lower."))
               .defaultValue(30))
            .min(0)
            .sliderRange(0, 80)
            .build()
      );
   private final Setting<AutoBlock.HandMode> hand = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("hand"))
                  .description("Main hand keeps your totem where it is. Offhand blocks while you keep swinging, but evicts the totem."))
               .defaultValue(AutoBlock.HandMode.MainHand))
            .build()
      );
   private final Setting<Boolean> faceThreat = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("face-the-blast"))
                  .description("Turn towards what is being blocked. A shield only stops what comes from in front, so without this the block is decorative."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgCombo
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Say when the shield goes up and comes down."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> blockLive = this.sgLive
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("block-standing-crystals"))
                  .description(
                     "Also block for a crystal that is standing next to you right now. This is the one chance to catch the first crystal too - it only works when the enemy leaves it up for a few ticks instead of popping it instantly."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> liveRange = this.sgLive
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("live-range"))
                  .description("How far to look for a crystal that is already placed."))
               .defaultValue(8.0)
               .min(1.0)
               .sliderRange(2.0, 16.0)
               .visible(this.blockLive::get))
            .build()
      );
   private final Setting<Double> minDamage = this.sgLive
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("min-damage"))
                  .description("Only block for a standing crystal that would deal at least this much, so a crystal you already have cover from is ignored."))
               .defaultValue(4.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.blockLive::get))
            .build()
      );
   private final Setting<Boolean> preArm = this.sgArm
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("pre-arm"))
                  .description(
                     "Hold the shield up whenever an enemy is close at all, before anything has happened. The fastest possible reaction, at the price of blocking nearly all the time in a fight."
                  ))
               .defaultValue(false))
            .build()
      );
   private final Setting<Double> preArmRange = this.sgArm
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("pre-arm-range"))
                  .description("How close an enemy has to be for pre-arm."))
               .defaultValue(6.0)
               .min(1.0)
               .sliderRange(1.0, 12.0)
               .visible(this.preArm::get))
            .build()
      );
   private final Setting<Boolean> ignoreFriends = this.sgArm
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("ignore-friends"))
                     .description("Do not treat friends as someone who would crystal you."))
                  .defaultValue(true))
               .visible(this.preArm::get))
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render"))
                  .description("Outline what the shield is up for."))
               .defaultValue(true))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("shape-mode"))
                     .description("How the shape is rendered."))
                  .defaultValue(ShapeMode.Lines))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> liveColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("crystal-color"))
                  .description("Outline colour for a crystal that is standing there now."))
               .defaultValue(new SettingColor(80, 180, 255, 200))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> comboColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("combo-color"))
                  .description("Outline colour for where the last crystal of a combo went off."))
               .defaultValue(new SettingColor(255, 90, 90, 180))
               .visible(this.render::get))
            .build()
      );
   private final ComboDetector combo = new ComboDetector();
   private class_1511 live;
   private float liveDamage;
   private boolean comboLatched;
   private boolean armedByEnemy;
   private boolean blocking;
   private int returnSlot = -1;

   public AutoBlock() {
      super(CrystalAddon.CATEGORY, "auto-block", "Raises a shield once it notices you are being crystal comboed.");
   }

   public void onDeactivate() {
      this.stopBlocking();
      this.combo.reset();
      this.live = null;
      this.comboLatched = false;
      this.armedByEnemy = false;
   }

   @EventHandler
   private void onEntityRemoved(EntityRemovedEvent event) {
      if (this.mc.field_1724 != null) {
         if (event.entity instanceof class_1511 crystal) {
            double var5 = (Double)this.comboRange.get();
            if (!(this.mc.field_1724.method_5858(crystal) > var5 * var5)) {
               this.combo.record(crystal.method_73189());
            }
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         this.combo.tick((Integer)this.comboWindow.get());
         if (this.combo.count() >= (Integer)this.comboHits.get()) {
            this.comboLatched = true;
         } else if (this.comboLatched && this.combo.sinceLast() > (Integer)this.hold.get()) {
            this.comboLatched = false;
         }

         this.live = this.blockLive.get() ? this.findLiveThreat() : null;
         this.armedByEnemy = (Boolean)this.preArm.get() && this.nearestEnemy((Double)this.preArmRange.get()) != null;
         if (!this.comboLatched && this.live == null && !this.armedByEnemy) {
            this.stopBlocking();
         } else {
            this.startBlocking();
         }
      }
   }

   private class_1511 findLiveThreat() {
      double rangeSq = (Double)this.liveRange.get() * (Double)this.liveRange.get();
      class_1511 worst = null;
      float worstDamage = 0.0F;

      for (class_1297 entity : this.mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 crystal && !crystal.method_31481() && !(this.mc.field_1724.method_5858(crystal) > rangeSq)) {
            float damage = BlastShield.crystalDamage(this.mc.field_1724, crystal.method_73189());
            if (!(damage < (Double)this.minDamage.get()) && damage > worstDamage) {
               worstDamage = damage;
               worst = crystal;
            }
         }
      }

      this.liveDamage = worstDamage;
      return worst;
   }

   private class_1657 nearestEnemy(double within) {
      double bestSq = within * within;
      class_1657 best = null;

      for (class_1657 player : this.mc.field_1687.method_18456()) {
         if (player != this.mc.field_1724 && player.method_5805() && (!(Boolean)this.ignoreFriends.get() || Friends.get().shouldAttack(player))) {
            double distSq = this.mc.field_1724.method_5858(player);
            if (distSq <= bestSq) {
               bestSq = distSq;
               best = player;
            }
         }
      }

      return best;
   }

   private class_243 facingTarget() {
      if (this.live != null) {
         return this.live.method_73189();
      } else if (this.comboLatched && this.combo.lastPos() != null) {
         return this.combo.lastPos();
      } else {
         class_1657 enemy = this.nearestEnemy((Double)this.preArmRange.get());
         return enemy == null ? null : enemy.method_33571();
      }
   }

   private void startBlocking() {
      FindItemResult shield = InvUtils.find(stack -> stack.method_57826(class_9334.field_56396));
      if (!shield.found()) {
         this.stopBlocking();
      } else if (this.bringToHand(shield)) {
         if ((Boolean)this.faceThreat.get()) {
            class_243 facing = this.facingTarget();
            if (facing != null) {
               Rotations.rotate(Rotations.getYaw(facing), Rotations.getPitch(facing), 100);
            }
         }

         if (!this.blocking) {
            this.blocking = true;
            if ((Boolean)this.chatInfo.get()) {
               if (this.comboLatched) {
                  this.info("Shield up, %d crystals in %d ticks.", new Object[]{this.combo.count(), this.comboWindow.get()});
               } else if (this.live != null) {
                  this.info("Shield up, a crystal next to you is worth %.1f.", new Object[]{this.liveDamage});
               } else {
                  this.info("Shield up, enemy close.", new Object[0]);
               }
            }
         }

         this.mc.field_1690.field_1904.method_23481(true);
         if (!this.mc.field_1724.method_6115()) {
            ((IMinecraftClient)this.mc).meteor$rightClick();
         }
      }
   }

   private boolean bringToHand(FindItemResult shield) {
      if (this.hand.get() == AutoBlock.HandMode.Offhand) {
         if (shield.isOffhand()) {
            return true;
         } else if (this.mc.field_1724.field_7512 != this.mc.field_1724.field_7498) {
            return false;
         } else {
            InvUtils.move().from(shield.slot()).toOffhand();
            return false;
         }
      } else if (shield.isOffhand()) {
         return true;
      } else if (shield.isHotbar()) {
         if (shield.slot() != this.mc.field_1724.method_31548().method_67532()) {
            if (this.returnSlot == -1) {
               this.returnSlot = this.mc.field_1724.method_31548().method_67532();
            }

            InvUtils.swap(shield.slot(), false);
         }

         return true;
      } else if (this.mc.field_1724.field_7512 != this.mc.field_1724.field_7498) {
         return false;
      } else {
         for (int i = 0; i <= 8; i++) {
            if (this.mc.field_1724.method_31548().method_5438(i).method_7960()) {
               InvUtils.move().from(shield.slot()).toHotbar(i);
               return false;
            }
         }

         return false;
      }
   }

   private void stopBlocking() {
      if (this.blocking) {
         this.mc.field_1690.field_1904.method_23481(false);
         this.blocking = false;
         if ((Boolean)this.chatInfo.get()) {
            this.info("Shield down.", new Object[0]);
         }
      }

      if (this.returnSlot != -1 && this.mc.field_1724 != null) {
         InvUtils.swap(this.returnSlot, false);
         this.returnSlot = -1;
      }
   }

   public boolean isBlocking() {
      return this.isActive() && this.blocking;
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get()) {
         if (this.live != null) {
            event.renderer.box(this.live.method_5829(), (Color)this.liveColor.get(), (Color)this.liveColor.get(), (ShapeMode)this.shapeMode.get(), 0);
         } else {
            class_243 last = this.combo.lastPos();
            if (this.comboLatched && last != null) {
               event.renderer
                  .box(
                     last.field_1352 - 0.5,
                     last.field_1351 - 0.5,
                     last.field_1350 - 0.5,
                     last.field_1352 + 0.5,
                     last.field_1351 + 1.5,
                     last.field_1350 + 0.5,
                     (Color)this.comboColor.get(),
                     (Color)this.comboColor.get(),
                     (ShapeMode)this.shapeMode.get(),
                     0
                  );
            }
         }
      }
   }

   public String getInfoString() {
      if (this.comboLatched) {
         return String.format("combo x%d", this.combo.count());
      } else if (this.live == null) {
         return this.blocking ? "holding" : null;
      } else {
         class_1799 shield = this.mc.field_1724 == null ? class_1799.field_8037 : this.mc.field_1724.method_62821();
         class_10707 component = shield == null ? null : (class_10707)shield.method_58694(class_9334.field_56396);
         return component != null && this.mc.field_1724.method_6048() < component.method_67197()
            ? String.format("arming %.1f", this.liveDamage)
            : String.format("%.1f", this.liveDamage);
      }
   }

   public static enum HandMode {
      MainHand,
      Offhand;
   }
}
