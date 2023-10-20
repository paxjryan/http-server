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

    public String lookupHeader(String headerName) {
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
        int idx = reqLine.indexOf(" ");
        if (idx == -1) {
            // throw req parsing error
        }

        if (reqLine.substring(0, idx).equals("GET")) {
            reqMethod = ReqMethod.GET;        
            Debug.DEBUG("Parsing req method: GET", DebugType.PARSING);
        } else if (reqLine.substring(0, idx).equals("POST")) {
            reqMethod = ReqMethod.POST;
            Debug.DEBUG("Parsing req method: POST", DebugType.PARSING);
        } else {
            // throw req parsing error
            Debug.DEBUG("Parsing req method error", DebugType.PARSING);
        }

        // non-safe url; need to check if it is valid elsewhere
        url = reqLine.substring(idx+1, reqLine.length());
        Debug.DEBUG("Parsing req url: " + url, DebugType.PARSING);
    }

    private void parseHeaderLine(String headerLine) { //throws RequestFormatException {
        int idx = headerLine.indexOf(":");
        if (idx == -1) {
            // throw header parsing error
        }

        String header = headerLine.substring(0, idx);
        String headerVal = headerLine.substring(idx+2);
        requestHeaders.put(header, headerVal);
        Debug.DEBUG("Parsing header: " + header + ", val: " + headerVal, DebugType.PARSING);
    }

    // // return the str in strs which matches the start of sb and deletes it from the beginning of sb, or
    // // returns empty string if no match found
    // private String findMatchingStartStr(StringBuffer sb, String[] strs) {
    //     for (int i = 0; i < strs.length; i++) {
    //         String str = strs[i];
    //         if (sb.substring(0, str.length).equals(str)) {
    //             sb.delete(0, str.length());
    //             return str;
    //         }
    //     }
    //     return "";
    // } 

    // private String[] parseLines(StringBuffer sb) {
    //     String[] lines = new String[];

    //     for (int i = 0; i < sb.length(); i++) {

    //     }
    // }

    // given string and list of suspected starting strings:
    // find which string it matches and apply behavior, or
    // if no matches, throw parsing error

}
