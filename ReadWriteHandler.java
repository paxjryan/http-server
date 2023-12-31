// Code modified from https://zoo.cs.yale.edu/classes/cs434/cs434-2023-fall/assignments/programming-proj1/examples/SelectServer/EchoLineReadWriteHandler.java

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator.RequestorType;
import java.net.Socket;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// for response header Date
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class ReadWriteHandler implements IReadWriteHandler {
    private ByteBuffer inBuffer;
    private StringBuffer requestBuffer;
    private StringBuffer cgiContentBuffer;
    private int cgiContentLength;

    private Request request;
    private boolean keepalive;
    private String url;         // possibly modified during content selection; does not include doc_root

    private StringBuffer cgiOutputBuffer;
    private ByteBuffer outBuffer;

    private enum State {
        READING_REQUEST, 
        READING_CONTENT,
        PROCESSING_REQUEST,
        SENDING_RESPONSE, 
        CONN_CLOSED
    }
    private State state;

    public ReadWriteHandler() {
        inBuffer = ByteBuffer.allocate(4096);
        requestBuffer = new StringBuffer(14096);

        keepalive = false;

        // TODO: cgi output limited to 4096 chars per chunk
        cgiOutputBuffer = new StringBuffer(4096);
        outBuffer = ByteBuffer.allocate(4096);

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
            case READING_CONTENT:
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

		if (state != State.READING_REQUEST && state != State.READING_CONTENT) { // this call should not happen, ignore
			return;
		}

		// process incoming request
		processRequestInBuffer(key);

        if (state == State.PROCESSING_REQUEST) {
            generateResponse();
        }

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

	private void processRequestInBuffer(SelectionKey key) throws IOException {
		Debug.DEBUG("processInBuffer", DebugType.NONSERVER);

		SocketChannel client = (SocketChannel) key.channel();
		int readBytes = client.read(inBuffer);
		Debug.DEBUG("handleRead: Read data from connection " + client + " for " + readBytes + " byte(s); to buffer "
				+ inBuffer, DebugType.NONSERVER);

		if (readBytes == -1) { // end of stream
			state = State.SENDING_RESPONSE;
			Debug.DEBUG("handleRead: readBytes == -1", DebugType.NONSERVER);
		} else {
            // TODO: throw 413 Entity Too Large if buffer overflow
			inBuffer.flip(); // read input

            while (state != State.PROCESSING_REQUEST && inBuffer.hasRemaining() && requestBuffer.length() < requestBuffer.capacity()) {
                char ch = (char) inBuffer.get();

                // read into request buffer
                if (state == State.READING_REQUEST) {
                    // Debug.DEBUG("Ch: " + String.valueOf(ch) + " (" + Integer.toString(ch) + ")", DebugType.NONSERVER);
                    requestBuffer.append(ch);

                    // "\n\r\n" (Windows) or "\r\r\n" (Mac) signal end of headers
                    if (requestBuffer.length() >= 3 && 
                            (requestBuffer.substring(requestBuffer.length() - 3).equals("\n\r\n") || 
                            requestBuffer.substring(requestBuffer.length() - 3).equals("\r\r\n"))) {
                        Debug.DEBUG("handleRead: found terminating chars", DebugType.NONSERVER);
                        
                        request = new Request();
                        boolean successfulParse = request.parseRequest(requestBuffer.toString());

                        if (!successfulParse) {
                            inBuffer.clear();
                            generateResponseWithCode(400, "Bad Request", null);
                            return;
                        }

                        // process cgi content if necessary
                        if (request.getReqMethod() == ReqMethod.POST) {
                            // TODO: throw error if we are missing this header

                            cgiContentLength = Integer.parseInt(request.lookupHeader("Content-Length"));
                            cgiContentBuffer = new StringBuffer(cgiContentLength+3);

                            state = State.READING_CONTENT;
                        } else {
                            state = State.PROCESSING_REQUEST;
                        }
                    }
                }

                // read into content buffer
                else if (state == State.READING_CONTENT) {
                    Debug.DEBUG("Writing ch to cgi buf: " + String.valueOf(ch) + " (" + Integer.toString(ch) + ")", DebugType.NONSERVER);
                    cgiContentBuffer.append(ch);
                    cgiContentLength--;

                    if (cgiContentLength == 0) {
                        state = State.PROCESSING_REQUEST;
                        performCgi(key);
                        break;
                    }
                }
            }
		}

		inBuffer.clear();
	}

    private void performCgi(SelectionKey key) {
        // Check url integrity for accesses above doc root
        url = request.getReqUrl();
        if (!checkUrlIntegrity()) {
            keepalive = false;
            generateResponseWithCode(400, "Bad Request", null);
        }

        String[] splitUrl = url.split("/");
        String executableName = splitUrl[splitUrl.length-1];

        ProcessBuilder processBuilder = new ProcessBuilder(String.valueOf(url.substring(1)));  // new ProcessBuilder("cgi/price.cgi");
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);

        Map<String, String> env = processBuilder.environment();
        
        String cgiQuery = cgiContentBuffer.toString();
        Debug.DEBUG("cgi buffer: " + cgiQuery, DebugType.NONSERVER);
        env.put("QUERY_STRING", cgiQuery);

        Socket sock = ((SocketChannel) key.channel()).socket();
        env.put("REMOTE_ADDR", sock.getInetAddress().getHostAddress());
        // Debug.DEBUG(sock.getInetAddress().getHostAddress(), DebugType.NONSERVER);
        env.put("REMOTE_HOST", "");
        env.put("REMOTE_IDENT", ""); 
        // TODO: "The REMOTE_USER variable provides a user identification string supplied by client as part of user authentication."
        env.put("REMOTE_USER", "");

        env.put("REQUEST_METHOD", "POST");

        env.put("SERVER_NAME", request.lookupHeader("Host"));
        // Debug.DEBUG(request.lookupHeader("Host"), DebugType.NONSERVER);
        env.put("SERVER_PORT", Integer.toString(Server.getPort())); 
        // Debug.DEBUG(Integer.toString(Server.getPort()), DebugType.NONSERVER);
        env.put("SERVER_PROTOCOL", request.getReqProtocol());
        // Debug.DEBUG(request.getReqProtocol(), DebugType.NONSERVER);
        env.put("SERVER_SOFTWARE", "aPAXche/1.0.0 (Ubuntu)");

        try {
            Process process = processBuilder.start();
            InputStream cgiProcessOutputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(cgiProcessOutputStream));

            String s;
            while ((s = reader.readLine()) != null && cgiOutputBuffer.length() + s.length() < cgiOutputBuffer.capacity()) {
                cgiOutputBuffer.append(s);
            }
            Debug.DEBUG("cgi success", DebugType.NONSERVER);
        } catch (IOException e) {
            Debug.DEBUG("Cgi error", DebugType.NONSERVER);
            generateResponseWithCode(500, "Internal Server Error: cgi failed", null);
        }
    }

	private void generateResponse() throws IOException {
        // determining Connection from headers and update keepalive variable
        updateKeepAlive();

        // Check url integrity for accesses above doc root
        url = request.getReqUrl();
        if (!checkUrlIntegrity()) {
            keepalive = false;
            generateResponseWithCode(400, "Bad Request", null);
            return;
        }

        if (request.getReqMethod() == ReqMethod.UNKNOWN) {
            keepalive = false;
            generateResponseWithCode(501, "Not Implemented", null);
            return;
        }

        if (request.getReqMethod() == ReqMethod.POST) {
            generateResponseWithCode(200, "OK", null);
            return;
        }

        Debug.DEBUG(request.getReqProtocol().substring(0,7), DebugType.NONSERVER);
        if (!request.getReqProtocol().substring(0,7).equals("HTTP/1.")) {
            generateResponseWithCode(505, "HTTP Version Not Supported", null);
            return;
        }

        if (url.equals("/load")) {
            generateResponseWithCode(200, "OK", null);
            return;
        }

        // Perform content selection
        performContentSelection();

        // Map url to file; return 404 Not Found if not found
        File f = mapUrlToFile();
        if (f == null) {
            keepalive = false; // close connections with Not Found errors
            generateResponseWithCode(404, "Not Found", null);
            return;
        }

        // if-modified-since; return 304 Not Modified if not modified since
        boolean modifiedSince = checkIfModifiedSince(f);
        if (!modifiedSince) {
            keepalive = false;
            generateResponseWithCode(304, "Not Modified", null);
            return;
        }

        // accept; return 406 Not Acceptable if content type not found in accept header
        boolean accepted = checkIfAccepted(f);
        if (!accepted) {
                Debug.DEBUG("406 error", DebugType.NONSERVER);
                generateResponseWithCode(406, "Not Acceptable", null);
                return;
        }

        generateResponseWithCode(200, "OK", f);
	} 

    private void generateResponseWithCode(int statusCode, String message, File f) {
        Debug.DEBUG("Output buf length: " + Integer.toString(cgiOutputBuffer.length()), DebugType.NONSERVER);

        if (statusCode == 404) {
            url = "err_not_found.html";
            f = mapUrlToFile();
        } 

        // Determine outBuffer size and allocate
        // Use 4K as max headers size: https://stackoverflow.com/questions/686217/maximum-on-http-header-values
        int headersSize = 4096;
        int numBytes = 0;
        if (f != null) {
            numBytes = (int) f.length();
        }
        outBuffer = ByteBuffer.allocate(headersSize + numBytes);

        // Output response status line with error status code and error message
        String protocol = request.getReqProtocol();
        if (protocol != null) {
            bufferWriteString(outBuffer, protocol + " " + Integer.toString(statusCode) + " " + message);
        } else {
            bufferWriteString(outBuffer, "HTTP/1.1 " + Integer.toString(statusCode) + " " + message);
        }

        // Output date header
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(("eee, dd MMM uuuu HH:mm:ss"));
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime gmtNow = now.withZoneSameInstant(ZoneId.of("GMT"));
        bufferWriteString(outBuffer, "Date: " + dtf.format(gmtNow) + " GMT");

        // Output server header
        bufferWriteString(outBuffer, "Server: aPAXche/1.0.0 (Ubuntu)");

        if (request.getReqMethod() == ReqMethod.POST) {
            bufferWriteString(outBuffer, "Content-Length: " + Integer.toString(cgiOutputBuffer.length()));

            // CRLF: End of headers, beginning of reponse body
            outBuffer.put((byte) '\r');
            outBuffer.put((byte) '\n');

            Debug.DEBUG("Output buf length: " + Integer.toString(cgiOutputBuffer.length()), DebugType.NONSERVER);
            bufferWriteString(outBuffer, cgiOutputBuffer.toString());
        } else {
            if (f != null) {
                // Output last-modified header
                ZonedDateTime fileModifiedTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
                ZonedDateTime fileModifiedGmtTime = fileModifiedTime.withZoneSameInstant(ZoneId.of("GMT"));
                bufferWriteString(outBuffer, "Last-Modified: " + dtf.format(fileModifiedGmtTime) + " GMT");

                // Output content-type header
                String contentType = "";

                try {
                    contentType = Files.probeContentType(f.toPath());
                } catch (IOException ex) {}
                
                bufferWriteString(outBuffer, "Content-Type: " + contentType);

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
        }

        // Reset all buffers at once
		outBuffer.flip();
        requestBuffer.delete(0, requestBuffer.length());
        if (request.getReqMethod() == ReqMethod.POST) {
            cgiContentBuffer.delete(0, cgiContentBuffer.length());
            cgiOutputBuffer.delete(0, cgiOutputBuffer.length());
        }
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
    private File mapUrlToFile() {
        return mapUrlToFile(url);
    }

    // url argument has not yet appended doc_root
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
            // Debug.DEBUG("hostname != null (path 1)", DebugType.NONSERVER);
        } else {
            docRoot = Server.getVirtualHostDocRoot();
            // Debug.DEBUG("hostname == null (path 2)", DebugType.NONSERVER);
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

            return fileModifiedGmtTime.isAfter(ifModSinceTime);
        }
        return true;
    }

    private boolean checkIfAccepted(File f) {
        String acceptTypeStr = request.lookupHeader("Accept");
        if (acceptTypeStr != null) {
            String[] acceptTypes = acceptTypeStr.split(",");
            String contentType = "";
            try {
                contentType = Files.probeContentType(f.toPath());
            } catch (IOException ex) {}

            for (int i = 0; i < acceptTypes.length; i++) {
                if (acceptTypes[i].trim().equals(contentType) || acceptTypes[i].indexOf("*/*") != -1) {
                    return true;
                }
            }
            return false;
        }      
        return true; 
    }

    private void outputResponseBody(File f) {
        try {
            FileInputStream fileInputStream = new FileInputStream(f);
            byte[] allBytes = fileInputStream.readAllBytes();

            outBuffer.put(allBytes);
            fileInputStream.close();
        } catch (IOException ex) {
            Debug.PRINT("ReadWriteHandler.java/outputResponseBody: unknown error reading from file input stream, system exiting");
            System.exit(1);
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
