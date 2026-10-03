package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.TargetState;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_2338;
import org.joml.Vector3d;

public class TargetInfo extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("range")).description("How far away a player can be to be read."))
            .defaultValue(12.0)
            .min(0.0)
            .sliderMax(24.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("priority"))
                  .description("Which player to read when several are in range."))
               .defaultValue(SortPriority.LowestDistance))
            .build()
      );
   private final Setting<Boolean> warnOnCover = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("warn-in-chat"))
                  .description("Say in chat when the target takes cover, so you stop feeding crystals into it."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> showCityBlock = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-city-block"))
                  .description("Outline the block to mine to open a surround."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> nametag = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("nametag"))
                  .description("Draw the state above the target."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> textScale = this.sgRender
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("text-scale")).description("Size of the label."))
               .defaultValue(1.0)
               .min(0.1)
               .sliderRange(0.5, 3.0)
               .visible(this.nametag::get))
            .build()
      );
   private final Setting<SettingColor> openColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("open-color"))
               .description("Colour while the target is exposed."))
            .defaultValue(new SettingColor(80, 255, 120, 220))
            .build()
      );
   private final Setting<SettingColor> coveredColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("covered-color"))
               .description("Colour while the target is behind cover."))
            .defaultValue(new SettingColor(255, 140, 60, 220))
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                        .name("shape-mode"))
                     .description("How the city block is drawn."))
                  .defaultValue(ShapeMode.Both))
               .visible(this.showCityBlock::get))
            .build()
      );
   private final Setting<SettingColor> citySideColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("city-side-color"))
                  .description("Side colour of the city block."))
               .defaultValue(new SettingColor(255, 255, 80, 50))
               .visible(this.showCityBlock::get))
            .build()
      );
   private final Setting<SettingColor> cityLineColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("city-line-color"))
                  .description("Line colour of the city block."))
               .defaultValue(new SettingColor(255, 255, 80, 200))
               .visible(this.showCityBlock::get))
            .build()
      );
   private final Vector3d textPos = new Vector3d();
   private class_1657 target;
   private TargetState state = TargetState.Open;
   private TargetState announced;
   private class_2338 cityBlock;

   public TargetInfo() {
      super(CrystalAddon.CATEGORY, "target-info", "Reads whether the target is open, surrounded, in a hole or burrowed.");
   }

   public void onDeactivate() {
      this.target = null;
      this.cityBlock = null;
      this.announced = null;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         this.target = TargetUtils.getPlayerTarget((Double)this.range.get(), (SortPriority)this.priority.get());
         if (this.target == null) {
            this.state = TargetState.Open;
            this.cityBlock = null;
            this.announced = null;
         } else {
            this.state = TargetState.of(this.target);
            this.cityBlock = this.state != TargetState.Surrounded && this.state != TargetState.InHole ? null : EntityUtils.getCityBlock(this.target);
            if ((Boolean)this.warnOnCover.get() && this.state != this.announced) {
               this.announced = this.state;
               if (!this.state.crystalWorks()) {
                  this.warning("%s is %s - crystals will barely scratch that.", new Object[]{this.target.method_5477().getString(), this.state.label()});
               } else {
                  this.info("%s is open.", new Object[]{this.target.method_5477().getString()});
               }
            }
         }
      }
   }

   public class_1657 getTarget() {
      return this.isActive() ? this.target : null;
   }

   public TargetState getState() {
      return this.state;
   }

   public static boolean crystalsWorthIt(class_1657 player) {
      TargetInfo module = (TargetInfo)Modules.get().get(TargetInfo.class);
      return module != null && module.isActive() && module.target == player ? module.state.crystalWorks() : true;
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if ((Boolean)this.showCityBlock.get() && this.cityBlock != null) {
         event.renderer.box(this.cityBlock, (Color)this.citySideColor.get(), (Color)this.cityLineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if ((Boolean)this.nametag.get() && this.target != null) {
         this.textPos.set(this.target.method_23317(), this.target.method_23318() + this.target.method_17682() + 0.5, this.target.method_23321());
         if (NametagUtils.to2D(this.textPos, (Double)this.textScale.get())) {
            String text = this.state.label();
            NametagUtils.begin(this.textPos);
            TextRenderer.get().begin(1.0, false, true);
            double half = TextRenderer.get().getWidth(text) / 2.0;
            TextRenderer.get().render(text, -half, 0.0, this.state.crystalWorks() ? (Color)this.openColor.get() : (Color)this.coveredColor.get(), true);
            TextRenderer.get().end();
            NametagUtils.end();
         }
      }
   }

   public String getInfoString() {
      return this.target == null ? null : this.state.label();
   }
}
