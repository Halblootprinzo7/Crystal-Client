package com.messerocks.crystal.mixin;

import net.minecraft.class_1747;
import net.minecraft.class_1750;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({class_1747.class})
public interface BlockItemInvoker {
   @Invoker("method_7707")
   class_2680 crystal$getPlacementState(class_1750 var1);
}
