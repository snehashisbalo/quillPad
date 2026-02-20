package org.quillpad.collab;

public class ServerRunner {
    public static void main(String[] args) {
        int port = 9999;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port, using default 9999");
            }
        }
        
        CollabServer server = new CollabServer(port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            server.stop();
        }));
        
        System.out.println("Starting collaboration server on port " + port + "...");
        server.start();
    }
}
