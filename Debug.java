public class Debug {
    private static boolean DEBUG = true;
    private static boolean DEBUG_SERVER = true;
    private static boolean DEBUG_NONSERVER = true;

    public static void DEBUG(String s, DebugType t) {
        if (DEBUG) {
            if (t == DebugType.SERVER && DEBUG_SERVER) {
                System.out.println(s);
            }
            if (t == DebugType.NONSERVER && DEBUG_NONSERVER) {
                System.out.println(s);
            }
        }
    }
}
