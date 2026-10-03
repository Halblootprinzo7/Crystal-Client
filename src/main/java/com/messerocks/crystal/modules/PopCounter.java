package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1657;
import net.minecraft.class_2663;

public class PopCounter extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> countSelf = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("count-self")).description("Also count your own pops.")).defaultValue(true)).build());
   private final Setting<Boolean> chat = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("chat")).description("Announce each pop in chat.")).defaultValue(true)).build());
   private final Setting<Boolean> resetOnLeave = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("reset-on-leave")).description("Clear the counts when you leave the server.")).defaultValue(true))
            .build()
      );
   private final Map<UUID, Integer> pops = new HashMap<>();

   public PopCounter() {
      super(CrystalAddon.CATEGORY, "pop-counter", "Counts how many totems each player has popped.");
   }

   public void onActivate() {
      this.pops.clear();
   }

   public int getPops(UUID uuid) {
      return this.pops.getOrDefault(uuid, 0);
   }

   @EventHandler
   private void onPacket(Receive event) {
      if (event.packet instanceof class_2663 packet) {
         if (packet.method_11470() == 35) {
            if (this.mc.field_1687 != null) {
               if (packet.method_11469(this.mc.field_1687) instanceof class_1657 player) {
                  if (player != this.mc.field_1724 || (Boolean)this.countSelf.get()) {
                     int count = this.pops.merge(player.method_5667(), 1, Integer::sum);
                     if ((Boolean)this.chat.get()) {
                        this.info("%s popped %d totem%s.", new Object[]{player.method_5477().getString(), count, count == 1 ? "" : "s"});
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      if ((Boolean)this.resetOnLeave.get()) {
         this.pops.clear();
      }
   }
}
