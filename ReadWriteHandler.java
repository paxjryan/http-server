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
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReadWriteHandler implements IReadWriteHandler {
    private ByteBuffer inBuffer;
    private StringBuffer requestBuffer;

    private Request request;
    private boolean keepalive;
    private String url;         // possibly modified during content selection; does not include doc_root

    private ByteBuffer outBuffer;
    private StringBuffer responseBody;

    private enum State {
        READING_REQUEST, 
        PROCESSING_REQUEST,
        SENDING_RESPONSE, 
        CONN_CLOSED
    }
    private State state;

    public ReadWriteHandler() {
        inBuffer = ByteBuffer.allocate(4096);
        requestBuffer = new StringBuffer(4096);

        keepalive = false;

        outBuffer = ByteBuffer.allocate(4096);
        responseBody = new StringBuffer(4096);

        state = State.READING_REQUEST;
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

			while (state != State.PROCESSING_REQUEST && inBuffer.hasRemaining() && requestBuffer.length() < requestBuffer.capacity()) {
				char ch = (char) inBuffer.get();
				Debug.DEBUG("Ch: " + String.valueOf(ch) + " (" + Integer.toString(ch) + ")", DebugType.NONSERVER);
				requestBuffer.append(ch);

                // "\n\r\n" (Windows) or "\r\r\n" (Mac) signal end of headers
                if (requestBuffer.length() >= 3 && 
                        (requestBuffer.substring(requestBuffer.length() - 3).equals("\n\r\n") || 
                        requestBuffer.substring(requestBuffer.length() - 3).equals("\r\r\n"))) {
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
        request = new Request();
        request.parseRequest(requestBuffer.toString());
        Debug.DEBUG(request.toString(), DebugType.NONSERVER);

        // determining Connection from headers and update keepalive variable
        updateKeepAlive();

        // if (request.getReqMethod() == Request.ReqMethod.GET) {
        //     Debug.DEBUG("method type GET", DebugType.NONSERVER);
        // } else if (request.getReqMethod() == Request.ReqMethod.POST) {
        //     Debug.DEBUG("method type POST", DebugType.NONSERVER);
        // }
        // Debug.DEBUG("protocol: " + request.getReqProtocol(), DebugType.NONSERVER);
        // Debug.DEBUG("keep-alive: " + String.valueOf(keepalive), DebugType.NONSERVER);

        // Check url integrity for accesses above doc root
        url = request.getReqUrl();
        if (!checkUrlIntegrity()) {
            keepalive = false;
            generateResponse(400, "Bad Request", null);
        }

        // Perform content selection
        performContentSelection();

        // Map url to file; return 404 Not Found if not found
        File f = mapUrlToFile(url);
        if (f == null) {
            keepalive = false; // close connections with Not Found errors
            generateResponse(404, "Not Found", f);
            return;
        }

        // if-modified-since
        boolean modifiedSince = checkIfModifiedSince(f);
        if (!modifiedSince) {
            keepalive = false;
            generateResponse(304, "Not Modified", null);
            return;
        }

        generateResponse(200, "OK", f);
	} 

    private void generateResponse(int statusCode, String message, File f) {
        if (statusCode == 404) {
            f = mapUrlToFile("err_not_found.html");
        } 

        // Output response status line with error status code and error message
        bufferWriteString(outBuffer, request.getReqProtocol() + " " + Integer.toString(statusCode) + " " + message);

        // Output date header
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(("eee, dd MMM uuuu HH:mm:ss"));
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime gmtNow = now.withZoneSameInstant(ZoneId.of("GMT"));
        bufferWriteString(outBuffer, "Date: " + dtf.format(gmtNow) + " GMT");

        // Ouput server header
        bufferWriteString(outBuffer, "Server: aPAXche/1.0.0 (Ubuntu)");

        if (f != null) {
            // Output last-modified header

            ZonedDateTime fileModifiedTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
            ZonedDateTime fileModifiedGmtTime = fileModifiedTime.withZoneSameInstant(ZoneId.of("GMT"));
            bufferWriteString(outBuffer, "Last-Modified: " + dtf.format(fileModifiedGmtTime) + " GMT");

            // TODO: Output content-type header
            String contentType = url.substring(url.lastIndexOf(".")+1);
            if (contentType.equals("htm")) {
                contentType = "html";
            }
            bufferWriteString(outBuffer, "Content-Type: text/" + contentType);

            // Output content-length header
            bufferWriteString(outBuffer, "Content-Length: " + Integer.toString((int) f.length()));
        }

        // CRLF: End of headers, beginning of reponse body
        outBuffer.put((byte) '\r');
        outBuffer.put((byte) '\n');

        // Output response body
        if (f != null) {
            outputResponseBody(f);
            outBuffer.put((byte) '\r');
            outBuffer.put((byte) '\n');
        }

		outBuffer.flip();
        requestBuffer.delete(0, requestBuffer.length());
        request = null;
		state = State.SENDING_RESPONSE;
    }

    private void updateKeepAlive() {
        String conn = request.lookupHeader("Connection");
        if (request.getReqProtocol().equals("HTTP/1.0")) {
            // HTTP/1.0 default is close, need to specify to keep alive
            keepalive = (conn != null && conn.equals("keep-alive"));
        } else {
            // HTTP/1.1+ default is keep-alive, need to specify close 
            keepalive = (conn == null || !conn.equals("close"));
        }
    }

    // The server should check the integrity of the URL in the request line
    // A common attack of an HTTP sever is to send in a URL such as ../../file to fetch content outside of the document root
    // TODO: Could extend this later; excluding all paths with ".." may exclude some legal paths
    private boolean checkUrlIntegrity() {
        return (url.indexOf("..") == -1);
    }

    private void performContentSelection() {
        // If the request URL is for DocumentRoot (ii.e., empty URL) without specifying a file name and the User-Agent header 
        // indicates that the request is from a mobile handset (e.g., it should at least detect iphone by detecting iPhone in 
        // the User-Agent string), it should return index_m.html, if it exists; index.html next (fall-through), and then Not Found    
        if (url.equals("/")) {
            String userAgent = request.lookupHeader("User-Agent");
            if (userAgent != null && userAgent.indexOf("iPhone") != -1) {
                if (mapUrlToFile("/index_m.html") != null) {
                    url = url + "index_m.html";
                }
            }
        }

        // If the URL ends with / without specifying a file name, the server should return index.html if it exists
        // otherwise it will return Not Found
        if (url.endsWith("/")) {
            if (mapUrlToFile(url + "index.html") != null) {
                url = url + "index.html";
            }
        }
    }

    // url argument has not yet appended doc_root
    // use 
    private File mapUrlToFile(String url) {
        // url: ignore leading /
        if (url.startsWith("/")) {
            url = url.substring(1);
        }

        // get docRoot
        String hostname = request.lookupHeader("Host");
        Debug.DEBUG("hostname: " + hostname, DebugType.NONSERVER);
        String docRoot;
        if (hostname != null) {
            docRoot = Server.getVirtualHostDocRoot(hostname);
            Debug.DEBUG("hostname != null (path 1)", DebugType.NONSERVER);
        } else {
            docRoot = Server.getVirtualHostDocRoot();
            Debug.DEBUG("hostname == null (path 2)", DebugType.NONSERVER);
        }

        // doc root: ignore leading /
        if (docRoot.startsWith("/")) {
            docRoot = docRoot.substring(1);
        }

        // add doc root to url
        String docRootedUrl = docRoot + url;
        Debug.DEBUG("full url requested: " + docRootedUrl, DebugType.NONSERVER);

        String fileName = docRootedUrl;
        File f = new File(fileName);
        if (!f.isFile()) {
            f = null; 
            Debug.DEBUG("couldn't find file " + fileName, DebugType.NONSERVER);           
        }

        return f;
    }

    private boolean checkIfModifiedSince(File f) {
        String ifModSinceStr = request.lookupHeader("If-Modified-Since");
        if (ifModSinceStr != null) {
            // removing "GMT"
            ifModSinceStr = ifModSinceStr.substring(0, ifModSinceStr.length()-4);   

            // get ZonedDateTime representation of if-modified-since time for comparison
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(("eee, dd MMM uuuu HH:mm:ss"));
            LocalDateTime localIfModSinceTime = LocalDateTime.parse(ifModSinceStr, dtf);
            ZonedDateTime ifModSinceTime = localIfModSinceTime.atZone(ZoneId.of("GMT"));
            Debug.DEBUG("modified time: " + ifModSinceTime.toString(), DebugType.NONSERVER);

            // get ZonedDateTime representation of actual file last modified time for comparison
            ZonedDateTime fileModifiedTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
            ZonedDateTime fileModifiedGmtTime = fileModifiedTime.withZoneSameInstant(ZoneId.of("GMT"));

            // TODO: if file was not modified after required time
            return fileModifiedTime.isAfter(ifModSinceTime);
        }
        return true;
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
