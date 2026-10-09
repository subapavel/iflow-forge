package io.poxray.cli;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * JSON and documents on stdout are UTF-8. The JVM uses the platform encoding for {@code System.out} (cp1250 on a
 * Czech Windows), which breaks redirected output ({@code po-xray json x.sca > x.json}); it is replaced at startup,
 * before a command runs.
 */
@ApplicationScoped
public class Utf8Stdout {

    void onStart(@Observes StartupEvent event) {
        if (!StandardCharsets.UTF_8.equals(System.out.charset())) {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        }
    }
}
