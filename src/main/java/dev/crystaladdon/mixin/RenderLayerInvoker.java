package dev.crystaladdon.mixin;

import net.minecraft.class_12247;
import net.minecraft.class_1921;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({class_1921.class})
public interface RenderLayerInvoker {
   @Invoker("method_75940")
   static class_1921 crystal$of(String name, class_12247 setup) {
      throw new AssertionError();
   }
}
