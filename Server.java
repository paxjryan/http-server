import java.nio.channels.*;
import java.net.*;
import java.io.IOException;

public class Server {
    public static int DEFAULT_PORT = 1223;
    public static String DEFAULT_DOC_ROOT = "./www-root/";

    public static ServerSocketChannel openServerSocketChannel(int port) {
        ServerSocketChannel serverSocketChannel = null;
        
        try {
            serverSocketChannel = ServerSocketChannel.open();
            ServerSocket serverSocket = serverSocketChannel.socket();
            InetSocketAddress address = new InetSocketAddress(port);
            serverSocket.bind(address);

            // non-blocking channel
            serverSocketChannel.configureBlocking(false);

            Debug.DEBUG("Server listening on port " + Integer.toString(port), DebugType.SERVER);
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        return serverSocketChannel;
    }

    public static void main(String[] args) {
        Debug.DEBUG("Starting server", DebugType.SERVER_VERBOSE);

        Dispatcher dispatcher = new Dispatcher();

        // open port
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception ex) {
            port = DEFAULT_PORT;
        }
        ServerSocketChannel ssc = openServerSocketChannel(port);

        IReadWriteHandlerFactory rwhFactory = new ReadWriteHandlerFactory();
        AcceptHandler acceptor = new AcceptHandler(rwhFactory);

        Thread dispatcherThread;
        try {
            SelectionKey key = ssc.register(dispatcher.selector(), SelectionKey.OP_ACCEPT);
            key.attach(acceptor);

            dispatcherThread = new Thread(dispatcher);
            dispatcherThread.start();
        } catch (IOException ex) {
            System.out.println("Cannot register or start server");
            System.exit(1);
        }

        Debug.DEBUG(Integer.toString(port), DebugType.SERVER);
    }
}