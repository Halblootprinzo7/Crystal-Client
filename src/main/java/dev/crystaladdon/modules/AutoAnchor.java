package dev.crystaladdon.modules;

import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.CrystalModule;
import dev.crystaladdon.utils.ActionBudget;
import dev.crystaladdon.utils.AimUtils;
import dev.crystaladdon.utils.AnchorActions;
import dev.crystaladdon.utils.AnchorSequence;
import dev.crystaladdon.utils.BlastShield;
import dev.crystaladdon.utils.InventoryGuard;
import dev.crystaladdon.utils.RevivedPlayers;
import dev.crystaladdon.utils.TurnProgress;
import dev.crystaladdon.utils.VanillaLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Sent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1750;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1934;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_3965;
import net.minecraft.class_640;

public class AutoAnchor extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAutomation = this.settings.createGroup("Automation");
   private final SettingGroup sgDamage = this.settings.createGroup("Damage");
   private final SettingGroup sgReliability = this.settings.createGroup("Reliability");
   private final SettingGroup sgAim = this.settings.createGroup("Aim");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> targetRange = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("target-range")).description("Only players this close are considered targets."))
            .defaultValue(12.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("priority"))
                  .description("How to pick a target when several are in range."))
               .defaultValue(SortPriority.LowestHealth))
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("range"))
               .description("Maximum distance to act on an anchor. Never further than the server's block interaction range, whatever this says."))
            .defaultValue(4.5)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Double> wallRange = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("wall-range")).description("Range when there is a block in the way."))
            .defaultValue(4.0)
            .min(0.0)
            .sliderMax(6.0)
            .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("speed"))
               .description(
                  "Clicks per second across the whole cycle, at an irregular spacing. Two clicks of one cycle are at least two ticks apart regardless, and Stealth's max-actions-per-second caps all modules together."
               ))
            .defaultValue(10.0)
            .range(1.0, 20.0)
            .sliderRange(1.0, 20.0)
            .build()
      );
   private final Setting<AutoAnchor.SwitchMode> switchMode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("switch-mode"))
                  .description(
                     "Hotbar really moves your selection onto the item - the number key ahead of the click, while the head still turns or the pace runs out. Silent goes back to your slot once a few ticks have passed without a click. With Stealth's same-tick-switch off a slot has to stand a tick before it clicks, so a Silent click then comes a tick after the switch."
                  ))
               .defaultValue(AutoAnchor.SwitchMode.Hotbar))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description(
                     "Face the anchor before acting. Off, a step only goes out when the rotation the server already has lands on the right face. Ignored while aim mode is Crosshair."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> swing = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("swing"))
                  .description("Render the hand swing client side."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> oneClick = this.sgAutomation
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("one-click"))
                  .description("You place the anchor, the module charges it and sets it off. Stops placing on its own and no longer needs an enemy in range."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> autoPlace = this.sgAutomation
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("auto-place"))
                     .description("Place anchors on good spots."))
                  .defaultValue(true))
               .visible(() -> !(Boolean)this.oneClick.get()))
            .build()
      );
   private final Setting<Boolean> autoCharge = this.sgAutomation
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-charge"))
                  .description("Charge uncharged anchors with glowstone."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> autoDetonate = this.sgAutomation
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-detonate"))
                  .description("Set charged anchors off automatically. Off leaves them standing, ready for you or Anchor Macro."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> minDamage = this.sgDamage
      .add(
         ((Builder)((Builder)new Builder().name("min-damage")).description("Do not act unless the anchor deals at least this much to the target."))
            .defaultValue(6.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Double> maxSelfDamage = this.sgDamage
      .add(
         ((Builder)((Builder)new Builder().name("max-self-damage")).description("Do not act on an anchor that would deal more than this to you."))
            .defaultValue(7.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Double> selfDamageWeight = this.sgDamage
      .add(
         ((Builder)((Builder)new Builder().name("self-damage-weight"))
               .description("How hard self damage counts when ranking spots. 0 picks the hardest hit regardless of what it costs you."))
            .defaultValue(1.0)
            .min(0.0)
            .sliderMax(4.0)
            .build()
      );
   private final Setting<Double> minDamageRatio = this.sgDamage
      .add(
         ((Builder)((Builder)new Builder().name("min-damage-ratio"))
               .description("Target damage must be at least this many times your own. 0 disables the check."))
            .defaultValue(1.5)
            .min(0.0)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<Double> maxTargetDistance = this.sgDamage
      .add(
         ((Builder)((Builder)new Builder().name("max-target-distance"))
               .description("Anchor has to be this close to the target. Keeps it off spots that only happen to clip them."))
            .defaultValue(3.5)
            .min(0.0)
            .sliderMax(8.0)
            .build()
      );
   private final Setting<Boolean> facePlace = this.sgDamage
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("face-place"))
                  .description("Ignore the damage requirement once the target is low."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> facePlaceHealth = this.sgDamage
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("face-place-health")).description("Target health below which face placing kicks in."))
               .defaultValue(8.0)
               .min(0.0)
               .sliderMax(36.0)
               .visible(this.facePlace::get))
            .build()
      );
   private final Setting<Double> facePlaceMinDamage = this.sgDamage
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("face-place-min-damage")).description("Damage requirement while face placing."))
               .defaultValue(1.5)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.facePlace::get))
            .build()
      );
   private final Setting<Boolean> predictSteps = this.sgReliability
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-steps"))
                  .description(
                     "Move on from place and charge as soon as the click is sent, instead of waiting for the server to show it. Without this the cycle costs three round trips - three times your ping on top of its clicks - which is what makes anchors feel dead on a laggy server. The server handles packets in order, so the steps still land in order. Either way the clicks are at least two ticks apart."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> timeout = this.sgReliability
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("confirm-timeout"))
                     .description("Ticks to wait for the server to confirm a step before giving up on the spot. Only used when predict-steps is off."))
                  .defaultValue(10))
               .min(1)
               .sliderMax(40)
               .visible(() -> !(Boolean)this.predictSteps.get()))
            .build()
      );
   private final Setting<Boolean> requireGlowstone = this.sgReliability
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("require-glowstone"))
                  .description("Do not place an anchor unless glowstone is available to charge it. Stops wasting anchors."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> autoRefill = this.sgReliability
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-refill"))
                  .description(
                     "Pull anchors and glowstone from your inventory into a free hotbar slot - one swap click, only while you stand still and once a hand could have opened the inventory and pointed at the stack, with a pause before the next click."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> netherGuard = this.sgReliability
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("nether-guard"))
                  .description("Stay off in the Nether, where an anchor sets your spawn instead of exploding."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgReliability
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Report why a spot was abandoned."))
               .defaultValue(false))
            .build()
      );
   private final Setting<AutoAnchor.AimMode> aimMode = this.sgAim
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("aim-mode"))
                  .description(
                     "Fov only acts on anchors that are actually on your screen. Crosshair narrows that to the one block you point at. Off still keeps to what lies in front of you - Stealth's view-angle holds for every module."
                  ))
               .defaultValue(AutoAnchor.AimMode.Off))
            .build()
      );
   private final Setting<Double> maxAngle = this.sgAim
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("max-angle"))
                  .description("Half angle of the cone around your view direction. Never wider than Stealth's view-angle, which holds for every module."))
               .defaultValue(45.0)
               .min(1.0)
               .sliderRange(5.0, 180.0)
               .visible(() -> this.aimMode.get() == AutoAnchor.AimMode.Angle))
            .build()
      );
   private final Setting<Double> fovMargin = this.sgAim
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("fov-margin"))
                  .description("Degrees shaved off each screen edge, so anchors right at the border do not count."))
               .defaultValue(5.0)
               .min(0.0)
               .sliderMax(30.0)
               .visible(() -> this.aimMode.get() == AutoAnchor.AimMode.Fov))
            .build()
      );
   private final Setting<Boolean> render = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("render"))
                  .description("Draw the anchor position being worked on."))
               .defaultValue(true))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("shape-mode"))
                     .description("How the shape is rendered."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("side-color"))
                  .description("Side colour."))
               .defaultValue(new SettingColor(255, 100, 40, 40))
               .visible(this.render::get))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("line-color"))
                  .description("Line colour."))
               .defaultValue(new SettingColor(255, 100, 40, 180))
               .visible(this.render::get))
            .build()
      );
   private class_1657 target;
   private final AnchorActions.Sightings sightings = new AnchorActions.Sightings();
   private final AnchorSequence sequence = new AnchorSequence(this.sightings);
   private final ActionBudget budget = new ActionBudget();
   private final AnchorActions.Refill refill = new AnchorActions.Refill();
   private double nextCost = Double.NaN;
   private class_2338 sightDropped;

   public AutoAnchor() {
      super(CrystalAddon.CATEGORY, "auto-anchor", "Fully automatic respawn anchor aura with confirmed steps.");
   }

   public void onDeactivate() {
      this.target = null;
      this.sequence.cancel();
      this.budget.reset();
      this.sightings.clear();
      this.refill.reset();
      this.sightDropped = null;
      AnchorActions.resetTurn(this);
   }

   @EventHandler
   private void onSent(Sent event) {
      if (this.mc.method_18854()) {
         this.sightings.onSent(event.packet);
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.isActive()) {
         if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
            if (this.sessionChanged()) {
               this.onDeactivate();
               this.sequence.reset();
            }

            this.sequence.tick();
            this.sightings.tick();
            if (Stealth.paused()) {
               AnchorActions.resetTurn(this);
            } else {
               this.budget.update((Double)this.speed.get(), 1);
               if ((Boolean)this.netherGuard.get() && this.mc.field_1687.method_27983() == class_1937.field_25180) {
                  this.sequence.cancel();
               } else if (!Stealth.handsBusy()) {
                  if (this.placingAllowed() || (Boolean)this.autoCharge.get() || (Boolean)this.autoDetonate.get()) {
                     if ((Boolean)this.autoRefill.get()) {
                        this.refill.tick(class_1802.field_23141, class_1802.field_8801);
                     }

                     this.target = this.findTarget();
                     if (this.target == null && !(Boolean)this.oneClick.get()) {
                        this.sequence.cancel();
                     } else if (!InventoryGuard.offhandInFlight()) {
                        if (!this.sequence.isRunning()) {
                           class_2338 spot = this.findSpot();
                           if (spot != null) {
                              this.sequence.start(spot);
                              this.advance(false);
                           }
                        } else if (!this.stillWorthIt(this.sequence.pos())) {
                           class_2338 dropped = this.sequence.pos();
                           if ((Boolean)this.chatInfo.get() && !Stealth.inView(dropped.method_46558()) && !dropped.equals(this.sightDropped)) {
                              this.warning("Anchor spot dropped: out of sight - it left Stealth's view-angle.", new Object[0]);
                              this.sightDropped = dropped;
                           }

                           this.sequence.cancel();
                        } else {
                           boolean worthIt = this.sightings.worth(this.sequence.pos());
                           this.advance(!worthIt);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private class_1657 findTarget() {
      double reach = this.maxTargetDistance.get() > 0.0
         ? Math.min((Double)this.targetRange.get(), this.reach() + (Double)this.maxTargetDistance.get() + 2.0)
         : (Double)this.targetRange.get();
      class_1657 found = TargetUtils.getPlayerTarget(reach, (SortPriority)this.priority.get());
      if (!TargetUtils.isBadTarget(found, reach)) {
         return found;
      } else {
         return TargetUtils.get(
               entity -> entity instanceof class_1657 playerx
                  && playerx != this.mc.field_1724
                  && RevivedPlayers.isRevived(playerx)
                  && Friends.get().shouldAttack(playerx)
                  && EntityUtils.getGameMode(playerx) == class_1934.field_9215
                  && PlayerUtils.isWithin(playerx, reach),
               (SortPriority)this.priority.get()
            ) instanceof class_1657 player
            ? player
            : null;
      }
   }

   private int blastWindow() {
      int ping = 0;
      if (this.mc.method_1562() != null) {
         class_640 entry = this.mc.method_1562().method_2871(this.mc.field_1724.method_5667());
         if (entry != null) {
            ping = entry.method_2959();
         }
      }

      return class_3532.method_15340(ping / 50 + 3, 3, 40);
   }

   private void advance(boolean hold) {
      boolean mustRotate = this.mustRotate() && !TurnProgress.cameraNeeded();
      BooleanSupplier ready = () -> this.isActive() && this.budget.canAfford(this.clickCost());
      BooleanSupplier gate = () -> {
         if (!this.isActive()) {
            return false;
         } else {
            double cost = this.clickCost();
            if (!this.budget.canAfford(cost)) {
               return false;
            } else if (!Stealth.claimUse()) {
               return false;
            } else if (!this.budget.tryConsume(cost)) {
               return false;
            } else {
               this.nextCost = Double.NaN;
               return true;
            }
         }
      };
      AnchorActions.Options options = new AnchorActions.Options(
            mustRotate, (Boolean)this.swing.get(), this.switchMode.get() == AutoAnchor.SwitchMode.Hotbar, 50, gate
         )
         .withOwner(this)
         .withReady(ready);
      AnchorSequence.Step step = this.sequence.step(options, (Integer)this.timeout.get(), this.phases(), (Boolean)this.predictSteps.get(), hold);
      if (step == AnchorSequence.Step.Failed && (Boolean)this.chatInfo.get()) {
         this.warning("Anchor spot dropped: %s.", new Object[]{this.sequence.failure()});
      }
   }

   private double clickCost() {
      if (Double.isNaN(this.nextCost)) {
         this.nextCost = Stealth.actionCost();
      }

      return this.nextCost;
   }

   private boolean placingAllowed() {
      return (Boolean)this.autoPlace.get() && !(Boolean)this.oneClick.get();
   }

   private AnchorSequence.Phases phases() {
      return new AnchorSequence.Phases(this.placingAllowed(), (Boolean)this.autoCharge.get(), (Boolean)this.autoDetonate.get());
   }

   private boolean actionable(class_2338 pos) {
      int charges = AnchorActions.charges(pos);
      boolean glowstone = AnchorActions.findGlowstone().found();
      if (charges < 0) {
         return this.placingAllowed() && AnchorActions.findAnchor().found() && (glowstone || !(Boolean)this.requireGlowstone.get());
      } else {
         return charges == 0
            ? (Boolean)this.autoCharge.get() && glowstone
            : (Boolean)this.autoDetonate.get() && (!AnchorActions.offhandBlocksDetonation(charges) || (Boolean)this.autoCharge.get() && glowstone);
      }
   }

   private double requiredDamage() {
      if (this.target == null) {
         return 0.0;
      } else {
         PopWindow pop = this.popWindow();
         if (pop != null) {
            return pop.anchorMinDamage();
         } else {
            boolean low = (Boolean)this.facePlace.get()
               && this.target.method_6032() > 0.0F
               && EntityUtils.getTotalHealth(this.target) <= (Double)this.facePlaceHealth.get();
            return low ? (Double)this.facePlaceMinDamage.get() : (Double)this.minDamage.get();
         }
      }
   }

   private PopWindow popWindow() {
      PopWindow module = (PopWindow)Modules.get().get(PopWindow.class);
      return module != null && module.isOpenFor(this.target) ? module : null;
   }

   private boolean popOpen() {
      return this.popWindow() != null;
   }

   private boolean stillWorthIt(class_2338 pos) {
      if (pos == null) {
         return false;
      } else {
         return this.target == null && !this.oneClick.get()
            ? false
            : this.score(pos, this.requiredDamage(), EntityUtils.getTotalHealth(this.mc.field_1724)) != null && this.clickable(pos);
      }
   }

   private boolean clickable(class_2338 pos) {
      return AnchorActions.charges(pos) >= 0 ? true : AnchorActions.placeHit(pos, this.mustRotate()) != null;
   }

   private double reach() {
      return Math.min((Double)this.range.get(), VanillaLimits.blockRange());
   }

   private boolean mustRotate() {
      return ((Boolean)this.rotate.get() || Stealth.legitPlace()) && this.aimMode.get() != AutoAnchor.AimMode.Crosshair;
   }

   private Double score(class_2338 pos, double required, double selfHealth) {
      class_243 center = pos.method_46558();
      double distance = this.mc.field_1724.method_33571().method_1022(center);
      if (distance > this.reach()) {
         return null;
      } else if (this.target != null
         && (Double)this.maxTargetDistance.get() > 0.0
         && this.target.method_33571().method_1022(center) > (Double)this.maxTargetDistance.get()) {
         return null;
      } else if (AnchorActions.charges(pos) < 0 && !AnchorActions.canPlace(pos)) {
         return null;
      } else if (!this.actionable(pos)) {
         return null;
      } else if (this.aimMode.get() == AutoAnchor.AimMode.Angle && !AimUtils.withinCone(center, (Double)this.maxAngle.get())) {
         return null;
      } else if (this.aimMode.get() == AutoAnchor.AimMode.Fov && !AimUtils.inFieldOfView(center, (Double)this.fovMargin.get())) {
         return null;
      } else if (distance > Math.min((Double)this.wallRange.get(), this.reach()) && !VanillaLimits.hasLineOfSight(center)) {
         return null;
      } else if (!Stealth.allowsBlock(pos, center)) {
         return null;
      } else {
         float damage = this.target == null ? 0.0F : BlastShield.anchorDamage(this.target, center);
         if (this.target != null && damage < required) {
            return null;
         } else {
            float selfDamage = BlastShield.anchorDamage(this.mc.field_1724, center);
            if (selfDamage > (Double)this.maxSelfDamage.get() || selfDamage >= selfHealth) {
               return null;
            } else if (this.target == null) {
               return (double)(-selfDamage);
            } else {
               PopWindow pop = this.popWindow();
               boolean skipRatio = pop != null && pop.ignoreRatio();
               return !skipRatio && this.minDamageRatio.get() > 0.0 && selfDamage > 0.0F && damage < selfDamage * this.minDamageRatio.get()
                  ? null
                  : damage - selfDamage * (Double)this.selfDamageWeight.get();
            }
         }
      }
   }

   private class_2338 findSpot() {
      double required = this.requiredDamage();
      double selfHealth = EntityUtils.getTotalHealth(this.mc.field_1724);
      int blastWindow = this.blastWindow();
      if (this.aimMode.get() == AutoAnchor.AimMode.Crosshair) {
         class_3965 hit = AimUtils.lookingAtBlock(this.reach());
         if (hit == null) {
            return null;
         } else {
            class_2338 looking = hit.method_17777();
            class_1750 context = new class_1750(this.mc.field_1724, class_1268.field_5808, new class_1799(class_1802.field_23141), hit);
            class_2338 spot = AnchorActions.charges(looking) >= 0 ? looking : context.method_8037();
            if (this.sequence.awaitingBlast(spot, blastWindow)) {
               return null;
            } else {
               return this.score(spot, required, selfHealth) != null && this.sightings.pickable(spot) && this.clickable(spot) ? spot : null;
            }
         }
      } else {
         class_2338 origin = this.mc.field_1724.method_24515();
         int radius = (int)Math.ceil(this.reach());
         List<AutoAnchor.Candidate> candidates = new ArrayList<>();

         for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
               for (int z = -radius; z <= radius; z++) {
                  class_2338 pos = origin.method_10069(x, y, z);
                  if (!this.sequence.awaitingBlast(pos, blastWindow)) {
                     Double value = this.score(pos, required, selfHealth);
                     if (value != null && this.sightings.pickable(pos)) {
                        double targetDistance = this.target != null
                           ? this.target.method_33571().method_1022(pos.method_46558())
                           : this.mc.field_1724.method_33571().method_1022(pos.method_46558());
                        candidates.add(new AutoAnchor.Candidate(pos, value, targetDistance));
                     }
                  }
               }
            }
         }

         candidates.sort(Comparator.comparingDouble(AutoAnchor.Candidate::score).reversed().thenComparingDouble(AutoAnchor.Candidate::targetDistance));

         for (AutoAnchor.Candidate candidate : candidates) {
            if (this.clickable(candidate.pos())) {
               return candidate.pos();
            }
         }

         return null;
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.render.get() && this.sequence.isRunning()) {
         event.renderer.box(this.sequence.pos(), (Color)this.sideColor.get(), (Color)this.lineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
      }
   }

   public String getInfoString() {
      if (this.sequence.isRunning()) {
         return this.popOpen() ? this.sequence.stage() + " pop" : this.sequence.stage().toString();
      } else if (this.target != null) {
         return this.popOpen() ? this.target.method_5477().getString() + " pop" : this.target.method_5477().getString();
      } else {
         return this.oneClick.get() ? "one-click" : null;
      }
   }

   public static enum AimMode {
      Off,
      Angle,
      Fov,
      Crosshair;
   }

   private record Candidate(class_2338 pos, double score, double targetDistance) {
   }

   public static enum SwitchMode {
      Hotbar,
      Silent;
   }
}
