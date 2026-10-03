package com.messerocks.crystal.hud;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.modules.AutoCrystal;
import com.messerocks.crystal.modules.PopCounter;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_310;

public class CrystalInfoHud extends HudElement {
   public static final HudElementInfo<CrystalInfoHud> INFO = new HudElementInfo(
      CrystalAddon.HUD_GROUP, "crystal-info", "Target, crystal damage and totem count.", CrystalInfoHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgScale = this.settings.createGroup("Scale");
   private final Setting<Boolean> showTotems = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("show-totems")).description("Show how many totems you are carrying.")).defaultValue(true)).build());
   private final Setting<Boolean> showPops = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("show-pops")).description("Show the target's pop count. Needs Pop Counter enabled."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> shadow = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("shadow")).description("Text shadow.")).defaultValue(true)).build());
   private final Setting<SettingColor> labelColor = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("label-color"))
               .description("Colour of the labels."))
            .defaultValue(new SettingColor(175, 175, 175))
            .build()
      );
   private final Setting<SettingColor> valueColor = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("value-color"))
               .description("Colour of the values."))
            .defaultValue(new SettingColor(160, 60, 255))
            .build()
      );
   private final Setting<Boolean> customScale = this.sgScale
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("custom-scale")).description("Applies a custom scale to this hud element.")).defaultValue(false))
            .build()
      );
   private final Setting<Double> scale = this.sgScale
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("scale"))
                  .description("Custom scale."))
               .defaultValue(1.0)
               .min(0.5)
               .sliderRange(0.5, 3.0)
               .visible(this.customScale::get))
            .build()
      );
   private final List<String> labels = new ArrayList<>();
   private final List<String> values = new ArrayList<>();

   public CrystalInfoHud() {
      super(INFO);
   }

   public void render(HudRenderer renderer) {
      this.labels.clear();
      this.values.clear();
      if (this.isInEditor()) {
         this.add("Target ", "Player123 12.5");
         this.add("Damage ", "9.4");
         this.add("Totems ", "27");
         this.add("Pops ", "3");
      } else {
         this.collect();
      }

      if (this.labels.isEmpty()) {
         this.setSize(0.0, 0.0);
      } else {
         double width = 0.0;
         double lineHeight = renderer.textHeight((Boolean)this.shadow.get(), this.getScale());
         double y = this.y;

         for (int i = 0; i < this.labels.size(); i++) {
            double x2 = renderer.text(this.labels.get(i), this.x, y, (Color)this.labelColor.get(), (Boolean)this.shadow.get(), this.getScale());
            x2 = renderer.text(this.values.get(i), x2, y, (Color)this.valueColor.get(), (Boolean)this.shadow.get(), this.getScale());
            width = Math.max(width, x2 - this.x);
            y += lineHeight;
         }

         this.setSize(width, lineHeight * this.labels.size());
      }
   }

   private void collect() {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 != null && mc.field_1687 != null) {
         AutoCrystal autoCrystal = (AutoCrystal)Modules.get().get(AutoCrystal.class);
         class_1657 target = autoCrystal == null ? null : autoCrystal.getTarget();
         if (target != null) {
            this.add("Target ", String.format("%s %.1f", target.method_5477().getString(), EntityUtils.getTotalHealth(target)));
            this.add("Damage ", String.format("%.1f", autoCrystal.getBestDamage()));
            if ((Boolean)this.showPops.get()) {
               PopCounter popCounter = (PopCounter)Modules.get().get(PopCounter.class);
               if (popCounter != null && popCounter.isActive()) {
                  this.add("Pops ", String.valueOf(popCounter.getPops(target.method_5667())));
               }
            }
         }

         if ((Boolean)this.showTotems.get()) {
            this.add("Totems ", String.valueOf(this.countTotems(mc)));
         }
      }
   }

   private int countTotems(class_310 mc) {
      int count = 0;

      for (int i = 0; i < mc.field_1724.method_31548().method_5439(); i++) {
         class_1799 stack = mc.field_1724.method_31548().method_5438(i);
         if (stack.method_31574(class_1802.field_8288)) {
            count += stack.method_7947();
         }
      }

      return count;
   }

   private void add(String label, String value) {
      this.labels.add(label);
      this.values.add(value);
   }

   private double getScale() {
      return this.customScale.get() ? (Double)this.scale.get() : Hud.get().getTextScale();
   }
}
