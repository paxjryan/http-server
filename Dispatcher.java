import java.nio.channels.*;
import java.io.IOException;
import java.util.*; // Set and Iterator

// import javax.swing.plaf.basic.BasicTreeUI.SelectionModelPropertyChangeHandler;

public class Dispatcher extends Thread {
    private Selector selector;
    private int id;

    public Dispatcher(int id) {
        try {
            selector = Selector.open();
        } catch (IOException ex) {
            System.out.println("Cannot create selector");
            ex.printStackTrace();
            System.exit(1);
        } 
        this.id = id;
    }

    public Selector selector() {
        return selector;
    }

    public int getDispatcherId() {
        return id;
    }

    public void run() {
        while (!Thread.interrupted()) {
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
                SelectionKey key = it.next();
                it.remove();

                try {
                    if (key.isAcceptable()) {
                        IAcceptHandler ah = (IAcceptHandler) key.attachment();
                        Debug.DEBUG("dispatcher " + Integer.toString(id) + " accept", DebugType.SERVER);
                        ah.handleAccept(key);
                    }
                    if (key.isReadable() || key.isWritable()) {
                        IReadWriteHandler rwh = (IReadWriteHandler) key.attachment();

                        if (key.isReadable()) {                            
                            Debug.DEBUG("dispatcher " + Integer.toString(id) + " read", DebugType.SERVER);
                            rwh.handleRead(key);
                        }
                        else if (key.isWritable()) {
                            Debug.DEBUG("dispatcher " + Integer.toString(id) + " write", DebugType.SERVER);
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