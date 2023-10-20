// Code modified from https://zoo.cs.yale.edu/classes/cs434/cs434-2023-fall/assignments/programming-proj1/examples/SelectServer/EchoLineReadWriteHandler.java

import java.nio.*;
import java.nio.channels.*;
import java.io.IOException;

public class ReadWriteHandler implements IReadWriteHandler {
    private ByteBuffer inBuffer;
    private ByteBuffer outBuffer;

    private StringBuffer request;
    private boolean keepalive;

    private enum State {
        READING_REQUEST, 
        PROCESSING_REQUEST,
        SENDING_RESPONSE, 
        CONN_CLOSED
    }
    private State state;

    public ReadWriteHandler() {
        inBuffer = ByteBuffer.allocate(4096);
        outBuffer = ByteBuffer.allocate(4096);

        state = State.READING_REQUEST;

        request = new StringBuffer(4096);
        keepalive = false;
    }

    public int getInitOps() {
        return SelectionKey.OP_READ;
    }

    public void handleException() {
        System.out.println("ReadWriteHandler: handleException()");
    }

    private void updateSelectorState(SelectionKey key) throws IOException {
		Debug.DEBUG("updating selector state ...", DebugType.NONSERVER);

        if (state == State.CONN_CLOSED) {
            Debug.DEBUG("Connection closed; shutdown", DebugType.NONSERVER);

            key.cancel();
            try {
                key.channel().close();
                // in a more general design, call have a handleException
            } catch (IOException cex) {
            }
            return;
        }

		int nextState = key.interestOps();

        switch (state) {
            case READING_REQUEST:
                nextState = nextState | SelectionKey.OP_READ;
                nextState = nextState & ~SelectionKey.OP_WRITE;
                Debug.DEBUG("New state: reading -> +Read -Write", DebugType.NONSERVER);
                break;
            case PROCESSING_REQUEST:
                nextState = nextState & ~SelectionKey.OP_READ;
                nextState = nextState & ~SelectionKey.OP_WRITE;
                Debug.DEBUG("New state: processing -> -Read -Write", DebugType.NONSERVER);
                break;
            case SENDING_RESPONSE:
                nextState = nextState & ~SelectionKey.OP_READ;
                nextState = nextState | SelectionKey.OP_WRITE;
                Debug.DEBUG("New state: writing -> -Read +Write", DebugType.NONSERVER);
                break;
        }

		key.interestOps(nextState);
	}

    public void handleRead(SelectionKey key) throws IOException {
		// a connection is ready to be read
		Debug.DEBUG("ReadWriteHandler: connection ready to be read", DebugType.NONSERVER);

		if (state != State.READING_REQUEST) { // this call should not happen, ignore
			return;
		}

		// process data
		processInBuffer(key);

		// update state
		updateSelectorState(key);
	}

	public void handleWrite(SelectionKey key) throws IOException {
		Debug.DEBUG("ReadWriteHandler: data ready to be written", DebugType.NONSERVER);

		// process data
		SocketChannel client = (SocketChannel) key.channel();
		Debug.DEBUG("handleWrite: Write data to connection " + client + "; from buffer " + outBuffer, DebugType.NONSERVER);
		int writeBytes = client.write(outBuffer);
		Debug.DEBUG("handleWrite: write " + writeBytes + " bytes; after write " + outBuffer, DebugType.NONSERVER);

		if (state == State.SENDING_RESPONSE && (outBuffer.remaining() == 0)) {
            if (keepalive) 
			    state = State.READING_REQUEST;
            else
                state = State.CONN_CLOSED;

			Debug.DEBUG("handleWrite: response sent", DebugType.NONSERVER);
		}

        outBuffer.clear();
        if (outBuffer.remaining() == 0)
            Debug.DEBUG("slay", DebugType.NONSERVER);

		// update state
		updateSelectorState(key);
	}

	private void processInBuffer(SelectionKey key) throws IOException {
		Debug.DEBUG("processInBuffer", DebugType.NONSERVER);

		SocketChannel client = (SocketChannel) key.channel();
		int readBytes = client.read(inBuffer);
		Debug.DEBUG("handleRead: Read data from connection " + client + " for " + readBytes + " byte(s); to buffer "
				+ inBuffer, DebugType.NONSERVER);

		if (readBytes == -1) { // end of stream
			state = State.PROCESSING_REQUEST;
			Debug.DEBUG("handleRead: readBytes == -1", DebugType.NONSERVER);
		} else {
			inBuffer.flip(); // read input
			outBuffer = ByteBuffer.allocate( inBuffer.remaining() + 1 );        

			while (state != State.PROCESSING_REQUEST && inBuffer.hasRemaining() && request.length() < request.capacity()) {
				char ch = (char) inBuffer.get();
				Debug.DEBUG("Ch: " + String.valueOf(ch) + " (" + Integer.toString(ch) + ")", DebugType.NONSERVER);
				request.append(ch);

                // "\n\r\n" (Windows) or "\r\r\n" (Mac) signal end of headers
                if (request.length() >= 3 && 
                        (request.substring(request.length() - 3).equals("\n\r\n") || 
                        request.substring(request.length() - 3).equals("\r\r\n"))) {
                    state = State.PROCESSING_REQUEST;
                    Debug.DEBUG("handleRead: find terminating chars", DebugType.NONSERVER);
                }
			} 
		}

		inBuffer.clear();

		if (state == State.PROCESSING_REQUEST) {
			generateResponse();
		}
	}

	private void generateResponse() throws IOException {
        Request r = new Request();
        r.parseRequest(request.toString());
        Debug.DEBUG(r.toString(), DebugType.NONSERVER);

        if (r.getReqMethod() == Request.ReqMethod.GET) {
            Debug.DEBUG("method type GET", null);
        } else if (r.getReqMethod() == Request.ReqMethod.POST) {
            Debug.DEBUG("method type POST", null);
        }

        // Debug.DEBUG("req length: " + Integer.toString(request.length()), DebugType.NONSERVER);
		// for (int i = 0; i < request.length(); i++) {
		// 	char ch = (char) request.charAt(i);

		// 	ch = Character.toUpperCase(ch);

		// 	outBuffer.put((byte) ch);
		// }
        // outBuffer.put((byte) '\n');
		// outBuffer.flip();
        // request.delete(0, request.length());
		// state = State.SENDING_RESPONSE;
	} 
}
