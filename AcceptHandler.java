import java.nio.channels.*;
import java.io.IOException;

public class AcceptHandler implements IAcceptHandler {
    private IReadWriteHandlerFactory rwhf;

    public AcceptHandler(IReadWriteHandlerFactory rwhf) {
        this.rwhf = rwhf;
    }

    public void handleException() {
        System.out.println("AcceptHandler: handleException()");
    }

    public void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();

        SocketChannel client = server.accept();
        Debug.DEBUG("AcceptHandler: accepted connection from " + client, DebugType.NONSERVER);

        client.configureBlocking(false);

        IReadWriteHandler rwh = rwhf.createHandler();
        int ops = rwh.getInitOps();

        SelectionKey clientKey = client.register(key.selector(), ops);
        clientKey.attach(rwh);
    }
}
