package dev.crystaladdon.hud;

import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.modules.AutoCrystal;
import dev.crystaladdon.modules.CrystalDamageEsp;
import dev.crystaladdon.modules.FightStats;
import dev.crystaladdon.modules.PopCounter;
import dev.crystaladdon.modules.TargetInfo;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_310;

public class CrystalInfoHud extends HudElement {
   public static final HudElementInfo<CrystalInfoHud> INFO = new HudElementInfo(
      CrystalAddon.HUD_GROUP, "crystal-info", "Target, crystal damage, totems and incoming damage.", CrystalInfoHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSelf = this.settings.createGroup("Self");
   private final SettingGroup sgScale = this.settings.createGroup("Scale");
   private final Setting<CrystalInfoHud.TargetSource> targetSource = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("target-source"))
                  .description(
                     "Which module's target to show. Auto takes Auto Crystal's, and falls back to Target Info and Crystal Damage ESP when Auto Crystal has none. The damage row says which module it came from, because the aura and the ESP measure differently."
                  ))
               .defaultValue(CrystalInfoHud.TargetSource.Auto))
            .build()
      );
   private final Setting<Boolean> showStatus = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-status"))
                  .description("While Auto Crystal is on but has no target, show why."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> showPops = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-pops"))
                  .description("Show the target's pop count. Needs Pop Counter enabled."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> showFight = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-fight"))
                  .description("Show Fight Stats' live line (dealt/taken and pops) while it is on and a fight is running."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> shadow = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("shadow"))
                  .description("Text shadow."))
               .defaultValue(true))
            .build()
      );
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
   private final Setting<SettingColor> warnColor = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("warn-color"))
               .description("Colour of values that need attention: an idle aura, a moderate incoming hit."))
            .defaultValue(new SettingColor(255, 170, 60))
            .build()
      );
   private final Setting<SettingColor> dangerColor = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("danger-color"))
               .description("Colour of values that are an emergency: no totem in the offhand, a lethal incoming hit."))
            .defaultValue(new SettingColor(255, 60, 60))
            .build()
      );
   private final Setting<Boolean> showTotems = this.sgSelf
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-totems"))
                  .description("Show totems as offhand | inventory. The offhand one is what saves you, so it turns red when missing."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> showCrystals = this.sgSelf
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-crystals"))
                  .description("Show how many end crystals you are carrying."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> showObsidian = this.sgSelf
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-obsidian"))
                  .description("Show how much obsidian you are carrying."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> showIncoming = this.sgSelf
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-incoming"))
                  .description("Show the damage the most dangerous crystal already standing near you would deal if it went off now."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> incomingRange = this.sgSelf
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("incoming-range"))
                  .description("How far around you to look for standing crystals."))
               .defaultValue(8.0)
               .min(1.0)
               .sliderMax(12.0)
               .visible(this.showIncoming::get))
            .build()
      );
   private final Setting<Boolean> customScale = this.sgScale
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("custom-scale"))
                  .description("Applies a custom scale to this hud element."))
               .defaultValue(false))
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
   private final List<CrystalInfoHud.Line> lines = new ArrayList<>();

   public CrystalInfoHud() {
      super(INFO);
   }

   public void tick(HudRenderer renderer) {
      this.lines.clear();
      if (this.isInEditor()) {
         this.add("Target ", "Player123 12.5");
         this.add("Damage (aura) ", "9.4");
         this.add("Pops ", "3");
         this.add("Status ", "idle (no target)", CrystalInfoHud.Tone.Warn);
         this.add("Totems ", "1 | 26");
         this.add("Crystals ", "384");
         this.add("Incoming ", "6.5", CrystalInfoHud.Tone.Warn);
      } else {
         class_310 mc = class_310.method_1551();
         if (mc.field_1724 != null && mc.field_1687 != null) {
            this.collectTarget();
            this.collectSelf(mc);
         }
      }
   }

   private void collectTarget() {
      AutoCrystal autoCrystal = (AutoCrystal)Modules.get().get(AutoCrystal.class);
      TargetInfo targetInfo = (TargetInfo)Modules.get().get(TargetInfo.class);
      CrystalDamageEsp esp = (CrystalDamageEsp)Modules.get().get(CrystalDamageEsp.class);
      class_1657 target = null;
      CrystalInfoHud.TargetSource from = null;
      CrystalInfoHud.TargetSource source = (CrystalInfoHud.TargetSource)this.targetSource.get();
      if ((source == CrystalInfoHud.TargetSource.Auto || source == CrystalInfoHud.TargetSource.AutoCrystal) && autoCrystal != null) {
         target = present(autoCrystal.getTarget());
         from = CrystalInfoHud.TargetSource.AutoCrystal;
      }

      if (target == null && (source == CrystalInfoHud.TargetSource.Auto || source == CrystalInfoHud.TargetSource.TargetInfo) && targetInfo != null) {
         target = present(targetInfo.getTarget());
         from = CrystalInfoHud.TargetSource.TargetInfo;
      }

      if (target == null && (source == CrystalInfoHud.TargetSource.Auto || source == CrystalInfoHud.TargetSource.DamageEsp) && esp != null) {
         target = present(esp.getTarget());
         from = CrystalInfoHud.TargetSource.DamageEsp;
      }

      if (target != null) {
         this.add("Target ", String.format("%s %.1f", target.method_5477().getString(), EntityUtils.getTotalHealth(target)));
         if (from == CrystalInfoHud.TargetSource.AutoCrystal) {
            this.add("Damage (aura) ", String.format("%.1f", autoCrystal.getBestDamage()));
         } else if (esp != null && esp.getTarget() == target && !Float.isNaN(esp.getBestDamage())) {
            this.add("Damage (esp) ", String.format("%.1f", esp.getBestDamage()));
         }

         if ((Boolean)this.showPops.get()) {
            PopCounter popCounter = (PopCounter)Modules.get().get(PopCounter.class);
            if (popCounter != null && popCounter.isActive()) {
               this.add("Pops ", String.valueOf(popCounter.getPops(target.method_5667())));
            }
         }
      }

      if ((Boolean)this.showStatus.get() && autoCrystal != null && autoCrystal.isActive() && autoCrystal.getTarget() == null) {
         String reason = autoCrystal.getInfoString();
         if (reason != null) {
            this.add("Status ", reason, CrystalInfoHud.Tone.Warn);
         }
      }

      if ((Boolean)this.showFight.get()) {
         FightStats fightStats = (FightStats)Modules.get().get(FightStats.class);
         if (fightStats != null && fightStats.isActive()) {
            String fight = fightStats.getInfoString();
            if (fight != null) {
               this.add("Fight ", fight);
            }
         }
      }
   }

   private static class_1657 present(class_1657 player) {
      return player != null && !player.method_31481() ? player : null;
   }

   private void collectSelf(class_310 mc) {
      class_1661 inventory = mc.field_1724.method_31548();
      if ((Boolean)this.showTotems.get()) {
         class_1799 offhand = mc.field_1724.method_6079();
         int inOffhand = offhand.method_31574(class_1802.field_8288) ? offhand.method_7947() : 0;
         int stocked = countMain(inventory, class_1802.field_8288);
         this.add("Totems ", inOffhand + " | " + stocked, inOffhand == 0 ? CrystalInfoHud.Tone.Danger : CrystalInfoHud.Tone.Normal);
      }

      if ((Boolean)this.showCrystals.get()) {
         this.add("Crystals ", String.valueOf(countAll(mc, inventory, class_1802.field_8301)));
      }

      if ((Boolean)this.showObsidian.get()) {
         this.add("Obsidian ", String.valueOf(countAll(mc, inventory, class_1802.field_8281)));
      }

      if ((Boolean)this.showIncoming.get()) {
         float worst = 0.0F;

         for (class_1511 crystal : mc.field_1687
            .method_8390(class_1511.class, mc.field_1724.method_5829().method_1014((Double)this.incomingRange.get()), e -> !e.method_31481())) {
            worst = Math.max(worst, DamageUtils.crystalDamage(mc.field_1724, crystal.method_73189()));
         }

         if (worst > 0.0F) {
            float health = EntityUtils.getTotalHealth(mc.field_1724);
            CrystalInfoHud.Tone tone = worst >= health
               ? CrystalInfoHud.Tone.Danger
               : (worst >= health / 2.0F ? CrystalInfoHud.Tone.Warn : CrystalInfoHud.Tone.Normal);
            this.add("Incoming ", String.format("%.1f", worst), tone);
         }
      }
   }

   private static int countMain(class_1661 inventory, class_1792 item) {
      int count = 0;

      for (int i = 0; i < 36; i++) {
         class_1799 stack = inventory.method_5438(i);
         if (stack.method_31574(item)) {
            count += stack.method_7947();
         }
      }

      return count;
   }

   private static int countAll(class_310 mc, class_1661 inventory, class_1792 item) {
      class_1799 offhand = mc.field_1724.method_6079();
      return countMain(inventory, item) + (offhand.method_31574(item) ? offhand.method_7947() : 0);
   }

   public void render(HudRenderer renderer) {
      if (this.lines.isEmpty()) {
         this.setSize(0.0, 0.0);
      } else {
         double width = 0.0;
         double lineHeight = renderer.textHeight((Boolean)this.shadow.get(), this.getScale());
         double y = this.y;

         for (CrystalInfoHud.Line line : this.lines) {
            double x2 = renderer.text(line.label(), this.x, y, (Color)this.labelColor.get(), (Boolean)this.shadow.get(), this.getScale());
            x2 = renderer.text(line.value(), x2, y, this.colorOf(line.tone()), (Boolean)this.shadow.get(), this.getScale());
            width = Math.max(width, x2 - this.x);
            y += lineHeight;
         }

         this.setSize(width, lineHeight * this.lines.size());
      }
   }

   private Color colorOf(CrystalInfoHud.Tone tone) {
      return switch (tone) {
         case Normal -> (SettingColor)this.valueColor.get();
         case Warn -> (SettingColor)this.warnColor.get();
         case Danger -> (SettingColor)this.dangerColor.get();
      };
   }

   private void add(String label, String value) {
      this.add(label, value, CrystalInfoHud.Tone.Normal);
   }

   private void add(String label, String value, CrystalInfoHud.Tone tone) {
      this.lines.add(new CrystalInfoHud.Line(label, value, tone));
   }

   private double getScale() {
      return this.customScale.get() ? (Double)this.scale.get() : Hud.get().getTextScale();
   }

   private record Line(String label, String value, CrystalInfoHud.Tone tone) {
   }

   public static enum TargetSource {
      Auto,
      AutoCrystal,
      TargetInfo,
      DamageEsp;
   }

   private static enum Tone {
      Normal,
      Warn,
      Danger;
   }
}
