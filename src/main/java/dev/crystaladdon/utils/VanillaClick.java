package dev.crystaladdon.utils;

import dev.crystaladdon.mixin.MinecraftClientAccessor;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1799;
import net.minecraft.class_2680;
import net.minecraft.class_2879;
import net.minecraft.class_310;
import net.minecraft.class_3489;
import net.minecraft.class_3965;
import net.minecraft.class_9334;
import net.minecraft.class_1269.class_9857;
import net.minecraft.class_1269.class_9860;
import net.minecraft.class_1269.class_9861;

public final class VanillaClick {
   private static final class_310 mc = class_310.method_1551();

   private VanillaClick() {
   }

   public static boolean reaches(class_3965 hit, class_1268 hand) {
      if (hand == class_1268.field_5808) {
         return true;
      } else if (mc.field_1724 != null && mc.field_1687 != null) {
         class_1799 main = mc.field_1724.method_6047();
         boolean inert = main.method_7960() || main.method_31573(class_3489.field_42611) || main.method_57826(class_9334.field_54274);
         if (!inert) {
            return false;
         } else if (hit == null) {
            return true;
         } else {
            class_2680 state = mc.field_1687.method_8320(hit.method_17777());
            return !LegitPlace.isInteractive(state);
         }
      } else {
         return false;
      }
   }

   public static class_1268 use(class_3965 hit, boolean visibleSwing) {
      if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1761 == null) {
         return null;
      } else if (!mc.field_1761.method_2923() && !mc.field_1724.method_3144()) {
         ((MinecraftClientAccessor)mc).crystal$setItemUseCooldown(4);
         float viewYaw = mc.field_1724.method_36454();
         float viewPitch = mc.field_1724.method_36455();
         mc.field_1724.method_36456((float)LegitPlace.currentYaw());
         mc.field_1724.method_36457((float)LegitPlace.currentPitch());

         class_1268 var4;
         ClickGate.clickStart();
         try {
            var4 = chain(hit, visibleSwing);
         } finally {
            ClickGate.clickEnd();
            mc.field_1724.method_36456(viewYaw);
            mc.field_1724.method_36457(viewPitch);
         }

         return var4;
      } else {
         return null;
      }
   }

   private static class_1268 chain(class_3965 hit, boolean visibleSwing) {
      for (class_1268 hand : class_1268.values()) {
         class_1799 stack = mc.field_1724.method_5998(hand);
         if (!stack.method_45435(mc.field_1687.method_45162())) {
            return null;
         }

         if (hit != null) {
            class_1269 result = mc.field_1761.method_2896(mc.field_1724, hand, hit);
            if (result instanceof class_9860 success) {
               if (success.comp_2909() == class_9861.field_52427) {
                  swing(hand, visibleSwing);
               }

               return hand;
            }

            if (result instanceof class_9857) {
               return null;
            }
         }

         if (!stack.method_7960() && mc.field_1761.method_2919(mc.field_1724, hand) instanceof class_9860 success) {
            if (success.comp_2909() == class_9861.field_52427) {
               swing(hand, visibleSwing);
            }

            return hand;
         }
      }

      return null;
   }

   private static void swing(class_1268 hand, boolean visible) {
      if (visible) {
         mc.field_1724.method_6104(hand);
      } else {
         mc.method_1562().method_52787(new class_2879(hand));
      }
   }
}
