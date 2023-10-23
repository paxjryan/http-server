import java.nio.ByteBuffer;
import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.File;
import java.io.FileReader;

public class ServerConfig {
    private int port;
    private int nSelectLoops;

    private HashMap<String, String> virtualHosts;
    private String firstVirtualHost;

    public ServerConfig() {
        virtualHosts = new HashMap<String, String>();
    }
    
    public int getPort() {
        return port;
    }

    public int getNSelectLoops() {
        return nSelectLoops;
    }

    // Returns String value of serverName key in virtualHosts, or null if not found
    public String lookupVirtualHost(String serverName) {
        Debug.DEBUG("attempting lookup of key " + serverName, DebugType.PARSING);

        return virtualHosts.get(serverName);
    }

    // public String toString() {
    //     String str = "";

    //     str += "Method: ";
    //     if (reqMethod == ReqMethod.GET) str += "GET\n";
    //     else if (reqMethod == ReqMethod.POST) str += "POST\n";
    //     str += url + "\n\n";

    //     str += "Headers:\n";

    //     for (Map.Entry<String, String> set : requestHeaders.entrySet()) {
    //         str += set.getKey() + ": ";
    //         str += set.getValue() + "\n";
    //     }

    //     return str;
    // }

    public void parseConfigFile(File f) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(f));

        parsePort(reader.readLine());
        parseNSelectLoops(reader.readLine());

        // virtual hosts specified in groups of 5 lines:
        // CRLF, <ignore>, DocumentRoot, ServerName, <ignore>
        String nextLine = reader.readLine();                    // line 1
        while (nextLine != null) {
            reader.readLine();                                  // line 2
            String docRootString = reader.readLine();           // line 3
            String serverNameString = reader.readLine();        // line 4
            parseVirtualHost(docRootString, serverNameString);
            reader.readLine();                                  // line 5
            nextLine = reader.readLine();                       // line 1
        }
    }

    private void parsePort(String portLine) {
        String[] arr = portLine.split("\\s");
        if (arr.length < 2) {
            // TODO: throw parsing error
        }

        port = Integer.parseInt(arr[1]);
        Debug.DEBUG("Parsing port: " + Integer.toString(port), DebugType.PARSING);
    }

    private void parseNSelectLoops(String loopsLine) {
        String[] arr = loopsLine.split("\\s");
        if (arr.length < 2) {
            // TODO: throw parsing error
        }

        nSelectLoops = Integer.valueOf(arr[1]);
        Debug.DEBUG("Parsing nSelectLoops: " + Integer.toString(nSelectLoops), DebugType.PARSING);
    }

    private void parseVirtualHost(String docRootLine, String serverNameLine) { 
        String[] docRoot = docRootLine.split("DocumentRoot");
        String[] serverName = serverNameLine.split("ServerName");
        if (docRoot.length < 2 || serverName.length < 2) {
            // TODO: throw virtual host parsing error
        }
        
        String sn = serverName[1].trim();
        String dr = docRoot[1].trim();
        virtualHosts.put(sn, dr);
        Debug.DEBUG("Parsing virtual host: " + sn + ", docRoot: " + dr, DebugType.PARSING);

        if (firstVirtualHost == null) {
            firstVirtualHost = sn;
        }
    }
}
