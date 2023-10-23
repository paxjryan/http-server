import java.nio.channels.*;
import java.net.*;
import java.io.IOException;
import java.io.File;

public class Server {
    public static final int DEFAULT_PORT = 1223;
    public static final String DEFAULT_DOC_ROOT = "./www-root/";

    public static ServerConfig serverConfig;

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

        int port = DEFAULT_PORT;

        // open config file
        // TODO: ask about this!!!
        if (args.length > 0) {
            File f = new File(args[0]);

            if (!f.isFile()) {
                System.out.println("Invalid server config");
            } else {
                ServerConfig sc = new ServerConfig();
                try {
                    sc.parseConfigFile(f);
                    serverConfig = sc;
                    port = serverConfig.getPort();
                } catch (IOException ex) {}
            }
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

        Debug.DEBUG("port" + Integer.toString(port), DebugType.SERVER);
    }
}