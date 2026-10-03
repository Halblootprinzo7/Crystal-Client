package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1792;
import net.minecraft.class_1802;

public class SmartTotem extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> threshold = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("health-threshold")).description("Swap once your health minus the predicted damage drops to this."))
            .defaultValue(11.0)
            .min(0.0)
            .sliderMax(36.0)
            .build()
      );
   private final Setting<Boolean> predictCrystals = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-crystals"))
                  .description("Count the strongest crystal currently placed near you as incoming damage."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> predictRange = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("predict-range")).description("How far to look for crystals."))
               .defaultValue(6.0)
               .min(0.0)
               .sliderMax(12.0)
               .visible(this.predictCrystals::get))
            .build()
      );
   private final Setting<Boolean> predictFall = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("predict-fall"))
                  .description("Count the fall damage you would take right now."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> restore = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("restore-offhand"))
                  .description("Put the previous offhand item back once you are safe again."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Report swaps in chat."))
               .defaultValue(false))
            .build()
      );
   private int originSlot = -1;

   public SmartTotem() {
      super(CrystalAddon.CATEGORY, "smart-totem", "Offhand totem swapping driven by predicted damage.");
   }

   public void onDeactivate() {
      this.originSlot = -1;
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (this.mc.field_1724.field_7512 == this.mc.field_1724.field_7498) {
            boolean holdingTotem = this.mc.field_1724.method_6079().method_31574(class_1802.field_8288);
            boolean danger = EntityUtils.getTotalHealth(this.mc.field_1724) - this.predictedDamage() <= (Double)this.threshold.get();
            if (danger) {
               if (holdingTotem) {
                  return;
               }

               FindItemResult totem = InvUtils.find(new class_1792[]{class_1802.field_8288});
               if (!totem.found() || totem.isOffhand()) {
                  return;
               }

               this.originSlot = totem.slot();
               InvUtils.move().from(this.originSlot).toOffhand();
               if ((Boolean)this.chatInfo.get()) {
                  this.info("Totem swapped in.", new Object[0]);
               }
            } else if ((Boolean)this.restore.get() && holdingTotem && this.originSlot != -1) {
               InvUtils.move().fromOffhand().to(this.originSlot);
               this.originSlot = -1;
               if ((Boolean)this.chatInfo.get()) {
                  this.info("Offhand restored.", new Object[0]);
               }
            }
         }
      }
   }

   private float predictedDamage() {
      float damage = 0.0F;
      if ((Boolean)this.predictCrystals.get()) {
         double rangeSq = (Double)this.predictRange.get() * (Double)this.predictRange.get();

         for (class_1297 entity : this.mc.field_1687.method_18112()) {
            if (entity instanceof class_1511 crystal && !crystal.method_31481() && !(this.mc.field_1724.method_5858(crystal) > rangeSq)) {
               damage = Math.max(damage, DamageUtils.crystalDamage(this.mc.field_1724, crystal.method_73189()));
            }
         }
      }

      if ((Boolean)this.predictFall.get()) {
         damage += DamageUtils.fallDamage(this.mc.field_1724);
      }

      return damage;
   }
}
