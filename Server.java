import java.net.*;

public class Server {
    public static void main(String[] args) {
        Debug.DEBUG("SERVER: hello world", DebugType.SERVER);

        Dispatcher dispatcher = new Dispatcher();
        Thread dispatcherThread = new Thread(dispatcher);
        dispatcherThread.start();
    }
}