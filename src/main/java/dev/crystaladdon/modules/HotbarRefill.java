package dev.crystaladdon.modules;

import dev.crystaladdon.CrystalAddon;
import dev.crystaladdon.CrystalModule;
import dev.crystaladdon.utils.InventoryGuard;
import dev.crystaladdon.utils.ReactionClock;
import dev.crystaladdon.utils.TotemRules;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;

public class HotbarRefill extends CrystalModule {
   private static final int MIN_DELAY = 2;
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
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("gapples")).defaultValue(true))
               .description("Keep golden apples in the hotbar. Enchanted and normal ones count together; enchanted ones are fetched first."))
            .build()
      );
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
                  .description(
                     "Ticks between two of this module's moves, at least 2 and spread by Stealth's timing-jitter. The shared half second between any two inventory moves applies on top."
                  ))
               .defaultValue(2))
            .min(2)
            .sliderRange(2, 20)
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("chat-info")).description("Say what was topped up.")).defaultValue(false)).build());
   private int timer;
   private final ReactionClock clock = new ReactionClock();
   private final TotemRules.InventoryReach reach = new TotemRules.InventoryReach();

   public HotbarRefill() {
      super(CrystalAddon.CATEGORY, "hotbar-refill", "Tops the hotbar back up from your inventory.");
   }

   public void onDeactivate() {
      this.timer = 0;
      this.clock.clear();
      this.reach.reset();
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         this.clock.tick();
         if (this.timer > 0) {
            this.timer--;
         }

         HotbarRefill.Move move = null;

         for (class_1792 item : this.wanted()) {
            HotbarRefill.Move candidate = this.plan(item);
            if (candidate == null) {
               this.clock.forget(item);
            } else if (this.clock.ready(item) && move == null) {
               move = candidate;
            }
         }

         if (move == null || this.timer > 0 || this.mc.field_1755 != null || !InventoryGuard.inventoryFree()) {
            this.reach.reset();
         } else if (this.reach.advance(move, SmartTotem.inventoryCouldBeOpen(1), () -> Stealth.pace(6))) {
            if (InventoryGuard.swapToHotbar(move.source(), move.hotbar())) {
               this.reach.reset();
               this.timer = Math.max(2, Stealth.pace(Math.max(2, (Integer)this.delay.get())));
               if ((Boolean)this.chatInfo.get()) {
                  this.info("Topped up %s.", new Object[]{move.item().toString()});
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
         items.add(class_1802.field_8463);
      }

      if ((Boolean)this.exp.get()) {
         items.add(class_1802.field_8287);
      }

      return items;
   }

   private HotbarRefill.Move plan(class_1792 item) {
      int inHotbar = this.countInHotbar(item);
      int wanted = item == class_1802.field_8288 ? 1 : (Integer)this.minCount.get();
      if (inHotbar >= wanted) {
         return null;
      } else {
         for (class_1792 candidate : candidatesFor(item)) {
            int source = this.findInBackpack(candidate);
            if (source != -1) {
               int target = this.findHotbarTarget(candidate, this.mc.field_1724.method_31548().method_5438(source).method_7947());
               if (target != -1) {
                  return new HotbarRefill.Move(candidate, source, target - 0);
               }
            }
         }

         return null;
      }
   }

   private int countInHotbar(class_1792 item) {
      int count = 0;

      for (int i = 0; i <= 8; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (counts(stack, item)) {
            count += stack.method_7947();
         }
      }

      class_1799 offhand = this.mc.field_1724.method_6079();
      if (item != class_1802.field_8288 && counts(offhand, item)) {
         count += offhand.method_7947();
      }

      return count;
   }

   private static boolean counts(class_1799 stack, class_1792 item) {
      return item != class_1802.field_8463 ? stack.method_31574(item) : stack.method_31574(class_1802.field_8463) || stack.method_31574(class_1802.field_8367);
   }

   private static List<class_1792> candidatesFor(class_1792 item) {
      return item == class_1802.field_8463 ? List.of(class_1802.field_8367, class_1802.field_8463) : List.of(item);
   }

   private int findInBackpack(class_1792 item) {
      int best = -1;
      int bestCount = 0;

      for (int i = 9; i <= 35; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (stack.method_31574(item) && stack.method_7947() > bestCount) {
            best = i;
            bestCount = stack.method_7947();
         }
      }

      return best;
   }

   private int findHotbarTarget(class_1792 item, int count) {
      int best = -1;
      int bestCount = Integer.MAX_VALUE;

      for (int i = 0; i <= 8; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (stack.method_31574(item) && stack.method_7947() < count && stack.method_7947() < bestCount) {
            best = i;
            bestCount = stack.method_7947();
         }
      }

      if (best != -1) {
         return best;
      } else {
         for (int ix = 0; ix <= 8; ix++) {
            if (this.mc.field_1724.method_31548().method_5438(ix).method_7960()) {
               return ix;
            }
         }

         return -1;
      }
   }

   public String getInfoString() {
      if (this.mc.field_1724 == null) {
         return null;
      } else {
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

   private record Move(class_1792 item, int source, int hotbar) {
   }
}
