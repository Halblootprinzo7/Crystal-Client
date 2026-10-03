package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.CrystalUtils;
import com.messerocks.crystal.utils.DamageWindow;
import com.messerocks.crystal.utils.LegitPlace;
import com.messerocks.crystal.utils.ServerVersion;
import com.messerocks.crystal.utils.VanillaLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.DamageUtils.RaycastFactory;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3726;
import org.joml.Vector3d;

public class CrystalDamageEsp extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDamage = this.settings.createGroup("Damage");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> targetRange = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("target-range")).description("How far away a player can be to be scanned around."))
            .defaultValue(12.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("priority"))
                  .description("Which player to scan around when several are in range."))
               .defaultValue(SortPriority.LowestHealth))
            .build()
      );
   private final Setting<Double> searchRadius = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("search-radius")).description("How far around the target to look for spots."))
            .defaultValue(5.0)
            .min(1.0)
            .sliderMax(8.0)
            .build()
      );
   private final Setting<Double> minDamage = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("min-damage")).description("Hide spots weaker than this.")).defaultValue(4.0).min(0.0).sliderMax(20.0).build()
      );
   private final Setting<Integer> maxSpots = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("max-spots"))
                  .description("How many of the best spots to draw."))
               .defaultValue(5))
            .min(1)
            .sliderMax(20)
            .build()
      );
   private final Setting<Boolean> onlyReachable = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("only-reachable"))
                  .description(
                     "Hide spots you could not place at anyway: out of reach, only reachable through a wall, too far to hit the crystal afterwards, or refused by Stealth's rules while Stealth is on. The same filter Auto Crystal places with."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> reach = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("reach"))
                  .description(
                     "Placement reach - Auto Crystal's place-range. Measured the way the aura measures it: to the nearest point of the obsidian, along a ray that really lands on it, so a spot behind a wall does not count."
                  ))
               .defaultValue(4.5)
               .min(0.0)
               .sliderMax(6.0)
               .visible(this.onlyReachable::get))
            .build()
      );
   private final Setting<Double> hitRange = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("hit-range"))
                  .description(
                     "How far away the crystal may be hit once it stands - Auto Crystal's break-range. With break on, the aura leaves out a spot whose crystal it could not hit afterwards. 0 leaves this check out, for an aura that does not break."
                  ))
               .defaultValue(3.0)
               .min(0.0)
               .sliderMax(6.0)
               .visible(this.onlyReachable::get))
            .build()
      );
   private final Setting<Boolean> vanillaReach = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("vanilla-reach"))
                     .description(
                        "Cap reach at the game's block reach and hit-range at its entity reach, like Auto Crystal's setting of the same name. Turn it off alongside Auto Crystal's to see the spots an extended-reach aura still places at."
                     ))
                  .defaultValue(true))
               .visible(this.onlyReachable::get))
            .build()
      );
   private final Setting<Boolean> showUnreachable = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("show-unreachable"))
                     .description("Still draw spots out of reach, greyed out, so you can see where to walk."))
                  .defaultValue(false))
               .visible(this.onlyReachable::get))
            .build()
      );
   private final Setting<Integer> updateInterval = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("update-interval"))
                  .description("Scan every this many ticks - 1 scans every tick. The scan is the expensive part, so raise this if it costs you frames."))
               .defaultValue(4))
            .min(1)
            .sliderMax(20)
            .build()
      );
   private final Setting<CrystalDamageEsp.Exposure> exposure = this.sgDamage
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("exposure"))
                  .description(
                     "Vanilla: soft blocks (dirt, stone, glowstone) shield the target like on the server, and the number after it in brackets is the next crystal once they are blown away. Meteor: only blast-proof blocks shield."
                  ))
               .defaultValue(CrystalDamageEsp.Exposure.Vanilla))
            .build()
      );
   private final Setting<Boolean> predictMovement = this.sgDamage
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-movement"))
                  .description(
                     "Move the target by the distance it covered last tick before computing damage. Helps against running players, jitters against strafing ones."
                  ))
               .defaultValue(false))
            .build()
      );
   private final Setting<CrystalDamageEsp.SortMode> sort = this.sgDamage
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("sort"))
                  .description("Damage: strongest hit first. Score: damage to the target minus a share of the damage to you, so trades come first."))
               .defaultValue(CrystalDamageEsp.SortMode.Damage))
            .build()
      );
   private final Setting<Double> selfWeight = this.sgDamage
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("self-weight")).description("How much each point of damage to you counts against a spot."))
               .defaultValue(0.5)
               .min(0.0)
               .sliderMax(2.0)
               .visible(() -> this.sort.get() == CrystalDamageEsp.SortMode.Score))
            .build()
      );
   private final Setting<Double> safetyMargin = this.sgDamage
      .add(
         ((Builder)((Builder)new Builder().name("safety-margin"))
               .description("A spot is marked as suicide when it would leave you with less health than this."))
            .defaultValue(2.0)
            .min(0.0)
            .sliderMax(10.0)
            .build()
      );
   private final Setting<Boolean> hideSuicidal = this.sgDamage
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("hide-suicidal"))
                  .description("Leave out spots that would kill you instead of marking them."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> usePopWindow = this.sgDamage
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("use-pop-window"))
                  .description("While Pop Window is open on the target, lower min-damage to Pop Window's crystal minimum."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> showPlaced = this.sgDamage
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-placed"))
                  .description("Also show crystals that are already standing near the target and what breaking them would do."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> showDamage = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-damage"))
                  .description("Draw the damage number at each spot."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> showSelfDamage = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("show-self-damage"))
                     .description("Add what the spot would cost you, as target/self."))
                  .defaultValue(true))
               .visible(this.showDamage::get))
            .build()
      );
   private final Setting<Boolean> showHurtWindow = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-hurt-window"))
                  .description(
                     "While the target is in its damage cooldown, dim the spots and show the ticks left above them - a crystal now only deals what exceeds the last hit."
                  ))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> textScale = this.sgRender
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("text-scale")).description("Size of the damage numbers."))
               .defaultValue(1.0)
               .min(0.1)
               .sliderRange(0.5, 3.0)
               .visible(this.showDamage::get))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("shape-mode"))
                  .description("How the boxes are rendered."))
               .defaultValue(ShapeMode.Lines))
            .build()
      );
   private final Setting<SettingColor> bestColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("best-color"))
               .description("Colour of the strongest spot."))
            .defaultValue(new SettingColor(60, 255, 120, 200))
            .build()
      );
   private final Setting<SettingColor> weakColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("weak-color"))
               .description("Colour of the weakest shown spot. Everything in between is blended."))
            .defaultValue(new SettingColor(255, 200, 60, 120))
            .build()
      );
   private final Setting<SettingColor> lethalColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("lethal-color"))
               .description("Colour for a spot that would kill the target outright."))
            .defaultValue(new SettingColor(255, 60, 60, 220))
            .build()
      );
   private final Setting<SettingColor> popColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("pop-color"))
               .description("Colour for a spot that is lethal but the target holds a totem, so it only pops."))
            .defaultValue(new SettingColor(255, 140, 255, 220))
            .build()
      );
   private final Setting<SettingColor> suicideColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("suicide-color"))
               .description("Colour for a spot that would kill you."))
            .defaultValue(new SettingColor(255, 0, 0, 60))
            .build()
      );
   private final Setting<SettingColor> placedColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("placed-color"))
                  .description("Colour of crystals already standing."))
               .defaultValue(new SettingColor(120, 200, 255, 200))
               .visible(this.showPlaced::get))
            .build()
      );
   private final Setting<SettingColor> unreachableColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("unreachable-color"))
                  .description("Colour of spots out of reach."))
               .defaultValue(new SettingColor(140, 140, 140, 90))
               .visible(() -> (Boolean)this.onlyReachable.get() && (Boolean)this.showUnreachable.get()))
            .build()
      );
   private final List<CrystalDamageEsp.Spot> spots = new ArrayList<>();
   private final List<CrystalDamageEsp.Spot> extras = new ArrayList<>();
   private final Color scratch = new Color();
   private final Vector3d textPos = new Vector3d();
   private class_1657 target;
   private int timer;

   public CrystalDamageEsp() {
      super(CrystalAddon.CATEGORY, "crystal-damage-esp", "Shows the crystal spots around a target and what each would do.");
   }

   public void onDeactivate() {
      this.spots.clear();
      this.extras.clear();
      this.target = null;
      this.timer = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (this.sessionChanged()) {
            this.onDeactivate();
         }

         if (this.timer > 0) {
            this.timer--;
         } else {
            this.timer = (Integer)this.updateInterval.get() - 1;
            this.target = TargetInfo.findPlayerTarget((Double)this.targetRange.get(), (SortPriority)this.priority.get());
            this.spots.clear();
            this.extras.clear();
            if (this.target != null) {
               this.scan();
            }
         }
      }
   }

   private void scan() {
      class_243 targetPos = this.target.method_73189();
      class_238 targetBox = this.target.method_5829();
      if ((Boolean)this.predictMovement.get()) {
         class_243 moved = new class_243(this.target.method_23317() - this.target.field_6014, 0.0, this.target.method_23321() - this.target.field_5969);
         class_238 next = targetBox.method_997(moved);
         if (moved.method_1027() > 1.0E-6 && this.mc.field_1687.method_8587(this.target, next)) {
            targetPos = targetPos.method_1019(moved);
            targetBox = next;
         }
      }

      RaycastFactory now = this.exposure.get() == CrystalDamageEsp.Exposure.Vanilla ? this.vanillaFactory() : DamageUtils.HIT_FACTORY;
      boolean vanilla = now != DamageUtils.HIT_FACTORY;
      double effectiveMin = (Double)this.minDamage.get();
      if ((Boolean)this.usePopWindow.get()) {
         PopWindow popWindow = (PopWindow)Modules.get().get(PopWindow.class);
         if (popWindow != null && popWindow.isOpenFor(this.target)) {
            effectiveMin = Math.min(effectiveMin, popWindow.crystalMinDamage());
         }
      }

      boolean selfFirst = this.sort.get() == CrystalDamageEsp.SortMode.Score || (Boolean)this.hideSuicidal.get();
      double placeReach = this.vanillaReach.get() ? Math.min((Double)this.reach.get(), VanillaLimits.blockRange()) : (Double)this.reach.get();
      double hitReach = this.vanillaReach.get() ? Math.min((Double)this.hitRange.get(), VanillaLimits.entityRange()) : (Double)this.hitRange.get();
      class_243 eyes = this.mc.field_1724.method_33571();
      class_2338 origin = this.target.method_24515();
      int radius = (int)Math.ceil((Double)this.searchRadius.get());
      boolean legacy = ServerVersion.needsLegacyCrystalPlacement();
      List<CrystalDamageEsp.Spot> reachable = new ArrayList<>();
      List<CrystalDamageEsp.Spot> unreachable = new ArrayList<>();

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               class_2338 base = origin.method_10069(x, y, z);
               if (CrystalUtils.isBase(base)) {
                  class_243 crystal = CrystalUtils.crystalPos(base);
                  boolean inReach = !(Boolean)this.onlyReachable.get() || withinReach(base, eyes, placeReach, hitReach) && Stealth.allowsBlock(base, crystal);
                  if ((inReach || (Boolean)this.showUnreachable.get()) && CrystalUtils.canPlace(base, legacy, false)) {
                     CrystalDamageEsp.Spot spot = this.rate(
                        base, crystal, new class_238(base.method_10084()), targetPos, targetBox, now, vanilla, inReach, false, selfFirst, effectiveMin
                     );
                     if (spot != null && (!(Boolean)this.hideSuicidal.get() || !this.isSuicide(spot.selfDamage))) {
                        (inReach ? reachable : unreachable).add(spot);
                     }
                  }
               }
            }
         }
      }

      Comparator<CrystalDamageEsp.Spot> order = this.sort.get() == CrystalDamageEsp.SortMode.Score
         ? Comparator.<CrystalDamageEsp.Spot>comparingDouble(s -> CrystalDamageEsp.Marks.score(s.best(), s.selfDamage, (Double)this.selfWeight.get()))
            .reversed()
         : Comparator.comparingDouble(CrystalDamageEsp.Spot::best).reversed();
      reachable.sort(order);
      int limit = (Integer)this.maxSpots.get();

      for (CrystalDamageEsp.Spot spot : reachable) {
         if (this.spots.size() >= limit) {
            break;
         }

         if (!(Boolean)this.onlyReachable.get() || rayReaches(spot.base, placeReach, hitReach)) {
            this.spots.add(this.withSelf(spot));
         } else if ((Boolean)this.showUnreachable.get()) {
            unreachable.add(spot.outOfReach());
         }
      }

      unreachable.sort(order);

      for (int i = 0; i < Math.min(limit, unreachable.size()); i++) {
         this.extras.add(this.withSelf(unreachable.get(i)));
      }

      if ((Boolean)this.showPlaced.get()) {
         class_238 area = this.target.method_5829().method_1014((Double)this.searchRadius.get());

         for (class_1511 entity : this.mc.field_1687.method_8390(class_1511.class, area, e -> !e.method_31481())) {
            CrystalDamageEsp.Spot spot = this.rate(
               null, entity.method_73189(), entity.method_5829(), targetPos, targetBox, now, vanilla, true, true, true, effectiveMin
            );
            if (spot != null) {
               this.extras.add(spot);
            }
         }
      }
   }

   private static boolean withinReach(class_2338 base, class_243 eyes, double placeReach, double hitReach) {
      return new class_238(base).method_49271(eyes) >= placeReach * placeReach
         ? false
         : hitReach <= 0.0 || CrystalUtils.crystalHitbox(base).method_49271(eyes) < hitReach * hitReach;
   }

   private static boolean rayReaches(class_2338 base, double placeReach, double hitReach) {
      return LegitPlace.forCrystal(base, placeReach) == null
         ? false
         : hitReach <= 0.0 || LegitPlace.forEntity(CrystalUtils.crystalHitbox(base), hitReach) != null;
   }

   private CrystalDamageEsp.Spot rate(
      class_2338 base,
      class_243 crystal,
      class_238 box,
      class_243 targetPos,
      class_238 targetBox,
      RaycastFactory now,
      boolean vanilla,
      boolean reachable,
      boolean placed,
      boolean withSelf,
      double minimum
   ) {
      float open = DamageUtils.crystalDamage(this.target, targetPos, targetBox, crystal, DamageUtils.HIT_FACTORY);
      if (open < minimum) {
         return null;
      } else {
         float damage = vanilla ? DamageUtils.crystalDamage(this.target, targetPos, targetBox, crystal, now) : open;
         float self = withSelf ? this.selfDamage(crystal) : Float.NaN;
         return new CrystalDamageEsp.Spot(base, crystal, box, damage, open, self, reachable, placed);
      }
   }

   private CrystalDamageEsp.Spot withSelf(CrystalDamageEsp.Spot spot) {
      return Float.isNaN(spot.selfDamage) ? spot.withSelf(this.selfDamage(spot.crystal)) : spot;
   }

   private float selfDamage(class_243 crystal) {
      return DamageUtils.crystalDamage(this.mc.field_1724, crystal);
   }

   private RaycastFactory vanillaFactory() {
      class_3726 context = class_3726.method_16195(this.target);
      return (ctx, pos) -> {
         class_2680 state = this.mc.field_1687.method_8320(pos);
         return state.method_26215() ? null : state.method_26194(this.mc.field_1687, pos, context).method_1092(ctx.start(), ctx.end(), pos);
      };
   }

   private boolean isSuicide(float self) {
      return CrystalDamageEsp.Marks.suicide(self, EntityUtils.getTotalHealth(this.mc.field_1724), (Double)this.safetyMargin.get());
   }

   private CrystalDamageEsp.Marks.Mark markFor(CrystalDamageEsp.Spot spot) {
      return this.target != null && this.mc.field_1724 != null
         ? CrystalDamageEsp.Marks.mark(
            spot.damage,
            EntityUtils.getTotalHealth(this.target),
            holdsTotem(this.target),
            spot.selfDamage,
            EntityUtils.getTotalHealth(this.mc.field_1724),
            (Double)this.safetyMargin.get()
         )
         : CrystalDamageEsp.Marks.Mark.None;
   }

   private static boolean holdsTotem(class_1657 player) {
      return player.method_6047().method_31574(class_1802.field_8288) || player.method_6079().method_31574(class_1802.field_8288);
   }

   private Color colorFor(CrystalDamageEsp.Spot spot) {
      Color base = switch (this.markFor(spot)) {
         case None -> null;
         case Kill -> (SettingColor)this.lethalColor.get();
         case Pop -> (SettingColor)this.popColor.get();
         case Suicide -> (SettingColor)this.suicideColor.get();
      };
      if (base == null) {
         if (spot.placed) {
            base = (Color)this.placedColor.get();
         } else if (!spot.reachable) {
            base = (Color)this.unreachableColor.get();
         }
      }

      if (base != null) {
         this.scratch.set(base);
      } else {
         this.gradient(spot);
      }

      if ((Boolean)this.showHurtWindow.get() && DamageWindow.reduced(this.target)) {
         this.scratch.a(this.scratch.a / 2);
      }

      return this.scratch;
   }

   private void gradient(CrystalDamageEsp.Spot spot) {
      if (this.spots.isEmpty()) {
         this.scratch.set((Color)this.bestColor.get());
      } else {
         float best = this.spots.get(0).best();
         float worst = this.spots.get(this.spots.size() - 1).best();
         double t = best - worst < 0.01 ? 1.0 : (spot.best() - worst) / (best - worst);
         t = Math.max(0.0, Math.min(1.0, t));
         Color a = (Color)this.weakColor.get();
         Color b = (Color)this.bestColor.get();
         this.scratch.set((int)(a.r + (b.r - a.r) * t), (int)(a.g + (b.g - a.g) * t), (int)(a.b + (b.b - a.b) * t), (int)(a.a + (b.a - a.a) * t));
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.target != null) {
         for (CrystalDamageEsp.Spot spot : this.spots) {
            this.drawBox(event, spot);
         }

         for (CrystalDamageEsp.Spot spot : this.extras) {
            this.drawBox(event, spot);
         }
      }
   }

   private void drawBox(Render3DEvent event, CrystalDamageEsp.Spot spot) {
      Color color = this.colorFor(spot);
      if (spot.placed) {
         event.renderer.box(spot.box, color, color, (ShapeMode)this.shapeMode.get(), 0);
      } else {
         event.renderer
            .box(
               spot.base.method_10263(),
               spot.base.method_10264() + 1.0,
               spot.base.method_10260(),
               spot.base.method_10263() + 1.0,
               spot.base.method_10264() + 2.0,
               spot.base.method_10260() + 1.0,
               color,
               color,
               (ShapeMode)this.shapeMode.get(),
               0
            );
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.target != null) {
         if ((Boolean)this.showHurtWindow.get() && DamageWindow.reduced(this.target)) {
            this.textPos.set(this.target.method_23317(), this.target.method_23320() + 0.8, this.target.method_23321());
            if (NametagUtils.to2D(this.textPos, (Double)this.textScale.get())) {
               this.drawText("iframe " + DamageWindow.ticksUntilOpen(this.target) + "t", (Color)this.weakColor.get());
            }
         }

         if ((Boolean)this.showDamage.get()) {
            for (CrystalDamageEsp.Spot spot : this.spots) {
               this.drawLabel(spot);
            }

            for (CrystalDamageEsp.Spot spot : this.extras) {
               this.drawLabel(spot);
            }
         }
      }
   }

   private void drawLabel(CrystalDamageEsp.Spot spot) {
      this.textPos.set(spot.crystal.field_1352, spot.crystal.field_1351 + 0.5, spot.crystal.field_1350);
      if (NametagUtils.to2D(this.textPos, (Double)this.textScale.get())) {
         String text = CrystalDamageEsp.Marks.label(this.markFor(spot), spot.damage, spot.openDamage, this.showSelfDamage.get() ? spot.selfDamage : Float.NaN);
         this.drawText(text, this.colorFor(spot));
      }
   }

   private void drawText(String text, Color color) {
      NametagUtils.begin(this.textPos);
      TextRenderer.get().begin(1.0, false, true);
      double half = TextRenderer.get().getWidth(text) / 2.0;
      TextRenderer.get().render(text, -half, 0.0, color, true);
      TextRenderer.get().end();
      NametagUtils.end();
   }

   public class_1657 getTarget() {
      return this.isActive() ? this.target : null;
   }

   private CrystalDamageEsp.Spot bestByDamage() {
      CrystalDamageEsp.Spot best = null;

      for (CrystalDamageEsp.Spot spot : this.spots) {
         if (best == null || spot.damage > best.damage) {
            best = spot;
         }
      }

      return best;
   }

   public float getBestDamage() {
      CrystalDamageEsp.Spot best = this.isActive() ? this.bestByDamage() : null;
      return best == null ? Float.NaN : best.damage;
   }

   public class_243 getBestCrystalPos() {
      CrystalDamageEsp.Spot best = this.isActive() ? this.bestByDamage() : null;
      return best == null ? null : best.crystal;
   }

   public String getInfoString() {
      CrystalDamageEsp.Spot best = this.bestByDamage();
      return best == null ? null : String.format("%.1f", best.damage);
   }

   public static enum Exposure {
      Meteor,
      Vanilla;
   }

   static final class Marks {
      private Marks() {
      }

      static double score(float damage, float self, double selfWeight) {
         return damage - selfWeight * (Float.isNaN(self) ? 0.0F : self);
      }

      static boolean suicide(float self, float selfHealth, double margin) {
         return !Float.isNaN(self) && self >= selfHealth - margin;
      }

      static CrystalDamageEsp.Marks.Mark mark(float damage, float targetHealth, boolean targetTotem, float self, float selfHealth, double margin) {
         if (targetHealth > 0.0F && damage >= targetHealth) {
            return targetTotem ? CrystalDamageEsp.Marks.Mark.Pop : CrystalDamageEsp.Marks.Mark.Kill;
         } else {
            return suicide(self, selfHealth, margin) ? CrystalDamageEsp.Marks.Mark.Suicide : CrystalDamageEsp.Marks.Mark.None;
         }
      }

      static String label(CrystalDamageEsp.Marks.Mark mark, float damage, float openDamage, float self) {
         StringBuilder text = new StringBuilder();
         switch (mark) {
            case None:
            default:
               break;
            case Kill:
               text.append("KILL ");
               break;
            case Pop:
               text.append("POP ");
               break;
            case Suicide:
               text.append("! ");
         }

         text.append(String.format("%.1f", damage));
         if (openDamage - damage > 1.0F) {
            text.append(String.format("(%.1f)", openDamage));
         }

         if (!Float.isNaN(self)) {
            text.append(String.format("/%.1f", self));
         }

         return text.toString();
      }

      static enum Mark {
         None,
         Kill,
         Pop,
         Suicide;
      }
   }

   public static enum SortMode {
      Damage,
      Score;
   }

   private record Spot(class_2338 base, class_243 crystal, class_238 box, float damage, float openDamage, float selfDamage, boolean reachable, boolean placed) {
      float best() {
         return Math.max(this.damage, this.openDamage);
      }

      CrystalDamageEsp.Spot withSelf(float self) {
         return new CrystalDamageEsp.Spot(this.base, this.crystal, this.box, this.damage, this.openDamage, self, this.reachable, this.placed);
      }

      CrystalDamageEsp.Spot outOfReach() {
         return new CrystalDamageEsp.Spot(this.base, this.crystal, this.box, this.damage, this.openDamage, this.selfDamage, false, this.placed);
      }
   }
}
