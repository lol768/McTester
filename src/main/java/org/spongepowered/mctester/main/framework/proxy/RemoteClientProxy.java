package org.spongepowered.mctester.main.framework.proxy;

import org.spongepowered.api.Sponge;
import org.spongepowered.mctester.main.McTester;
import org.spongepowered.mctester.main.message.ResponseWrapper;
import org.spongepowered.mctester.main.message.toclient.MessageRPCRequest;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.mctester.main.ServerOnly;
import org.spongepowered.mctester.main.framework.Client;

import java.util.concurrent.TimeUnit;

public class RemoteClientProxy extends BaseProxy {

    public RemoteClientProxy(SpongeExecutorService mainThreadExecutor, ProxyCallback callback) {
        super(null, mainThreadExecutor, callback);
    }

    public static Client newProxy(ProxyCallback callback) {
        RemoteClientProxy proxy = new RemoteClientProxy(McTester.INSTANCE.syncExecutor, callback);
        return proxy.makeProxy(Client.class);
    }

    @Override
    Object dispatch(InvocationData data) {
        if (Sponge.getServer().isMainThread()) {
            throw new IllegalStateException(String.format("You attempted to call '%s' from the main thread, probably by using the Scheduler API or TestUtils.batchActions.\n" +
                                            "All Client methods must be called directly or indirectly from your @Test method.", data.format()));
        }

        this.mainThreadExecutor.schedule(() -> McTester.INSTANCE.sendToPlayer(new MessageRPCRequest(new RemoteInvocationData(data))), 0, TimeUnit.SECONDS);

        try {
            // Blocking
            ResponseWrapper result = ServerOnly.INBOUND_QUEUE.take();
            return result.inner;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
