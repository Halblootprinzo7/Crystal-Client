package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import java.util.HashSet;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1297;
import net.minecraft.class_1511;

public class SuicidePrevent extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgGuard = this.settings.createGroup("Guarded modules");
   private final Setting<Double> minHealth = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("min-health")).description("Pause the auras once your health (plus absorption) drops to this."))
            .defaultValue(6.0)
            .min(0.0)
            .sliderMax(36.0)
            .build()
      );
   private final Setting<Double> resumeGap = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("resume-gap"))
               .description("How far above min-health you must recover before the auras come back on. Stops it flickering on and off at the threshold."))
            .defaultValue(6.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Boolean> predictCrystals = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-crystals"))
                  .description("Also pause when the strongest crystal already next to you would drop your health past the limit if it went off now."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> predictRange = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("predict-range")).description("How far to look for a threatening crystal."))
               .defaultValue(8.0)
               .min(0.0)
               .sliderMax(12.0)
               .visible(this.predictCrystals::get))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Say in chat when it pauses and resumes the auras."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> guardDoubleTap = this.sgGuard
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                  .name("double-tap"))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> guardAutoAnchor = this.sgGuard
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                  .name("auto-anchor"))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> guardAnchorMacro = this.sgGuard
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                  .name("anchor-macro"))
               .defaultValue(true))
            .build()
      );
   private final Set<Module> pausedByMe = new HashSet<>();

   public SuicidePrevent() {
      super(CrystalAddon.CATEGORY, "suicide-prevent", "Pauses the offensive auras before your own or an incoming crystal can kill you.");
   }

   public void onDeactivate() {
      this.resumeAll();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         double health = EntityUtils.getTotalHealth(this.mc.field_1724);
         boolean danger = health <= (Double)this.minHealth.get();
         if (!danger && (Boolean)this.predictCrystals.get()) {
            double afterHit = health - this.strongestCrystalDamage();
            if (afterHit <= (Double)this.minHealth.get()) {
               danger = true;
            }
         }

         if (danger) {
            this.pauseAll();
         } else if (health >= (Double)this.minHealth.get() + (Double)this.resumeGap.get()) {
            this.resumeAll();
         }
      }
   }

   private double strongestCrystalDamage() {
      double worst = 0.0;

      for (class_1297 entity : this.mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 crystal && !crystal.method_31481() && !(crystal.method_5739(this.mc.field_1724) > (Double)this.predictRange.get())) {
            float damage = DamageUtils.crystalDamage(this.mc.field_1724, crystal.method_73189());
            if (damage > worst) {
               worst = damage;
            }
         }
      }

      return worst;
   }

   private void pauseAll() {
      this.pause(DoubleTap.class, (Boolean)this.guardDoubleTap.get());
      this.pause(AutoAnchor.class, (Boolean)this.guardAutoAnchor.get());
      this.pause(AnchorMacro.class, (Boolean)this.guardAnchorMacro.get());
   }

   private void pause(Class<? extends Module> type, boolean guarded) {
      if (guarded) {
         Module module = Modules.get().get(type);
         if (module != null && module.isActive()) {
            module.toggle();
            this.pausedByMe.add(module);
            if ((Boolean)this.chatInfo.get()) {
               this.info("Paused %s - self-kill risk.", new Object[]{module.title});
            }
         }
      }
   }

   private void resumeAll() {
      if (!this.pausedByMe.isEmpty()) {
         for (Module module : this.pausedByMe) {
            if (!module.isActive()) {
               module.toggle();
               if ((Boolean)this.chatInfo.get()) {
                  this.info("Resumed %s - safe again.", new Object[]{module.title});
               }
            }
         }

         this.pausedByMe.clear();
      }
   }

   public String getInfoString() {
      return this.pausedByMe.isEmpty() ? null : "paused " + this.pausedByMe.size();
   }
}
