package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.CrystalUtils;
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
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import org.joml.Vector3d;

public class CrystalDamageEsp extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
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
                  .description("Hide spots you could not place at anyway."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> reach = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("reach")).description("Your placement reach."))
               .defaultValue(4.5)
               .min(0.0)
               .sliderMax(6.0)
               .visible(this.onlyReachable::get))
            .build()
      );
   private final Setting<Integer> updateInterval = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("update-interval"))
                  .description("Ticks between scans. The scan is the expensive part, so raise this if it costs you frames."))
               .defaultValue(4))
            .min(1)
            .sliderMax(20)
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
   private final List<CrystalDamageEsp.Spot> spots = new ArrayList<>();
   private final Color scratch = new Color();
   private final Vector3d textPos = new Vector3d();
   private class_1657 target;
   private int timer;

   public CrystalDamageEsp() {
      super(CrystalAddon.CATEGORY, "crystal-damage-esp", "Shows the crystal spots around a target and what each would do.");
   }

   public void onDeactivate() {
      this.spots.clear();
      this.target = null;
      this.timer = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (this.timer > 0) {
            this.timer--;
         } else {
            this.timer = (Integer)this.updateInterval.get();
            this.target = TargetUtils.getPlayerTarget((Double)this.targetRange.get(), (SortPriority)this.priority.get());
            this.spots.clear();
            if (this.target != null) {
               this.scan();
            }
         }
      }
   }

   private void scan() {
      class_2338 origin = this.target.method_24515();
      int radius = (int)Math.ceil((Double)this.searchRadius.get());
      List<CrystalDamageEsp.Spot> found = new ArrayList<>();

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               class_2338 base = origin.method_10069(x, y, z);
               if (CrystalUtils.canPlace(base, false, false)) {
                  class_243 crystal = CrystalUtils.crystalPos(base);
                  if (!(Boolean)this.onlyReachable.get() || !(this.mc.field_1724.method_33571().method_1022(crystal) > (Double)this.reach.get())) {
                     float damage = DamageUtils.crystalDamage(this.target, crystal);
                     if (!(damage < (Double)this.minDamage.get())) {
                        found.add(new CrystalDamageEsp.Spot(base, damage, DamageUtils.crystalDamage(this.mc.field_1724, crystal)));
                     }
                  }
               }
            }
         }
      }

      found.sort(Comparator.<CrystalDamageEsp.Spot>comparingDouble(s -> s.damage).reversed());

      for (int i = 0; i < Math.min((Integer)this.maxSpots.get(), found.size()); i++) {
         this.spots.add(found.get(i));
      }
   }

   private Color colorFor(CrystalDamageEsp.Spot spot) {
      if (this.target != null && spot.damage >= EntityUtils.getTotalHealth(this.target)) {
         return (Color)this.lethalColor.get();
      } else {
         float best = this.spots.get(0).damage;
         float worst = this.spots.get(this.spots.size() - 1).damage;
         double t = best - worst < 0.01 ? 1.0 : (spot.damage - worst) / (best - worst);
         Color a = (Color)this.weakColor.get();
         Color b = (Color)this.bestColor.get();
         return this.scratch.set((int)(a.r + (b.r - a.r) * t), (int)(a.g + (b.g - a.g) * t), (int)(a.b + (b.b - a.b) * t), (int)(a.a + (b.a - a.a) * t));
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (!this.spots.isEmpty()) {
         for (CrystalDamageEsp.Spot spot : this.spots) {
            Color color = this.colorFor(spot);
            event.renderer
               .box(
                  spot.pos.method_10263(),
                  spot.pos.method_10264() + 1.0,
                  spot.pos.method_10260(),
                  spot.pos.method_10263() + 1.0,
                  spot.pos.method_10264() + 2.0,
                  spot.pos.method_10260() + 1.0,
                  color,
                  color,
                  (ShapeMode)this.shapeMode.get(),
                  0
               );
         }
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if ((Boolean)this.showDamage.get() && !this.spots.isEmpty()) {
         for (CrystalDamageEsp.Spot spot : this.spots) {
            this.textPos.set(spot.pos.method_10263() + 0.5, spot.pos.method_10264() + 1.5, spot.pos.method_10260() + 0.5);
            if (NametagUtils.to2D(this.textPos, (Double)this.textScale.get())) {
               String text = this.showSelfDamage.get() ? String.format("%.1f/%.1f", spot.damage, spot.selfDamage) : String.format("%.1f", spot.damage);
               NametagUtils.begin(this.textPos);
               TextRenderer.get().begin(1.0, false, true);
               double half = TextRenderer.get().getWidth(text) / 2.0;
               TextRenderer.get().render(text, -half, 0.0, this.colorFor(spot), true);
               TextRenderer.get().end();
               NametagUtils.end();
            }
         }
      }
   }

   public String getInfoString() {
      return this.spots.isEmpty() ? null : String.format("%.1f", this.spots.get(0).damage);
   }

   private record Spot(class_2338 pos, float damage, float selfDamage) {
   }
}
