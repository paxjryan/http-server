// Code modified from https://zoo.cs.yale.edu/classes/cs434/cs434-2023-fall/assignments/programming-proj1/examples/SelectServer/EchoLineReadWriteHandler.java

import java.nio.*;
import java.nio.channels.SelectionKey;
import java.io.IOException;

public class ReadWriteHandler implements IReadWriteHandler {
    private ByteBuffer inBuffer;
    private ByteBuffer outBuffer;

    private StringBuffer request;

    private enum State {
        READING_REQUEST, 
        PROCESSING_REQUEST,
        SENDING_RESPONSE
    }
    private State state;

    public ReadWriteHandler() {
        inBuffer = ByteBuffer.allocate(4096);
        outBuffer = ByteBuffer.allocate(4096);

        state = State.READING_REQUEST;
    }

    public int getInitOps() {
        return SelectionKey.OP_READ;
    }

    public void handleException() {
        System.out.println("ReadWriteHandler: handleException()");
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

		Debug.DEBUG("ReadWriteHandler: request fully read");
	}

	private void updateSelectorState(SelectionKey key) throws IOException {
		Debug.DEBUG("updating selector state ...", DebugType.NONSERVER);

		if (channelClosed)
			return;

		/*
		 * if (responseSent) { Debug.DEBUG(
		 * "***Response sent; shutdown connection"); client.close();
		 * dispatcher.deregisterSelection(sk); channelClosed = true; return; }
		 */

		int nextState = key.interestOps();

        switch (state) {
            case State.READING_REQUEST:
                nextState = nextState | SelectionKey.OP_READ;
                nextState = nextState & ~SelectionKey.OP_WRITE;
                Debug.DEBUG("New state: reading -> +Read -Write", DebugType.NONSERVER);
                break;
            case State.PROCESSING_REQUEST:
                nextState = nextState & ~SelectionKey.OP_READ;
                nextState = nextState & ~SelectionKey.OP_WRITE;
                Debug.DEBUG("New state: processing -> -Read -Write", DebugType.NONSERVER);
                break;
            case State.SENDING_RESPONSE:
                nextState = nextState & ~SelectionKey.OP_READ;
                nextState = nextState | SelectionKey.OP_WRITE;
                Debug.DEBUG("New state: writing -> -Read +Write", DebugType.NONSERVER);
                break;
        }

		key.interestOps(nextState);
	}

	public void handleWrite(SelectionKey key) throws IOException {
		Debug.DEBUG("ReadWriteHandler: data ready to be written");

		// process data
		SocketChannel client = (SocketChannel) key.channel();
		Debug.DEBUG("handleWrite: Write data to connection " + client + "; from buffer " + outBuffer, DebugType.NONSERVER);
		int writeBytes = client.write(outBuffer);
		Debug.DEBUG("handleWrite: write " + writeBytes + " bytes; after write " + outBuffer, DebugType.NONSERVER);

		if (state == State.SENDING_RESPONSE && (outBuffer.remaining() == 0)) {
			state = State.READING_REQUEST;
			Debug.DEBUG("handleWrite: response sent", DebugType.NONSERVER);
		}

		// update state
		updateSelectorState(key);

		// try {Thread.sleep(5000);} catch (InterruptedException e) {}
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
			// outBuffer = ByteBuffer.allocate( inBuffer.remaining() );
			while (!requestComplete && inBuffer.hasRemaining() && request.length() < request.capacity()) {
				char ch = (char) inBuffer.get();
				Debug.DEBUG("Ch: " + ch, DebugType.NONSERVER);
				request.append(ch);
				if (ch == '\r' || ch == '\n') {
					state = State.PROCESSING_REQUEST;
					// client.shutdownInput();
					Debug.DEBUG("handleRead: find terminating chars", DebugType.NONSERVER);
				} 
			} 
		}

		inBuffer.clear();

		if (state == State.PROCESSING_REQUEST) {
			generateResponse();
		}

	}

	private void generateResponse() {
		for (int i = 0; i < request.length(); i++) {
			char ch = (char) request.charAt(i);

			ch = Character.toUpperCase(ch);

			outBuffer.put((byte) ch);
		}
		outBuffer.flip();
		state = State.SENDING_RESPONSE;
	} 

}
