package dev.mewltiplayer.core.transport;

import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

public interface TailcatBridge extends AutoCloseable {
    CompletionStage<ConnectionInfo> join(String shareCode);

    CompletionStage<String> share(int minecraftPort);

    CompletionStage<String> share(int minecraftPort, Path identityFile);

    CompletionStage<Void> stopJoin();

    CompletionStage<Void> stopShare();

    CompletionStage<BridgeStatus> status();

    CompletionStage<NetworkStatus> probe();

    @Override
    void close();
}
