import java.nio.channels.SelectionKey;
import java.io.IOException;

public interface IAcceptHandler {
    public void handleAccept(SelectionKey key) throws IOException;
}
