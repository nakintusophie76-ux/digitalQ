package com.kiu.digitalq;

import com.kiu.digitalq.server.QueueServer;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Headless server entry point, for a dedicated machine in the Finance Department.
 *
 * <pre>
 *   java -cp build\classes com.kiu.digitalq.ServerMain [port] [data-folder]
 * </pre>
 */
public final class ServerMain {

    public static void main(String[] args) {
        int port = 8765;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                System.err.println("Port must be a number, for example 8765.");
                System.exit(2);
            }
        }
        Path dataDirectory = args.length > 1 ? Path.of(args[1]) : Path.of("data");

        QueueServer server = new QueueServer(port, dataDirectory);
        try {
            server.start();
        } catch (Exception ex) {
            System.err.println("Could not start the KIU digitalQ server on port " + port + ": " + ex.getMessage());
            System.err.println("The port may already be in use. Try: server.bat 8766");
            System.exit(1);
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[KIU digitalQ] Shutting down...");
            server.stop();
            latch.countDown();
        }, "kiuq-shutdown"));

        System.out.println("[KIU digitalQ] Press Ctrl+C to stop.");
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
