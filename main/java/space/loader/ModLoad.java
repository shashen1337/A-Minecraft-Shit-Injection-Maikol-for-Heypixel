package space.loader;

import net.minecraftforge.fml.common.Mod;
import space.Core;

@Mod("space")
public class ModLoad {
    public ModLoad() {
        //Core.mode = true;
        new Core().initialize();
    }
}
