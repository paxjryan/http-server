import java.nio.channels.*;
import java.net.*;
import java.io.IOException;
import java.io.File;
import java.util.*;
import java.util.concurrent.locks.*;

// Note: finished. Also move the static methods sometime soon

public class Server {
    public static final int DEFAULT_PORT = 1223;
    public static final String DEFAULT_DOC_ROOT = "./www-root/";

    private static ServerConfig serverConfig;
    private static Selector selector;

    public static int getPort() {
        return serverConfig.getPort();
    }

    // TODO: move static method to ServerConfig?
    // returns docroot associated with serverName in serverConfig, 
    // or default doc root if no config,
    // or first configured server's doc root if serverName not in config
    public static String getVirtualHostDocRoot(String serverName) {
        if (serverConfig == null) {
            return DEFAULT_DOC_ROOT;
        }
        String docRoot = serverConfig.lookupVirtualHost(serverName);
        // 
        if (docRoot == null) {
            return getVirtualHostDocRoot();
        }
        return docRoot;
    }

    // TODO: move static method to ServerConfig?
    // returns docroot associated with first server in serverConfig,
    // or default doc root if no config
    public static String getVirtualHostDocRoot() {
        if (serverConfig == null) {
            return DEFAULT_DOC_ROOT;
        }
        String firstServerName = serverConfig.getFirstVirtualHost();
        if (firstServerName == null) {
            // TODO: throw lookup error
        }
        return serverConfig.lookupVirtualHost(firstServerName);
    }

    public static ServerSocketChannel openServerSocketChannel(int port) {
        ServerSocketChannel serverSocketChannel = null;
        
        try {
            serverSocketChannel = ServerSocketChannel.open();
            ServerSocket serverSocket = serverSocketChannel.socket();
            InetSocketAddress address = new InetSocketAddress(port);
            serverSocket.bind(address);

            // non-blocking channel
            serverSocketChannel.configureBlocking(false);

            Debug.PRINT("Server listening on port " + Integer.toString(port));
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }

        return serverSocketChannel;
    }

    public static void main(String[] args) {
        Debug.PRINT("Starting server");

        try {
            selector = Selector.open();
        } catch (IOException ex) {
            System.out.println("Cannot create selector");
            ex.printStackTrace();
            System.exit(1);
        }    

        int port = DEFAULT_PORT;
        int nSelectLoops = 1;

        // open config file
        // TODO: ASSUMES CONFIG FILE IS FIRST COMMAND-LINE ARGUMENT
        if (args.length > 0) {
            if (args.length != 2 || (!args[0].equals("-c") && !args[0].equals("-config"))) {
                System.out.println("Usage: java server [-c|-config] <config_file_name>");
                return;
            }

            File f = new File(args[1]);

            if (!f.isFile()) {
                System.out.println("Could not find server config file " + args[0] + "; using default configuration");
            } else {
                ServerConfig sc = new ServerConfig();
                try {
                    sc.parseConfigFile(f);
                    serverConfig = sc;
                    port = serverConfig.getPort();
                    nSelectLoops = serverConfig.getNSelectLoops();
                } catch (IOException ex) {
                    // TODO: throw server config file parsing error
                    System.out.println("Server config file parsing error");
                    System.exit(1);
                }
            }
        }

        ServerSocketChannel ssc = openServerSocketChannel(port);

        Lock acceptHandlerLock = new ReentrantLock();

        try {
            Dispatcher[] dispatchers = new Dispatcher[nSelectLoops];

            // start nSelectLoops select multiplexing loops
            for (int i = 0; i < nSelectLoops; i++) {
                Debug.DEBUG("starting dispatcher " + Integer.toString(i), DebugType.SERVER);
                
                Dispatcher dispatcher = new Dispatcher(i);

                IReadWriteHandlerFactory rwhFactory = new ReadWriteHandlerFactory();
                AcceptHandler acceptor = new AcceptHandler(rwhFactory, acceptHandlerLock);

                SelectionKey key = ssc.register(dispatcher.selector(), SelectionKey.OP_ACCEPT);
                key.attach(acceptor);

                dispatchers[i] = dispatcher;
                dispatcher.start();
            }
            // spin up management thread
            Manager manager = new Manager(dispatchers);
            Thread managerThread = new Thread(manager);
            managerThread.start();
        } catch (IOException ex) {
            System.out.println("Cannot register or start dispatcher thread");
            System.exit(1);
        }
    }
}