package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;

public class HotbarRefill extends CrystalModule {
   private final SettingGroup sgItems = this.settings.getDefaultGroup();
   private final SettingGroup sgGeneral = this.settings.createGroup("General");
   private final Setting<Boolean> crystals = this.sgItems
      .add(((Builder)((Builder)((Builder)new Builder().name("end-crystals")).defaultValue(true)).description("Keep end crystals in the hotbar.")).build());
   private final Setting<Boolean> obsidian = this.sgItems
      .add(((Builder)((Builder)((Builder)new Builder().name("obsidian")).defaultValue(true)).description("Keep obsidian in the hotbar.")).build());
   private final Setting<Boolean> totems = this.sgItems
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("totems")).defaultValue(true))
               .description("Keep a totem in the hotbar. Smart Totem still handles the offhand."))
            .build()
      );
   private final Setting<Boolean> anchors = this.sgItems
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("anchors-and-glowstone")).defaultValue(true))
               .description("Keep respawn anchors and glowstone in the hotbar."))
            .build()
      );
   private final Setting<Boolean> gapples = this.sgItems
      .add(((Builder)((Builder)((Builder)new Builder().name("gapples")).defaultValue(true)).description("Keep golden apples in the hotbar.")).build());
   private final Setting<Boolean> exp = this.sgItems
      .add(((Builder)((Builder)((Builder)new Builder().name("xp-bottles")).defaultValue(false)).description("Keep experience bottles in the hotbar.")).build());
   private final Setting<Integer> minCount = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("refill-below"))
                  .description("Top an item up once fewer than this many are in the hotbar."))
               .defaultValue(4))
            .min(1)
            .sliderMax(64)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("delay"))
                  .description("Ticks between moves. Too fast and the server drops the clicks."))
               .defaultValue(2))
            .min(0)
            .sliderMax(20)
            .build()
      );
   private final Setting<Boolean> pauseInScreens = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("pause-in-screens"))
                  .description("Do nothing while a container is open, so the clicks cannot go into a chest."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("chat-info")).description("Say what was topped up.")).defaultValue(false)).build());
   private int timer;

   public HotbarRefill() {
      super(CrystalAddon.CATEGORY, "hotbar-refill", "Tops the hotbar back up from your inventory.");
   }

   public void onDeactivate() {
      this.timer = 0;
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.field_1724 != null) {
         if (!(Boolean)this.pauseInScreens.get() || this.mc.field_1724.field_7512 == this.mc.field_1724.field_7498) {
            if (this.timer > 0) {
               this.timer--;
            } else {
               for (class_1792 item : this.wanted()) {
                  if (this.topUp(item)) {
                     this.timer = (Integer)this.delay.get();
                     return;
                  }
               }
            }
         }
      }
   }

   private List<class_1792> wanted() {
      List<class_1792> items = new ArrayList<>(7);
      if ((Boolean)this.crystals.get()) {
         items.add(class_1802.field_8301);
      }

      if ((Boolean)this.obsidian.get()) {
         items.add(class_1802.field_8281);
      }

      if ((Boolean)this.totems.get()) {
         items.add(class_1802.field_8288);
      }

      if ((Boolean)this.anchors.get()) {
         items.add(class_1802.field_23141);
         items.add(class_1802.field_8801);
      }

      if ((Boolean)this.gapples.get()) {
         items.add(class_1802.field_8367);
      }

      if ((Boolean)this.exp.get()) {
         items.add(class_1802.field_8287);
      }

      return items;
   }

   private boolean topUp(class_1792 item) {
      int inHotbar = this.countInHotbar(item);
      int wanted = item == class_1802.field_8288 ? 1 : (Integer)this.minCount.get();
      if (inHotbar >= wanted) {
         return false;
      } else {
         int source = this.findInBackpack(item);
         if (source == -1) {
            return false;
         } else {
            int target = this.findHotbarTarget(item);
            if (target == -1) {
               return false;
            } else {
               InvUtils.move().from(source).toHotbar(target - 0);
               if ((Boolean)this.chatInfo.get()) {
                  this.info("Topped up %s.", new Object[]{item.toString()});
               }

               return true;
            }
         }
      }
   }

   private int countInHotbar(class_1792 item) {
      int count = 0;

      for (int i = 0; i <= 8; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (stack.method_31574(item)) {
            count += stack.method_7947();
         }
      }

      if (this.mc.field_1724.method_6079().method_31574(item)) {
         count += this.mc.field_1724.method_6079().method_7947();
      }

      return count;
   }

   private int findInBackpack(class_1792 item) {
      for (int i = 9; i <= 35; i++) {
         if (this.mc.field_1724.method_31548().method_5438(i).method_31574(item)) {
            return i;
         }
      }

      return -1;
   }

   private int findHotbarTarget(class_1792 item) {
      for (int i = 0; i <= 8; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (stack.method_31574(item) && stack.method_7947() < stack.method_7914()) {
            return i;
         }
      }

      for (int ix = 0; ix <= 8; ix++) {
         if (this.mc.field_1724.method_31548().method_5438(ix).method_7960()) {
            return ix;
         }
      }

      return -1;
   }

   public String getInfoString() {
      int missing = 0;

      for (class_1792 item : this.wanted()) {
         int wanted = item == class_1802.field_8288 ? 1 : (Integer)this.minCount.get();
         if (this.countInHotbar(item) < wanted) {
            missing++;
         }
      }

      return missing == 0 ? null : missing + " low";
   }
}
