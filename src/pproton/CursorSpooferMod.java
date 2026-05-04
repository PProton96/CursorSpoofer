package pproton;

import arc.Events;
import arc.func.Cons;
import arc.net.Server;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.ClientSnapshotCallPacket;
import mindustry.mod.Mod;
import mindustry.net.Host;
import mindustry.net.Net;
import mindustry.net.NetConnection;

import java.io.IOException;
import java.lang.reflect.Field;

public class CursorSpooferMod extends Mod {

    @Override
    public void init() {
        if(Vars.headless) return;

        Events.on(ClientLoadEvent.class, event -> installSnapshotMaskHook());
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
                    // Force reported cursor to unit position for this snapshot only.
                    packet.pointerX = packet.x;
                    packet.pointerY = packet.y;
                    if (Vars.player.unit() != null && !Vars.player.shooting()) delegate.sendClient(packet, reliable);
                } catch (Exception e) {
                    Log.err("[CursorSpoofer] Failed to send client snapshot.", e);
                } finally {
                    packet.pointerX = oldPointerX;
                    packet.pointerY = oldPointerY;
                    if (Vars.player.shooting()) delegate.sendClient(packet, reliable);
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
