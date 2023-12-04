import java.nio.channels.*;
import java.io.IOException;
import java.util.concurrent.locks.*;

public class AcceptHandler implements IAcceptHandler {
    private IReadWriteHandlerFactory rwhf;
    private Lock lock;

    public AcceptHandler(IReadWriteHandlerFactory rwhf, Lock lock) {
        this.rwhf = rwhf;
        this.lock = lock;
    }

    public void handleException() {
        System.out.println("AcceptHandler: handleException()");
    }

    public void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();

        try {
            lock.lock();

            SocketChannel client = server.accept();

            if (client != null) {
                Debug.DEBUG("AcceptHandler: accepted connection from " + client, DebugType.NONSERVER);

                client.configureBlocking(false);

                IReadWriteHandler rwh = rwhf.createHandler();
                int ops = rwh.getInitOps();

                SelectionKey clientKey = client.register(key.selector(), ops);
                clientKey.attach(rwh);
            } else {
                Debug.DEBUG("AcceptHandler: null client", DebugType.NONSERVER);
            }
        } finally {
            lock.unlock();
        }
    }
}
