import java.io.BufferedReader;
import java.nio.channels.*;
import java.io.InputStreamReader;

public class Manager implements Runnable {
        private Dispatcher[] dispatchers;

        public Manager(Dispatcher[] dispatchers) {
            this.dispatchers = dispatchers;
        }

        public void run() {
            System.out.print("> ");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                while (true) {
                    String input = reader.readLine().trim();

                    if (input.trim().equals("shutdown")) {
                        Debug.DEBUG("Manager: shutting system down", DebugType.SERVER);

                        for (Dispatcher d : dispatchers) {
                            Debug.DEBUG("Manager: interrupting dispatcher " + Integer.toString(d.getDispatcherId()), DebugType.SERVER);
                            d.interrupt();
                        }

                        Debug.PRINT("All threads shut down, system exiting");
                        System.exit(0);
                    } else {
                        Debug.DEBUG("unknown command", DebugType.SERVER);
                        System.out.print("> ");
                    }
                }
            }
            catch (Exception e) {
                Debug.DEBUG("unknown error with input stream", DebugType.SERVER);
            }
        }
}
