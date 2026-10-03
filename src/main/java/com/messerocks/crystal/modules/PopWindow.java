package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_2663;

public class PopWindow extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> window = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("window")).description("How many ticks the window stays open after a pop.")).defaultValue(20))
            .min(1)
            .sliderMax(60)
            .build()
      );
   private final Setting<Double> crystalMinDamage = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("crystal-min-damage"))
               .description("Damage requirement Auto Crystal uses inside the window."))
            .defaultValue(0.5)
            .min(0.0)
            .sliderMax(10.0)
            .build()
      );
   private final Setting<Double> anchorMinDamage = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("anchor-min-damage"))
               .description("Damage requirement Auto Anchor uses inside the window."))
            .defaultValue(0.5)
            .min(0.0)
            .sliderMax(10.0)
            .build()
      );
   private final Setting<Boolean> ignoreRatio = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ignore-damage-ratio"))
                  .description("Also ignore Auto Anchor's min-damage-ratio inside the window. Your own safety limit still applies."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> countSelf = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("count-own-pops"))
                  .description("Also open a window when you pop. Only useful for testing that detection works."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Say in chat when a window opens."))
               .defaultValue(true))
            .build()
      );
   private final Map<UUID, Integer> open = new HashMap<>();

   public PopWindow() {
      super(CrystalAddon.CATEGORY, "pop-window", "After the target pops a totem, lets Auto Crystal and Auto Anchor drop their damage floor for a moment.");
   }

   public void onDeactivate() {
      this.open.clear();
   }

   @EventHandler
   private void onTick(Pre event) {
      this.open.entrySet().removeIf(entry -> {
         int left = entry.getValue() - 1;
         entry.setValue(left);
         return left <= 0;
      });
   }

   @EventHandler
   private void onPacket(Receive event) {
      if (event.packet instanceof class_2663 packet) {
         if (packet.method_11470() == 35) {
            if (this.mc.field_1687 != null) {
               if (packet.method_11469(this.mc.field_1687) instanceof class_1657 player) {
                  if (player != this.mc.field_1724 || (Boolean)this.countSelf.get()) {
                     this.open.put(player.method_5667(), (Integer)this.window.get());
                     if ((Boolean)this.chatInfo.get()) {
                        this.info("%s popped, window open for %d ticks.", new Object[]{player.method_5477().getString(), this.window.get()});
                     }
                  }
               }
            }
         }
      }
   }

   public boolean isOpenFor(class_1657 target) {
      return this.isActive() && target != null && this.open.getOrDefault(target.method_5667(), 0) > 0;
   }

   public double crystalMinDamage() {
      return (Double)this.crystalMinDamage.get();
   }

   public double anchorMinDamage() {
      return (Double)this.anchorMinDamage.get();
   }

   public boolean ignoreRatio() {
      return (Boolean)this.ignoreRatio.get();
   }

   public String getInfoString() {
      return this.open.isEmpty() ? null : this.open.size() + " open";
   }
}
