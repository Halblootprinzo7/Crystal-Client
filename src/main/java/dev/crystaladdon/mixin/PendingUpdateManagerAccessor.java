package dev.crystaladdon.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.class_7202;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_7202.class})
public interface PendingUpdateManagerAccessor {
   // Block position (asLong) -> the server's state held back until the click that predicted it is acknowledged.
   @Accessor("field_37953")
   Long2ObjectOpenHashMap<?> crystal$getPending();
}
