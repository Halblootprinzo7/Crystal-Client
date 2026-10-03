package com.messerocks.crystal.mixin;

import net.minecraft.class_1735;
import net.minecraft.class_465;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_465.class})
public interface HandledScreenAccessor {
   @Accessor("field_2776")
   int crystal$getX();

   @Accessor("field_2800")
   int crystal$getY();

   @Accessor("field_2787")
   class_1735 crystal$getFocusedSlot();
}
