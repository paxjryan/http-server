public class Debug {
    private static boolean DEBUG = true;
    private static boolean DEBUG_SERVER = true;
    private static boolean DEBUG_SERVER_VERBOSE = true;
    private static boolean DEBUG_NONSERVER = true;
    private static boolean DEBUG_PARSING = true;

    public static void DEBUG(String s, DebugType t) {
        if (DEBUG) {
            if (t == DebugType.SERVER && DEBUG_SERVER) {
                System.out.println(s);
            }
            if (t == DebugType.SERVER_VERBOSE && DEBUG_SERVER_VERBOSE) {
                System.out.println(s);
            }
            if (t == DebugType.NONSERVER && DEBUG_NONSERVER) {
                System.out.println(s);
            }
            if (t == DebugType.PARSING && DEBUG_PARSING) {
                System.out.println(s);
            }
        }
    }

    public static void PRINT(String s) {
        System.out.println(s);
    }
}
