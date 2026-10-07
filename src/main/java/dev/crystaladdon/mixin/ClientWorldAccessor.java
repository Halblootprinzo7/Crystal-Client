package dev.crystaladdon.mixin;

import net.minecraft.class_638;
import net.minecraft.class_7202;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_638.class})
public interface ClientWorldAccessor {
   // The client world's PendingUpdateManager: the blocks a click of ours predicted that the server has not answered yet.
   @Accessor("field_37951")
   class_7202 crystal$getPendingUpdates();
}
