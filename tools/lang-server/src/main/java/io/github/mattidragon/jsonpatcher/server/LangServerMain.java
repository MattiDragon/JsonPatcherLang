package io.github.mattidragon.jsonpatcher.server;

import org.eclipse.lsp4j.launch.LSPLauncher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

public class LangServerMain {
    public static void main(String[] args) {
        int port = 18092;
        for (var arg : args) {
            if (arg.startsWith("--socket=")) {
                port = Integer.parseInt(arg.substring("--socket=".length()));
            } else {
                System.err.println("Unexpected argument: " + arg);
            }
        }
        
        try (var socket = new Socket("localhost", port)) {
            runServer(socket.getInputStream(), socket.getOutputStream());
        } catch (IOException e) {
            System.err.println("Failed to start socket");
            e.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private static void runServer(InputStream in, OutputStream out){
        var server = new JsonPatcherLanguageServer();
        var launcher = LSPLauncher.createServerLauncher(server, in, out);
        server.connect(launcher.getRemoteProxy());

        try {
            launcher.startListening().get();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error in language server");
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
