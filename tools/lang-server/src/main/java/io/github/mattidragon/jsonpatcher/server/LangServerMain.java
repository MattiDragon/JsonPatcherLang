package io.github.mattidragon.jsonpatcher.server;

import org.eclipse.lsp4j.launch.LSPLauncher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class LangServerMain {
    public static void main(String[] args) {
//        hackIO();

        if (Boolean.parseBoolean(System.getProperty("jsonpatcher.ls.disable_log", "false"))) {
            LogManager.getLogManager().reset();
            var globalLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
            globalLogger.setLevel(Level.OFF);
        }
        
        var server = new JsonPatcherLanguageServer();
        var launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        
        try {
            launcher.startListening().get();
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error in language server");
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void hackIO() {
        var oldOut = System.out;
        OutputStream file;
        try {
            //noinspection resource
            file = Files.newOutputStream(Path.of("log.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                oldOut.write(b);
                file.write(b);
            }

            @Override
            public void flush() throws IOException {
                oldOut.flush();
                file.flush();
            }
        }));
        var oldErr = System.err;
        OutputStream file2;
        try {
            //noinspection resource
            file2 = Files.newOutputStream(Path.of("err.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.setErr(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                oldErr.write(b);
                file2.write(b);
            }
        }));

        OutputStream file3;
        try {
            //noinspection resource
            file3 = Files.newOutputStream(Path.of("in.txt"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var oldIn = System.in;
        System.setIn(new InputStream() {
            @Override
            public int read() throws IOException {
                var val = oldIn.read();
                file3.write(val);
                return val;
            }
        });
    }
}
