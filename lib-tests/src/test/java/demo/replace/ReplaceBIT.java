package demo.replace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/** Hai lớp song song trên cùng context thay thế: mỗi test stub riêng, không thấy stub của nhau. */
class ReplaceBIT extends ReplaceBaseIT {

    @PerTestMock ConnectedClient client;
    @PerTestMock(name = "remote") ReplaceConfig.Broker remote;

    @Test
    void ownStubs() {
        when(client.ping()).thenReturn("pongB");
        when(remote.send("m")).thenReturn("remoteB");
        meetInSameContext("replace");

        assertThat(dispatcher.dispatch("m")).isEqualTo("pongB|remoteB|safe:m");
    }
}
