package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.RevivedPlayers;
import com.messerocks.crystal.utils.TargetState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_1934;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import org.joml.Vector3d;

public class TargetInfo extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<TargetInfo.TargetSource> targetSource = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("target-source"))
                  .description(
                     "Auto Crystal: read the player Auto Crystal is attacking, so the reading is about the fight you are in. Own: pick with range and priority below."
                  ))
               .defaultValue(TargetInfo.TargetSource.AutoCrystal))
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("range"))
               .description("How far away a player can be to be read."))
            .defaultValue(12.0)
            .min(0.0)
            .sliderMax(24.0)
            .build()
      );
   private final Setting<SortPriority> priority = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("priority")).description("Which player to read when several are in range."))
               .defaultValue(SortPriority.LowestDistance))
            .build()
      );
   private final Setting<Boolean> warnOnCover = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("warn-in-chat"))
                  .description("Say in chat when the target takes cover, so you stop feeding crystals into it, and when they leave it again."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> announceDelay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("announce-delay"))
                     .description("Ticks a state has to hold before it is announced. Filters out a jump inside a surround reading as open for a moment."))
                  .defaultValue(4))
               .min(0)
               .sliderMax(20)
               .visible(this.warnOnCover::get))
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
   private final Setting<Double> labelOffset = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("label-offset"))
                  .description("Height of the label above the target's hitbox. The default clears Meteor's Nametags, which sit just above the eyes."))
               .defaultValue(0.9)
               .min(0.0)
               .sliderMax(2.5)
               .visible(this.nametag::get))
            .build()
      );
   private final Setting<Boolean> hideOpenLabel = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("hide-open-label"))
                     .description(
                        "Draw nothing while the target is simply open and there is nothing to act on. An exposure reading counts as something to act on, so the label still appears when show-exposure has a number."
                     ))
                  .defaultValue(true))
               .visible(this.nametag::get))
            .build()
      );
   private final Setting<Boolean> showExposure = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("show-exposure"))
                     .description(
                        "Add how much of the target the best Crystal Damage ESP spot can see, computed like vanilla. Needs Crystal Damage ESP on the same target."
                     ))
                  .defaultValue(true))
               .visible(this.nametag::get))
            .build()
      );
   private final Setting<Double> textScale = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("text-scale"))
                  .description("Size of the label."))
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
   private final Setting<SettingColor> softColor = this.sgRender
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("soft-color"))
               .description("Colour while the target hides behind soft blocks the first crystal will break."))
            .defaultValue(new SettingColor(255, 230, 80, 220))
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
         ((Builder)((Builder)((Builder)((Builder)new Builder().name("shape-mode")).description("How the city block is drawn.")).defaultValue(ShapeMode.Both))
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
   private final TargetInfo.Announcer announcer = new TargetInfo.Announcer();
   private class_1657 target;
   private TargetState state = TargetState.Open;
   private class_2338 cityBlock;
   private float exposure = Float.NaN;
   private long ticks;

   public TargetInfo() {
      super(CrystalAddon.CATEGORY, "target-info", "Reads whether the target is open, surrounded, in a hole, trapped or burrowed.");
   }

   public void onDeactivate() {
      this.target = null;
      this.state = TargetState.Open;
      this.cityBlock = null;
      this.exposure = Float.NaN;
      this.announcer.clear();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (this.sessionChanged()) {
            this.onDeactivate();
         }

         this.ticks++;
         this.target = this.resolveTarget();
         if (this.target == null) {
            this.state = TargetState.Open;
            this.cityBlock = null;
            this.exposure = Float.NaN;
         } else {
            this.state = TargetState.of(this.target);
            this.cityBlock = this.state.cityable() ? EntityUtils.getCityBlock(this.target) : null;
            this.exposure = this.measureExposure();
            TargetInfo.Announcer.Kind kind = this.announcer
               .update(this.target.method_5667(), this.state, this.ticks, (Integer)this.announceDelay.get(), (Boolean)this.warnOnCover.get());
            String name = this.target.method_5477().getString();
            switch (kind) {
               case None:
               default:
                  break;
               case Covered:
                  this.warning("%s is %s - crystals will barely scratch that.", new Object[]{name, this.state.label()});
                  break;
               case Exposed:
                  this.info("%s is out of cover again (%s).", new Object[]{name, this.state.label()});
            }
         }
      }
   }

   private class_1657 resolveTarget() {
      if (this.targetSource.get() == TargetInfo.TargetSource.AutoCrystal) {
         AutoCrystal autoCrystal = (AutoCrystal)Modules.get().get(AutoCrystal.class);
         class_1657 aimed = autoCrystal == null ? null : autoCrystal.getTarget();
         if (aimed != null && !aimed.method_31481()) {
            return aimed;
         }
      }

      return findPlayerTarget((Double)this.range.get(), (SortPriority)this.priority.get());
   }

   public static class_1657 findPlayerTarget(double range, SortPriority priority) {
      class_1657 found = TargetUtils.getPlayerTarget(range, priority);
      if (found != null) {
         return found;
      } else {
         return TargetUtils.get(
               entity -> entity instanceof class_1657 playerx
                  && playerx != MeteorClient.mc.field_1724
                  && RevivedPlayers.isRevived(playerx)
                  && Friends.get().shouldAttack(playerx)
                  && EntityUtils.getGameMode(playerx) == class_1934.field_9215
                  && PlayerUtils.isWithin(playerx, range),
               priority
            ) instanceof class_1657 player
            ? player
            : null;
      }
   }

   private float measureExposure() {
      if ((Boolean)this.showExposure.get() && (Boolean)this.nametag.get()) {
         CrystalDamageEsp esp = (CrystalDamageEsp)Modules.get().get(CrystalDamageEsp.class);
         if (esp != null && esp.getTarget() == this.target) {
            class_243 best = esp.getBestCrystalPos();
            return best == null ? Float.NaN : TargetState.exposure(this.target, best);
         } else {
            return Float.NaN;
         }
      } else {
         return Float.NaN;
      }
   }

   public class_1657 getTarget() {
      return this.isActive() ? this.target : null;
   }

   public TargetState getState() {
      return this.state;
   }

   public static TargetState stateOf(class_1657 player) {
      if (player == null) {
         return TargetState.Open;
      } else {
         TargetInfo module = (TargetInfo)Modules.get().get(TargetInfo.class);
         return module != null && module.isActive() && module.target == player ? module.state : TargetState.of(player);
      }
   }

   public static boolean crystalsWorthIt(class_1657 player) {
      return stateOf(player).crystalWorks();
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
         if (!(Boolean)this.hideOpenLabel.get() || this.state != TargetState.Open || this.cityBlock != null || !Float.isNaN(this.exposure)) {
            this.textPos
               .set(
                  this.target.method_23317(),
                  this.target.method_23318() + this.target.method_17682() + (Double)this.labelOffset.get(),
                  this.target.method_23321()
               );
            if (NametagUtils.to2D(this.textPos, (Double)this.textScale.get())) {
               StringBuilder text = new StringBuilder(this.state.label());
               if (this.state != TargetState.Open) {
                  long held = this.announcer.heldTicks(this.target.method_5667(), this.ticks);
                  if (held >= 0L) {
                     text.append(String.format(" %.1fs", held / 20.0));
                  }
               }

               if (!Float.isNaN(this.exposure)) {
                  text.append(String.format(" %d%%", Math.round(this.exposure * 100.0F)));
               }

               String line = text.toString();
               NametagUtils.begin(this.textPos);
               TextRenderer.get().begin(1.0, false, true);
               SettingColor color = !this.state.crystalWorks()
                  ? (SettingColor)this.coveredColor.get()
                  : (this.state.firstCrystalOpens() ? (SettingColor)this.softColor.get() : (SettingColor)this.openColor.get());
               double half = TextRenderer.get().getWidth(line) / 2.0;
               TextRenderer.get().render(line, -half, 0.0, color, true);
               TextRenderer.get().end();
               NametagUtils.end();
            }
         }
      }
   }

   public String getInfoString() {
      return this.target == null ? null : this.state.label();
   }

   static final class Announcer {
      private static final long FORGET_AFTER = 1200L;
      private final Map<UUID, TargetInfo.Announcer.Seen> seen = new HashMap<>();
      private final Map<UUID, TargetState> announced = new HashMap<>();

      TargetInfo.Announcer.Kind update(UUID id, TargetState state, long now, int delay) {
         return this.update(id, state, now, delay, true);
      }

      TargetInfo.Announcer.Kind update(UUID id, TargetState state, long now, int delay, boolean announce) {
         this.forgetStale(now);
         TargetInfo.Announcer.Seen entry = this.seen.computeIfAbsent(id, k -> new TargetInfo.Announcer.Seen());
         if (entry.state != state || now - entry.last > 1L) {
            entry.state = state;
            entry.since = now;
         }

         entry.last = now;
         TargetState last = this.announced.get(id);
         if (state == last || now - entry.since < delay) {
            return TargetInfo.Announcer.Kind.None;
         } else if (!announce) {
            return TargetInfo.Announcer.Kind.None;
         } else {
            this.announced.put(id, state);
            if (!state.crystalWorks()) {
               return TargetInfo.Announcer.Kind.Covered;
            } else {
               return last != null && !last.crystalWorks() ? TargetInfo.Announcer.Kind.Exposed : TargetInfo.Announcer.Kind.None;
            }
         }
      }

      private void forgetStale(long now) {
         this.seen.entrySet().removeIf(e -> {
            if (now - e.getValue().last <= 1200L) {
               return false;
            } else {
               this.announced.remove(e.getKey());
               return true;
            }
         });
      }

      long heldTicks(UUID id, long now) {
         TargetInfo.Announcer.Seen entry = this.seen.get(id);
         return entry != null && now - entry.last <= 1L ? now - entry.since : -1L;
      }

      void clear() {
         this.seen.clear();
         this.announced.clear();
      }

      static enum Kind {
         None,
         Covered,
         Exposed;
      }

      private static final class Seen {
         TargetState state;
         long since;
         long last;
      }
   }

   public static enum TargetSource {
      AutoCrystal,
      Own;
   }
}
