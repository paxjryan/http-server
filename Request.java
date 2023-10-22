import java.nio.ByteBuffer;
import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

// request interface:
// Request()
// parseRequest(String)
// getReqMethod(), getReqUrl()
// lookupHeader(String)
// toString()

// typical use:
// Request r = Request();
// r.parseRequest(requestStr);
// connectionType = r.lookupHeader("conn");

public class Request {
    public enum ReqMethod {
        GET,
        POST
    }
    // request method line
    private ReqMethod reqMethod;
    private String url;
    private String protocol;

    // other request headers
    private HashMap<String, String> requestHeaders;

    public Request() {
        requestHeaders = new HashMap<String, String>();
    }

    public ReqMethod getReqMethod() {
        return reqMethod;
    }

    public String getReqUrl() {
        return url;
    }

    public String getReqProtocol() {
        return protocol;
    }

    // Returns String value of headerName key in requestHeaders, or null if not found
    public String lookupHeader(String headerName) {
        Debug.DEBUG("attempting lookup of key " + headerName, DebugType.PARSING);

        return requestHeaders.get(headerName);
    }

    public String toString() {
        String str = "";

        str += "Method: ";
        if (reqMethod == ReqMethod.GET) str += "GET\n";
        else if (reqMethod == ReqMethod.POST) str += "POST\n";
        str += url + "\n\n";

        str += "Headers:\n";

        for (Map.Entry<String, String> set : requestHeaders.entrySet()) {
            str += set.getKey() + ": ";
            str += set.getValue() + "\n";
        }

        return str;
    }

    public void parseRequest(String req) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(req));

        parseRequestLine(reader.readLine());

        String header = reader.readLine();
        while (header.length() > 0) {
            parseHeaderLine(header);
            header = reader.readLine();
        }
    }

    private void parseRequestLine(String reqLine) { //throws RequestFormatException {
        String[] request = reqLine.split("\\s");

        if (request.length < 3) {
            // TODO: throw req parsing error
        }

        if (request[0].equals("GET")) {
            reqMethod = ReqMethod.GET;        
            Debug.DEBUG("Parsing req method: GET", DebugType.PARSING);
        } else if (request[0].equals("POST")) {
            reqMethod = ReqMethod.POST;
            Debug.DEBUG("Parsing req method: POST", DebugType.PARSING);
        } else {
            // throw req parsing error
            Debug.DEBUG("Parsing req method error", DebugType.PARSING);
        }
        
        // non-safe url, need to check for validity elsewhere
        url = request[1];
        protocol = request[2];

        Debug.DEBUG("Parsing req url: " + url, DebugType.PARSING);
        Debug.DEBUG("Parsing req protocol: " + protocol, DebugType.PARSING);
    }

    private void parseHeaderLine(String headerLine) { //throws RequestFormatException {
        String[] header = headerLine.split(": ");
        if (header.length < 2) {
            // TODO: throw header parsing error
        }
        
        requestHeaders.put(header[0], header[1]);
        Debug.DEBUG("Parsing header: " + header[0] + ", val: " + header[1], DebugType.PARSING);
    }
}
