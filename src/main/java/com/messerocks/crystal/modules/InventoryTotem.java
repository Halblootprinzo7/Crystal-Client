package com.messerocks.crystal.modules;

import com.messerocks.crystal.CrystalAddon;
import com.messerocks.crystal.CrystalModule;
import java.lang.reflect.Field;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_11908;
import net.minecraft.class_1735;
import net.minecraft.class_1802;
import net.minecraft.class_3675;
import net.minecraft.class_465;
import net.minecraft.class_3675.class_306;
import org.lwjgl.glfw.GLFW;

public class InventoryTotem extends CrystalModule {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> health = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("health"))
               .description("Only act when your health plus absorption is at or below this. 20 acts whenever the offhand is empty of totems."))
            .defaultValue(20.0)
            .min(1.0)
            .sliderMax(36.0)
            .build()
      );
   private final Setting<Integer> settleTicks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("settle-ticks"))
                  .description(
                     "Ticks to wait after moving the cursor before the key goes in. Vanilla only fills focusedSlot while the screen renders under the pointer, so pressing in the same tick would find nothing there."
                  ))
               .defaultValue(2))
            .min(1)
            .sliderRange(1, 10)
            .build()
      );
   private final Setting<Boolean> restoreCursor = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("restore-cursor"))
                  .description("Put the pointer back where it was afterwards, so it does not stay parked on the totem slot."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> chatInfo = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat-info"))
                  .description("Say what it did."))
               .defaultValue(false))
            .build()
      );
   private static Field screenX;
   private static Field screenY;
   private int waited = -1;
   private double returnX;
   private double returnY;

   public InventoryTotem() {
      super(CrystalAddon.CATEGORY, "inventory-totem", "Moves the cursor onto a totem in your open inventory and presses the swap-hands key.");
   }

   public void onDeactivate() {
      this.waited = -1;
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.field_1724 != null) {
         if (this.mc.field_1755 instanceof class_465<?> screen) {
            if (this.waited >= 0) {
               if (++this.waited >= (Integer)this.settleTicks.get()) {
                  this.waited = -1;
                  this.press(screen);
               }
            } else if (!this.mc.field_1724.method_6079().method_31574(class_1802.field_8288)) {
               if (!(EntityUtils.getTotalHealth(this.mc.field_1724) > (Double)this.health.get())) {
                  if (screen.method_17577().method_34255().method_7960()) {
                     class_1735 totem = this.findTotem(screen);
                     if (totem != null) {
                        this.moveTo(screen, totem);
                     }
                  }
               }
            }
         } else {
            this.waited = -1;
         }
      }
   }

   private class_1735 findTotem(class_465<?> screen) {
      for (class_1735 slot : screen.method_17577().field_7761) {
         if (slot.method_7681() && slot.method_7677().method_31574(class_1802.field_8288) && slot.method_7674(this.mc.field_1724)) {
            return slot;
         }
      }

      return null;
   }

   private void moveTo(class_465<?> screen, class_1735 slot) {
      Integer left = read(screenX, screen);
      Integer top = read(screenY, screen);
      if (left != null && top != null) {
         double scale = this.mc.method_22683().method_4495();
         double x = (left + slot.field_7873 + 8) * scale;
         double y = (top + slot.field_7872 + 8) * scale;
         this.returnX = this.mc.field_1729.method_1603();
         this.returnY = this.mc.field_1729.method_1604();
         GLFW.glfwSetCursorPos(this.mc.method_22683().method_4490(), x, y);
         this.waited = 0;
      } else {
         this.error("Could not read the screen offsets - Minecraft's internals have changed.", new Object[0]);
         this.toggle();
      }
   }

   private void press(class_465<?> screen) {
      class_306 key = class_3675.method_15981(this.mc.field_1690.field_1831.method_1428());
      if (key.method_1444() == -1) {
         this.warning("No key is bound to Swap Item With Offhand, so there is nothing to press.", new Object[0]);
         this.toggle();
      } else {
         screen.method_25404(new class_11908(key.method_1444(), 0, 0));
         if ((Boolean)this.chatInfo.get()) {
            this.info("Totem moved to the offhand.", new Object[0]);
         }

         if ((Boolean)this.restoreCursor.get()) {
            GLFW.glfwSetCursorPos(this.mc.method_22683().method_4490(), this.returnX, this.returnY);
         }
      }
   }

   private static Integer read(Field field, class_465<?> screen) {
      try {
         return field == null ? null : field.getInt(screen);
      } catch (Throwable var3) {
         return null;
      }
   }

   static {
      try {
         screenX = class_465.class.getDeclaredField("x");
         screenY = class_465.class.getDeclaredField("y");
         screenX.setAccessible(true);
         screenY.setAccessible(true);
      } catch (Throwable var1) {
         screenX = null;
         screenY = null;
      }
   }
}
