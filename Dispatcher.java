import java.nio.channels.*;
import java.io.IOException;
import java.util.*; // Set and Iterator

public class Dispatcher implements Runnable {
    private Selector selector;

    public Dispatcher() {
        try {
            selector = Selector.open();
        } catch (IOException ex) {
            System.out.println("Cannot create selector");
            ex.printStackTrace();
            System.exit(1);
        }    
    }

    public Selector selector() {
        return selector;
    }

    public void run() {
        while (true) {
            Debug.DEBUG("Enter select loop", DebugType.NONSERVER);

            try {
                selector.select();
            } catch (IOException ex) {
                ex.printStackTrace();
                break;
            }

            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> it = readyKeys.iterator();

            while (it.hasNext()) {
                SelectionKey key = (SelectionKey) it.next();
                it.remove();

                try {
                    if (key.isAcceptable()) {
                        IAcceptHandler ah = (IAcceptHandler) key.attachment();
                        ah.handleAccept(key);
                    }
                    if (key.isReadable() || key.isWritable()) {
                        IReadWriteHandler rwh = (IReadWriteHandler) key.attachment();

                        if (key.isReadable()) {
                            rwh.handleRead(key);
                        }
                        else if (key.isWritable()) {
                            rwh.handleWrite(key);
                        }
                    }
                } catch (IOException ex) {
                    Debug.DEBUG("Dispatcher: exception handling key " + key, DebugType.NONSERVER);

                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException e) {}
                }
            }
        }
    }
}