package pproton;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.net.Server;
import arc.scene.ui.Button;
import arc.scene.ui.CheckBox;
import arc.scene.ui.layout.Cell;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.ClientSnapshotCallPacket;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.net.Host;
import mindustry.net.Net;
import mindustry.net.NetConnection;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

import java.io.IOException;
import java.lang.reflect.Field;

public class CursorSpooferMod extends Mod {

    public static boolean isEnabled() {
        return Core.settings.getBool("spoofer-enabled", true);
    }

    public static void setEnabled(boolean newState) {
        Core.settings.put("spoofer-enabled", newState);
    }

    @Override
    public void init() {
        if(Vars.headless) return;

        Events.on(ClientLoadEvent.class, event -> {
            installSnapshotMaskHook();
            initializeUI();
        });
    }

    private void initializeUI() {
        Vars.ui.settings.addCategory("Cursor Spoofer", Icon.eye, this::initCategoryUI);
    }
    private void initCategoryUI(SettingsTable table) {
        table.check("Enable Spoofer",
                Core.settings.getBool("spoofer-enabled", true),
                checked -> Core.settings.put("spoofer-enabled", checked)
        ).row();
        Cell[] ignoreShootingCheckbox = new Cell[1];

        ignoreShootingCheckbox[0] = table.check("Ignore(Disable) Shooting",
        Core.settings.getBool("spoofer-disable-shooting", false), checked -> {
            if (!Core.settings.getBool("spoofer-disable-shooting")) {
                showIgnoreShootingWarningDialog(ignoreShootingCheckbox[0]);
            } else {
                Core.settings.put("spoofer-disable-shooting", false);
            }
        });
        // Can't add .row() after a checkbox because .row() returns void.
        ignoreShootingCheckbox[0].row();
    }
    private void showIgnoreShootingWarningDialog(Cell checkbox) {
        BaseDialog dialog = new BaseDialog("Confirm");
        dialog.cont.add("Enable ignoring shooting?").fontScale(1.2f)
                .center().row();
        dialog.cont.add("You wouldn't be able to shoot!").center().row();
        dialog.cont.add("(only visually)").center().row();
        dialog.cont.table(buttons -> {
            buttons.button("Yes", () -> {
                Core.settings.put("spoofer-disable-shooting", true);
                dialog.hide();
            }).size(100f, 50f).pad(10f);
            buttons.button("No", () -> {
                Core.settings.put("spoofer-disable-shooting", false);
                checkbox.checked(false);
                dialog.hide();
            }).size(100f, 50f).pad(10f);
        }).center().row();
        dialog.show();
    }

    private void installSnapshotMaskHook(){
        try{
            Field providerField = Net.class.getDeclaredField("provider");
            providerField.setAccessible(true);

            Net.NetProvider current = (Net.NetProvider)providerField.get(Vars.net);
            if(current instanceof SnapshotMaskingProvider){
                return;
            }

            providerField.set(Vars.net, new SnapshotMaskingProvider(current));
            Log.info("[CursorSpoofer] Installed snapshot mask hook.");
        }catch(Throwable t){
            Log.err("[CursorSpoofer] Failed to install snapshot mask hook.", t);
        }
    }

    private static final class SnapshotMaskingProvider implements Net.NetProvider{
        private final Net.NetProvider delegate;

        private SnapshotMaskingProvider(Net.NetProvider delegate){
            this.delegate = delegate;
        }

        @Override
        public void connectClient(String ip, int port, Runnable success) throws IOException{
            delegate.connectClient(ip, port, success);
        }

        @Override
        public void sendClient(Object object, boolean reliable) {
            if(object instanceof ClientSnapshotCallPacket packet) {
                float oldPointerX = packet.pointerX;
                float oldPointerY = packet.pointerY;
                try {
                    if (!CursorSpooferMod.isEnabled()) return;
                    // Force reported cursor to unit position for this snapshot only.
                    packet.pointerX = packet.x;
                    packet.pointerY = packet.y;
                    if (Vars.player.unit() == null) return;
                    if (Core.settings.getBool("spoofer-disable-shooting")) {
                        delegate.sendClient(packet, reliable);
                        return;
                    }
                    if (!Vars.player.shooting()) delegate.sendClient(packet, reliable);
                } catch (Exception e) {
                    Log.err("[CursorSpoofer] Failed to send client snapshot.", e);
                } finally {
                    packet.pointerX = oldPointerX;
                    packet.pointerY = oldPointerY;
                    if (!CursorSpooferMod.isEnabled()) {
                        delegate.sendClient(packet, reliable);
                        return;
                    }
                    if (Vars.player.shooting() &&
                        !Core.settings.getBool("spoofer-disable-shooting")) delegate.sendClient(packet, reliable);
                }
                return;
            }

            delegate.sendClient(object, reliable);
        }

        @Override
        public void disconnectClient(){
            delegate.disconnectClient();
        }

        @Override
        public void discoverServers(Cons<Host> callback, Runnable done){
            delegate.discoverServers(callback, done);
        }

        @Override
        public void pingHost(String address, int port, Cons<Host> valid, Cons<Exception> failed){
            delegate.pingHost(address, port, valid, failed);
        }

        @Override
        public void hostServer(int port) throws IOException{
            delegate.hostServer(port);
        }

        @Override
        public Iterable<? extends NetConnection> getConnections(){
            return delegate.getConnections();
        }

        @Override
        public void closeServer(){
            delegate.closeServer();
        }

        @Override
        public void dispose(){
            delegate.dispose();
        }

        @Override
        public void setConnectFilter(Server.ServerConnectFilter connectFilter){
            delegate.setConnectFilter(connectFilter);
        }

        @Override
        public Server.ServerConnectFilter getConnectFilter(){
            return delegate.getConnectFilter();
        }
    }
}
