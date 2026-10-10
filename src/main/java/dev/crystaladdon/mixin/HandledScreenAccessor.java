package dev.crystaladdon.mixin;

import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_465;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({class_465.class})
public interface HandledScreenAccessor {
   @Accessor("field_2776")
   int crystal$getX();

   @Accessor("field_2800")
   int crystal$getY();

   @Accessor("field_2787")
   class_1735 crystal$getFocusedSlot();

   // The screen's own slot click: what the swap-hands key does over a slot. Overridden by the creative screen, which
   // syncs the change with its own packets.
   @Invoker("method_2383")
   void crystal$onMouseClick(class_1735 slot, int slotId, int button, class_1713 actionType);
}
