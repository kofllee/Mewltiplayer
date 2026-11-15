package dev.mewltiplayer.core.transport;

import java.util.concurrent.CompletionStage;

public interface TailcatBridge extends AutoCloseable {
    CompletionStage<ConnectionInfo> join(String inviteCode);

    CompletionStage<String> share(int minecraftPort);

    CompletionStage<Void> stopJoin();

    CompletionStage<Void> stopShare();

    CompletionStage<BridgeStatus> status();

    CompletionStage<NetworkStatus> probe();

    @Override
    void close();
}
