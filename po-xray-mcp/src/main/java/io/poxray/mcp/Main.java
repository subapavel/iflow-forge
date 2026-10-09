package io.poxray.mcp;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * MCP messages must be UTF-8, but {@code System.out} uses the platform encoding (cp1250 on a Czech Windows), which
 * the STDIO transport picks up at startup; the standard output is therefore replaced before Quarkus starts.
 */
@QuarkusMain
public class Main {

    public static void main(String... args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        Quarkus.run(args);
    }
}
