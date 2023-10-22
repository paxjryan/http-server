// Code modified from https://zoo.cs.yale.edu/classes/cs434/cs434-2023-fall/assignments/programming-proj1/examples/SelectServer/EchoLineReadWriteHandler.java

import java.nio.*;
import java.nio.channels.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
// for response header Date
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;

public class ReadWriteHandler implements IReadWriteHandler {
    private ByteBuffer inBuffer;
    private ByteBuffer outBuffer;

    private StringBuffer request;
    private StringBuffer responseBody;
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
        responseBody = new StringBuffer(4096);
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
            // try {
            //     Thread.sleep(1000);
            // } catch (InterruptedException ex) {

            // }

            try {
                key.channel().close();
                key.cancel();
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
			state = State.SENDING_RESPONSE;
			Debug.DEBUG("handleRead: readBytes == -1", DebugType.NONSERVER);
		} else {
			inBuffer.flip(); // read input
			// outBuffer = ByteBuffer.allocate( inBuffer.remaining() + 3 );        

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

        String url = r.getReqUrl();
        // TODO: check url safety
        File f = mapUrlToFile(url);

        // determining Connection from headers
        String protocol = r.getReqProtocol();
        String conn = r.lookupHeader("Connection");
        if (protocol.equals("HTTP/1.0")) {
            // HTTP/1.0 default is close, need to specify to keep alive
            keepalive = (conn != null && conn.equals("keep-alive"));
        } else {
            // HTTP/1.1+ default is keep-alive, need to specify close 
            keepalive = (conn == null || !conn.equals("close"));
        }

        if (r.getReqMethod() == Request.ReqMethod.GET) {
            Debug.DEBUG("method type GET", DebugType.NONSERVER);
        } else if (r.getReqMethod() == Request.ReqMethod.POST) {
            Debug.DEBUG("method type POST", DebugType.NONSERVER);
        }
        Debug.DEBUG("protocol: " + protocol, DebugType.NONSERVER);
        Debug.DEBUG("keep-alive: " + String.valueOf(keepalive), DebugType.NONSERVER);

        // Debug.DEBUG("req length: " + Integer.toString(request.length()), DebugType.NONSERVER);
		// for (int i = 0; i < request.length(); i++) {
		// 	char ch = (char) request.charAt(i);

		// 	ch = Character.toUpperCase(ch);

		// 	outBuffer.put((byte) ch);
		// }

        // Output response status line
        // TODO: change to reflect correct status code and message
        bufferWriteString(outBuffer, protocol + " 200 OK");

        // Output date header
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(("eee, dd MMM uuuu HH:mm:ss"));
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime gmtNow = now.withZoneSameInstant(ZoneId.of("GMT"));
        bufferWriteString(outBuffer, "Date: " + dtf.format(gmtNow) + " GMT");

        // Ouput server header
        bufferWriteString(outBuffer, "Server: aPAXche/1.0.0 (Ubuntu)");

        // Output last-modified header
        ZonedDateTime fileModifiedTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
        ZonedDateTime fileModifiedGmtTime = fileModifiedTime.withZoneSameInstant(ZoneId.of("GMT"));
        bufferWriteString(outBuffer, "Last-Modified: " + dtf.format(fileModifiedGmtTime) + " GMT");

        // TODO: Output content-type header
        bufferWriteString(outBuffer, "Content-Type: ");

        // Output content-length header
        bufferWriteString(outBuffer, "Content-Length: " + Integer.toString((int) f.length()));

        // CRLF: End of headers, beginning of reponse body
        outBuffer.put((byte) '\r');
        outBuffer.put((byte) '\n');


        // Output response body
        if (f != null) {
            outputResponseBody(f);
        }

        outBuffer.put((byte) '\r');
        outBuffer.put((byte) '\n');
		outBuffer.flip();
        request.delete(0, request.length());
		state = State.SENDING_RESPONSE;
	} 

    private File mapUrlToFile(String url) {
        // ignore leading /
        if (url.startsWith("/")) {
            url = url.substring(1);
        }

        String fileName = url;

        File f = new File(fileName);
        if (!f.isFile()) {
            // TODO: output error 404 not found
            f = null;
        }

        return f;
    }

    private void outputResponseBody(File f) {
        try {
            FileInputStream fileInputStream = new FileInputStream(f);
            int numBytes = (int) f.length();

            for (int i = 0; i < numBytes; i++) {
                responseBody.append((char) fileInputStream.read());
            }

            bufferWriteString(outBuffer, responseBody.toString());
        } catch (IOException ex) {
            // TODO
        }
        
    }

    // careful, could overwrite buf capacity
    private static void bufferWriteString(ByteBuffer buf, String s) {
        for (int i = 0; i < s.length(); i++) {
            buf.put((byte) s.charAt(i));
        }
        buf.put((byte) '\r');
        buf.put((byte) '\n');
    }
}
