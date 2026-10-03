package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.ActionBudget;
import com.messerocks.crystal.utils.AimUtils;
import com.messerocks.crystal.utils.AnchorActions;
import com.messerocks.crystal.utils.AnchorSequence;
import com.messerocks.crystal.utils.BlastShield;
import com.messerocks.crystal.utils.CrystalUtils;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_3965;

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
         ((Builder)((Builder)new Builder().name("range")).description("Maximum distance to act on an anchor."))
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
         ((Builder)((Builder)new Builder().name("speed")).description("Actions per second across the whole cycle. 0 is unlimited."))
            .defaultValue(10.0)
            .min(0.0)
            .sliderMax(40.0)
            .build()
      );
   private final Setting<AutoAnchor.SwitchMode> switchMode = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("switch-mode"))
                  .description("Hotbar really moves your selection onto the item. Silent swaps back within the tick."))
               .defaultValue(AutoAnchor.SwitchMode.Hotbar))
            .build()
      );
   private final Setting<Boolean> rotate = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rotate"))
                  .description("Face the anchor before acting. Ignored while aim mode is Crosshair."))
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
   private final Setting<Boolean> pauseOnUse = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("pause-on-use"))
                  .description("Stop while eating or drinking."))
               .defaultValue(true))
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
                     "Move on from place and charge as soon as the click is sent, instead of waiting for the server to show it. Without this the cycle costs three round trips - three times your ping on top of its three ticks - which is what makes anchors feel dead on a laggy server. The server handles packets in order, so the steps still land in order."
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
                  .description("Pull anchors and glowstone from your inventory into a free hotbar slot."))
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
                  .description("Fov only acts on anchors that are actually on your screen. Crosshair narrows that to the one block you point at."))
               .defaultValue(AutoAnchor.AimMode.Off))
            .build()
      );
   private final Setting<Double> maxAngle = this.sgAim
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("max-angle")).description("Half angle of the cone around your view direction."))
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
   private final AnchorSequence sequence = new AnchorSequence();
   private final ActionBudget budget = new ActionBudget();

   public AutoAnchor() {
      super(CrystalAddon.CATEGORY, "auto-anchor", "Fully automatic respawn anchor aura with confirmed steps.");
   }

   public void onDeactivate() {
      this.target = null;
      this.sequence.cancel();
      this.budget.reset();
      AnchorActions.resetTurn();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.mc.field_1761 != null) {
         this.budget.update((Double)this.speed.get(), 1);
         if ((Boolean)this.netherGuard.get() && this.mc.field_1687.method_27983() == class_1937.field_25180) {
            this.sequence.cancel();
         } else if (!(Boolean)this.pauseOnUse.get() || !this.mc.field_1724.method_6115()) {
            if (this.placingAllowed() || (Boolean)this.autoCharge.get() || (Boolean)this.autoDetonate.get()) {
               if ((Boolean)this.autoRefill.get() && !AnchorActions.refillHotbar(class_1802.field_23141)) {
                  AnchorActions.refillHotbar(class_1802.field_8801);
               }

               this.target = TargetUtils.getPlayerTarget((Double)this.targetRange.get(), (SortPriority)this.priority.get());
               if (TargetUtils.isBadTarget(this.target, (Double)this.targetRange.get())) {
                  this.target = null;
                  if (!(Boolean)this.oneClick.get()) {
                     this.sequence.cancel();
                     return;
                  }
               }

               if (!this.sequence.isRunning()) {
                  class_2338 spot = this.findSpot();
                  if (spot != null) {
                     if (!(Boolean)this.requireGlowstone.get() || AnchorActions.charges(spot) >= 0 || AnchorActions.findGlowstone().found()) {
                        this.sequence.start(spot);
                        this.advance();
                     }
                  }
               } else if (!this.stillWorthIt(this.sequence.pos())) {
                  this.sequence.cancel();
               } else {
                  this.advance();
               }
            }
         }
      }
   }

   private void advance() {
      boolean mustRotate = ((Boolean)this.rotate.get() || Stealth.legitPlace()) && this.aimMode.get() != AutoAnchor.AimMode.Crosshair;
      BooleanSupplier gate = () -> {
         if (!this.budget.canAfford()) {
            return false;
         } else {
            return !Stealth.claimAction() ? false : this.budget.tryConsume();
         }
      };
      AnchorActions.Options options = new AnchorActions.Options(
         mustRotate, (Boolean)this.swing.get(), this.switchMode.get() == AutoAnchor.SwitchMode.Hotbar, 50, gate
      );
      AnchorSequence.Step step = this.sequence.step(options, (Integer)this.timeout.get(), this.phases(), (Boolean)this.predictSteps.get());
      if (step == AnchorSequence.Step.Failed && (Boolean)this.chatInfo.get()) {
         this.warning("Anchor spot dropped: %s.", new Object[]{this.sequence.failure()});
      }
   }

   private boolean placingAllowed() {
      return (Boolean)this.autoPlace.get() && !(Boolean)this.oneClick.get();
   }

   private AnchorSequence.Phases phases() {
      return new AnchorSequence.Phases(this.placingAllowed(), (Boolean)this.autoCharge.get(), (Boolean)this.autoDetonate.get());
   }

   private boolean actionable(class_2338 pos) {
      int charges = AnchorActions.charges(pos);
      if (charges < 0) {
         return this.placingAllowed();
      } else {
         return charges == 0 ? (Boolean)this.autoCharge.get() : (Boolean)this.autoDetonate.get();
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
            boolean low = (Boolean)this.facePlace.get() && EntityUtils.getTotalHealth(this.target) <= (Double)this.facePlaceHealth.get();
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
      return Stealth.legitPlace() && AnchorActions.charges(pos) < 0 ? AnchorActions.placeHit(pos) != null : true;
   }

   private Double score(class_2338 pos, double required, double selfHealth) {
      class_243 center = pos.method_46558();
      if (!CrystalUtils.inRange(center, (Double)this.range.get(), (Double)this.wallRange.get())) {
         return null;
      } else if (!Stealth.allowsBlock(pos, center)) {
         return null;
      } else if (this.aimMode.get() == AutoAnchor.AimMode.Angle && !AimUtils.withinCone(center, (Double)this.maxAngle.get())) {
         return null;
      } else if (this.aimMode.get() == AutoAnchor.AimMode.Fov && !AimUtils.inFieldOfView(center, (Double)this.fovMargin.get())) {
         return null;
      } else if (this.target != null
         && (Double)this.maxTargetDistance.get() > 0.0
         && this.target.method_33571().method_1022(center) > (Double)this.maxTargetDistance.get()) {
         return null;
      } else if (AnchorActions.charges(pos) < 0 && !AnchorActions.canPlace(pos)) {
         return null;
      } else if (!this.actionable(pos)) {
         return null;
      } else {
         float selfDamage = BlastShield.anchorDamage(this.mc.field_1724, center);
         if (selfDamage > (Double)this.maxSelfDamage.get() || selfDamage >= selfHealth) {
            return null;
         } else if (this.target == null) {
            return (double)(-selfDamage);
         } else {
            float damage = BlastShield.anchorDamage(this.target, center);
            if (damage < required) {
               return null;
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
      if (this.aimMode.get() == AutoAnchor.AimMode.Crosshair) {
         class_3965 hit = AimUtils.lookingAtBlock((Double)this.range.get());
         if (hit == null) {
            return null;
         } else {
            class_2338 looking = hit.method_17777();
            class_2338 spot = AnchorActions.charges(looking) >= 0 ? looking : looking.method_10093(hit.method_17780());
            return this.score(spot, required, selfHealth) != null && this.clickable(spot) ? spot : null;
         }
      } else {
         class_2338 origin = this.mc.field_1724.method_24515();
         int radius = (int)Math.ceil((Double)this.range.get());
         class_2338 best = null;
         double bestScore = Double.NEGATIVE_INFINITY;
         double bestTargetDistance = Double.MAX_VALUE;

         for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
               for (int z = -radius; z <= radius; z++) {
                  class_2338 pos = origin.method_10069(x, y, z);
                  Double value = this.score(pos, required, selfHealth);
                  if (value != null) {
                     double targetDistance = this.target != null
                        ? this.target.method_33571().method_1022(pos.method_46558())
                        : this.mc.field_1724.method_33571().method_1022(pos.method_46558());
                     if ((value > bestScore || value == bestScore && targetDistance < bestTargetDistance) && this.clickable(pos)) {
                        bestScore = value;
                        bestTargetDistance = targetDistance;
                        best = pos;
                     }
                  }
               }
            }
         }

         return best;
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

   public static enum SwitchMode {
      Hotbar,
      Silent;
   }
}
