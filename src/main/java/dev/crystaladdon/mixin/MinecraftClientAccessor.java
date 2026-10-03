package dev.crystaladdon.mixin;

import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_310.class})
public interface MinecraftClientAccessor {
   @Accessor("field_1752")
   int crystal$getItemUseCooldown();

   @Accessor("field_1752")
   void crystal$setItemUseCooldown(int var1);

   @Accessor("field_1771")
   int crystal$getAttackCooldown();
}
