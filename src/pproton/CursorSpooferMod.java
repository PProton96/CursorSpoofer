package pproton;

import arc.*;
import arc.math.geom.Position;
import arc.util.*;
import arc.util.io.Writes;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.ClientSnapshotCallPacket;
import mindustry.mod.*;
import mindustry.ui.dialogs.*;

import java.lang.reflect.Field;


public class CursorSpooferMod extends Mod {

    @Override
    public void init() {
        if (Vars.headless) return; // nothing to do on a dedicated server

        // Wait until the full client is loaded before touching the pool registry
        Events.on(ClientLoadEvent.class, e -> {
            try {
                injectSpoofedPool();
                Log.info("[CursorMask] Cursor masking active.");
            } catch (Throwable ex) {
                Log.err("[CursorMask] Failed to install hook — " +
                        "check field names against your build:", ex);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void injectSpoofedPool() throws Exception {
        // Arc stores all pools in a static ObjectMap<Class, Pool>.
        // We replace the entry for ClientSnapshotPacket with our own pool
        // so that Pools.obtain(ClientSnapshotPacket.class) returns our subclass.
        Field typePools = Pools.class.getDeclaredField("typePools");
        typePools.setAccessible(true);

        // The map is arc.struct.ObjectMap — cast is safe here
        var map = (arc.struct.ObjectMap<Class, Pool>) typePools.get(null);

        map.put(ClientSnapshotCallPacket.class, new Pool<ClientSnapshotCallPacket>() {
            @Override
            protected ClientSnapshotCallPacket newObject() {
                return new SpoofedSnapshotPacket();
            }
        });
    }

    private static class SpoofedSnapshotPacket extends ClientSnapshotCallPacket {

        @Override
        public void write(Writes stream) {
            // Only mask in an active multiplayer session where we have a live unit
            if (Vars.net.client() && Vars.player != null && !Vars.player.dead()) {
                float realX = pointerX, realY = pointerY;

                // Replace with our unit's centre — what the server will store
                // as player.mouseX / player.mouseY for other clients to see
                pointerX = Vars.player.x;
                pointerY = Vars.player.y;

                super.write(stream); // serialise with spoofed values

                // Restore originals (the object goes back to the pool afterwards)
                pointerX = realX;
                pointerY = realY;
            } else {
                super.write(stream); // singleplayer / not yet connected — send normally
            }
        }
    }

    public CursorSpooferMod() {
        Log.info("Loaded ExampleJavaMod constructor.");

        //listen for game load event
        Events.on(ClientLoadEvent.class, e -> {
            //show dialog upon startup
            Time.runTask(10f, () -> {
                BaseDialog dialog = new BaseDialog("frog");
                dialog.cont.add("behold").row();
                //mod sprites are prefixed with the mod name (this mod is called 'example-java-mod' in its config)
                dialog.cont.image(Core.atlas.find("cursor-spoofer-frog")).pad(20f).row();
                dialog.cont.button("I see", dialog::hide).size(100f, 50f);
                dialog.show();
            });
        });


    }

//    @Override
//    public void init() {
//        Events.run(Trigger.update, () -> {
//            // Only run if we are in a multiplayer game and have a unit
//            if (Vars.player.unit() != null && !Vars.player.unit().dead) {
//                // Check if player is shooting
//                boolean isShooting = Vars.player.shooting;
//
//                if (!isShooting) {
//                    float uX = Vars.player.unit().x;
//                    float uY = Vars.player.unit().y;
//
//                    // Direct field assignment (like New-Controls mod does)
//                    Vars.player.mouseX = uX;
//                    Vars.player.mouseY = uY;
//
//                    // Also sync unit aim (like New-Controls aimLook does)
//                    Vars.player.unit().aimX(uX);
//                    Vars.player.unit().aimY(uY);
//                    Vars.player.unit().aimLook(uX, uY);
//                }
//            }
//        });
//    }

    @Override
    public void loadContent(){
        Log.info("Loading some example content.");
    }

}
