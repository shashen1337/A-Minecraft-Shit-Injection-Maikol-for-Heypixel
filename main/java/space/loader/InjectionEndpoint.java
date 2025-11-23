package space.loader;

import space.Core;

public class InjectionEndpoint {

    public static void Load() {
        //Core.mode = false;
        new space.Core().initialize();
    }
}
