package demo.replace;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Bean thật phụ thuộc vào cả hai bean bị thay: nhận wrapper mock-only. */
@Service
public class Dispatcher {

    private final ConnectedClient client;
    private final ReplaceConfig.Broker remote;
    private final ReplaceConfig.Broker safe;

    Dispatcher(ConnectedClient client, @Qualifier("remote") ReplaceConfig.Broker remote, @Qualifier("safe") ReplaceConfig.Broker safe) {
        this.client = client;
        this.remote = remote;
        this.safe = safe;
    }

    public String dispatch(String message) {
        return client.ping() + "|" + remote.send(message) + "|" + safe.send(message);
    }
}
