package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import com.messerocks.crystal.utils.BlastShield;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_12206;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_4969;
import net.minecraft.class_9334;

public class SuicidePrevent extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgGuard = this.settings.createGroup("Guarded modules");
   private final Setting<Double> minHealth = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("min-health")).description("Pause the auras once your health (plus weighted absorption) drops to this."))
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
   private final Setting<Double> absorptionWeight = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("absorption-weight"))
               .description(
                  "How much absorption hearts count towards your health. 1 counts them fully; lower treats the absorption a totem hands out as less of a cushion than real health."
               ))
            .defaultValue(1.0)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .build()
      );
   private final Setting<Double> noTotemHealth = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("no-totem-health"))
               .description("With no totem in either hand, pause already at this health, since the next pop would be a death. 0 turns it off."))
            .defaultValue(12.0)
            .min(0.0)
            .sliderMax(36.0)
            .build()
      );
   private final Setting<Boolean> pauseAfterOwnPop = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("pause-after-own-pop"))
                  .description("Pause right after you pop, until a totem is back in hand. Needs Pop Window to be on."))
               .defaultValue(true))
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
   private final Setting<Boolean> predictAnchors = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-anchors"))
                  .description("Count charged respawn anchors outside the Nether the same way - one click on them is an explosion."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> predictFall = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-fall"))
                  .description("Add the damage of the fall you are currently in on top of the strongest blast."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> predictRange = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("predict-range")).description("How far to look for a threatening crystal or anchor."))
               .defaultValue(8.0)
               .min(0.0)
               .sliderMax(12.0)
               .visible(() -> (Boolean)this.predictCrystals.get() || (Boolean)this.predictAnchors.get()))
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
   private static final int MAX_ANCHOR_RADIUS = 8;
   private final Map<CrystalModule, Long> pausedByMe = new HashMap<>();
   private final EnumSet<SuicidePrevent.Gate.Reason> reasons = EnumSet.noneOf(SuicidePrevent.Gate.Reason.class);
   private SuicidePrevent.Gate.Reason lastReason;
   private final List<class_2338> chargedAnchors = new ArrayList<>();
   private Object anchorWorld;
   private int anchorScanTimer;
   private boolean leaving;

   public SuicidePrevent() {
      super(CrystalAddon.CATEGORY, "suicide-prevent", "Pauses the offensive auras before your own or an incoming crystal can kill you.");
   }

   public void onActivate() {
      this.leaving = false;
      this.clearReasons();
   }

   public void onDeactivate() {
      this.chargedAnchors.clear();
      this.anchorWorld = null;
      this.clearReasons();
      if (!this.leaving) {
         this.resumeAll();
      }
   }

   @EventHandler(
      priority = 201
   )
   private void onGameLeft(GameLeftEvent event) {
      this.leaving = true;
   }

   @EventHandler(
      priority = 200
   )
   private void onTick(Pre event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         this.pausedByMe.entrySet().removeIf(entry -> entry.getKey().activationVersion() != entry.getValue());
         double health = this.mc.field_1724.method_6032() + this.mc.field_1724.method_6067() * (Double)this.absorptionWeight.get();
         boolean hasTotem = this.holdsTotem();
         SuicidePrevent.Gate.Reason reason = null;
         if (!this.mc.field_1724.method_68878() && !this.mc.field_1724.method_7325()) {
            PopWindow popWindow = (PopWindow)Modules.get().get(PopWindow.class);
            boolean ownPop = (Boolean)this.pauseAfterOwnPop.get() && popWindow != null && popWindow.selfWindowOpen();
            reason = SuicidePrevent.Gate.danger(health, this.worstHit(), hasTotem, ownPop, (Double)this.minHealth.get(), (Double)this.noTotemHealth.get());
         }

         if (reason != null) {
            this.reasons.add(reason);
            this.lastReason = reason;
            this.pauseAll(reason);
         } else if (SuicidePrevent.Gate.safe(
            this.reasons, health, hasTotem, (Double)this.minHealth.get(), (Double)this.resumeGap.get(), (Double)this.noTotemHealth.get()
         )) {
            this.resumeAll();
            this.clearReasons();
         }
      }
   }

   private void clearReasons() {
      this.reasons.clear();
      this.lastReason = null;
   }

   private boolean holdsTotem() {
      return this.mc.field_1724.method_6079().method_57826(class_9334.field_54274) || this.mc.field_1724.method_6047().method_57826(class_9334.field_54274);
   }

   private double worstHit() {
      double worst = 0.0;
      if ((Boolean)this.predictCrystals.get()) {
         worst = this.strongestCrystalDamage();
      }

      if ((Boolean)this.predictAnchors.get()) {
         worst = Math.max(worst, this.strongestAnchorDamage());
      }

      if ((Boolean)this.predictFall.get()) {
         worst += Math.max(0.0F, DamageUtils.fallDamage(this.mc.field_1724));
      }

      return worst;
   }

   private double strongestCrystalDamage() {
      double worst = 0.0;

      for (class_1297 entity : this.mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 crystal && !crystal.method_31481() && !(crystal.method_5739(this.mc.field_1724) > (Double)this.predictRange.get())) {
            float damage = BlastShield.crystalDamage(this.mc.field_1724, crystal.method_73189());
            if (damage > worst) {
               worst = damage;
            }
         }
      }

      return worst;
   }

   private double strongestAnchorDamage() {
      if (this.anchorWorld != this.mc.field_1687) {
         this.anchorWorld = this.mc.field_1687;
         this.chargedAnchors.clear();
         this.anchorScanTimer = 0;
      }

      if (this.anchorScanTimer-- <= 0) {
         this.anchorScanTimer = 1;
         this.scanAnchors();
      }

      double worst = 0.0;
      double rangeSq = (Double)this.predictRange.get() * (Double)this.predictRange.get();

      for (class_2338 pos : this.chargedAnchors) {
         class_2680 state = this.mc.field_1687.method_8320(pos);
         if (state.method_27852(class_2246.field_23152)
            && (Integer)state.method_11654(class_4969.field_23153) != 0
            && !(this.mc.field_1724.method_5707(pos.method_46558()) > rangeSq)) {
            float damage = BlastShield.anchorDamage(this.mc.field_1724, pos.method_46558());
            if (damage > worst) {
               worst = damage;
            }
         }
      }

      return worst;
   }

   private void scanAnchors() {
      this.chargedAnchors.clear();
      int radius = Math.min(8, (int)Math.ceil((Double)this.predictRange.get()));
      class_2338 center = this.mc.field_1724.method_24515();

      for (class_2338 pos : class_2338.method_10097(center.method_10069(-radius, -radius, -radius), center.method_10069(radius, radius, radius))) {
         class_2680 state = this.mc.field_1687.method_8320(pos);
         if (state.method_27852(class_2246.field_23152)
            && (Integer)state.method_11654(class_4969.field_23153) != 0
            && !(Boolean)this.mc.field_1687.method_75728().method_75697(class_12206.field_63757, pos)) {
            this.chargedAnchors.add(pos.method_10062());
         }
      }
   }

   private void pauseAll(SuicidePrevent.Gate.Reason reason) {
      this.pause(DoubleTap.class, (Boolean)this.guardDoubleTap.get(), reason);
      this.pause(AutoAnchor.class, (Boolean)this.guardAutoAnchor.get(), reason);
      this.pause(AnchorMacro.class, (Boolean)this.guardAnchorMacro.get(), reason);
   }

   private void pause(Class<? extends Module> type, boolean guarded, SuicidePrevent.Gate.Reason reason) {
      if (guarded) {
         Module module = Modules.get().get(type);
         if (module instanceof CrystalModule crystal && module.isActive()) {
            module.toggle();
            this.pausedByMe.put(crystal, crystal.activationVersion());
            if ((Boolean)this.chatInfo.get()) {
               this.info("Paused %s - %s.", new Object[]{module.title, reason.text});
            }
         }
      }
   }

   private void resumeAll() {
      if (!this.pausedByMe.isEmpty()) {
         for (Entry<CrystalModule, Long> entry : this.pausedByMe.entrySet()) {
            CrystalModule module = entry.getKey();
            if (!module.isActive() && module.activationVersion() == entry.getValue()) {
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
      if (this.pausedByMe.isEmpty()) {
         return null;
      } else {
         return this.lastReason == null ? "paused " + this.pausedByMe.size() : "paused " + this.pausedByMe.size() + ", " + this.lastReason.text;
      }
   }

   static final class Gate {
      private Gate() {
      }

      static SuicidePrevent.Gate.Reason danger(double health, double worstHit, boolean hasTotem, boolean ownPopOpen, double minHealth, double noTotemHealth) {
         if (health <= minHealth) {
            return SuicidePrevent.Gate.Reason.HEALTH;
         } else if (worstHit > 0.0 && health - worstHit <= minHealth) {
            return SuicidePrevent.Gate.Reason.BLAST;
         } else if (noTotemHealth > 0.0 && !hasTotem && health <= noTotemHealth) {
            return SuicidePrevent.Gate.Reason.NO_TOTEM;
         } else {
            return ownPopOpen ? SuicidePrevent.Gate.Reason.OWN_POP : null;
         }
      }

      static boolean safe(Set<SuicidePrevent.Gate.Reason> seen, double health, boolean hasTotem, double minHealth, double gap, double noTotemHealth) {
         boolean needsGap = seen.isEmpty()
            || seen.contains(SuicidePrevent.Gate.Reason.HEALTH)
            || seen.contains(SuicidePrevent.Gate.Reason.BLAST)
            || seen.contains(SuicidePrevent.Gate.Reason.NO_TOTEM);
         return needsGap && health < minHealth + gap
            ? false
            : !seen.contains(SuicidePrevent.Gate.Reason.NO_TOTEM) || hasTotem || !(health < noTotemHealth + gap);
      }

      static enum Reason {
         HEALTH("health"),
         BLAST("blast"),
         NO_TOTEM("no totem"),
         OWN_POP("own pop");

         final String text;

         private Reason(String text) {
            this.text = text;
         }
      }
   }
}
