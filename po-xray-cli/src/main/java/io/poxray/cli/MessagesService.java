package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.monitoring.KeyResolver;
import io.poxray.monitoring.KeyResolver.Resolution;
import io.poxray.monitoring.Message;
import io.poxray.monitoring.MessageArchive;
import io.poxray.monitoring.MessageInterface;
import io.poxray.monitoring.MonitoringClient;
import io.poxray.monitoring.MonitoringSystems;
import io.poxray.monitoring.SaveTarget;
import io.poxray.monitoring.SearchResult;
import io.poxray.po.PoException;
import io.poxray.po.PoSystem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Callable;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;

/**
 * Shared logic of the {@code po-xray messages} commands: the system (spec §5), the message key of an ID, the table
 * output, saving on request, and the error mapping (exit 1 with one line on stderr, exit 2 for an ambiguous ID).
 */
@ApplicationScoped
public class MessagesService {

    /** Local time with zone in tables. */
    static final DateTimeFormatter TABLE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    @Inject
    MonitoringEnvironment environment;

    /** Ends a command with this exit code after its output was printed. */
    static final class Exit extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final int code;

        Exit(int code) {
            super(null, null, false, false);
            this.code = code;
        }
    }

    /** Runs a command body: a PO, monitoring or argument error is one line on stderr and exit code 1. */
    CommandResult run(Callable<CommandResult> body) {
        try {
            return body.call();
        } catch (Exit e) {
            return CommandResult.valueOf(e.code);
        } catch (PoException | IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return CommandResult.FAILURE;
    }

    /** The client of the system: {@code --system}, else POXRAY_SYSTEM, else ~/.po-xray/monitoring.properties. */
    MonitoringClient client(String system) {
        Map<String, String> env = environment.env();
        String name = MonitoringSystems.resolve(system, env, Path.of(System.getProperty("user.home"), ".po-xray"));
        // credentials from the same environment as the system name (never System.getenv() directly)
        return new MonitoringClient(PoSystem.load(name, PoSystem.defaultDirectory(), env));
    }

    /**
     * The message key to read: {@code key} when given; else {@code idOrKey} resolved by {@link KeyResolver}. An
     * ambiguous ID prints its candidates (stdout) and a hint (stderr) and ends with exit code 2; an ID without a usable
     * key prints the reason and ends with exit code 1. A key is never picked silently.
     */
    String key(CommandInvocation invocation, MonitoringClient client, String idOrKey, String key, String direction,
            boolean archive) {
        if (key != null && !key.isBlank()) {
            return key.strip();
        }
        if (idOrKey == null || idOrKey.isBlank()) {
            throw new IllegalArgumentException("Give a message ID or key, or --key <message key>");
        }
        Resolution r = KeyResolver.resolve(client, idOrKey.strip(), direction, archive);
        switch (r) {
            case Resolution.Key k -> {
                return k.messageKey();
            }
            case Resolution.NotFound n -> {
                System.err.println("Error: message " + n.idOrKey() + ": " + n.reason());
                throw new Exit(1);
            }
            case Resolution.Ambiguous a -> {
                for (Message m : a.candidates()) {
                    invocation.println(String.join("  ", m.messageKey() == null ? "(no key)" : m.messageKey(),
                            or(m.direction()), endpoint(m.receiverName(), m.receiverInterface()), or(m.status())));
                }
                System.err.println("Message " + idOrKey.strip() + " has " + a.candidates().size()
                        + " candidates (listed above): repeat with --key <message key> or --direction OUTBOUND|INBOUND");
                throw new Exit(2);
            }
        }
    }

    /** A search answer: {@code --json} the model, else one table row per message and the count. */
    void print(CommandInvocation invocation, SearchResult result, boolean json) {
        if (json) {
            invocation.println(Json.write(result).stripTrailing());
        } else {
            result.messages().forEach(m -> invocation.println(row(m)));
            invocation.println(result.messages().size() + " message(s)");
        }
        if (result.moreAvailable()) {
            System.err.println("more messages exist — narrow the filter or raise --limit");
        }
        otherFields(result.otherFields().size());
    }

    /** Prints a note when the answer held values the reader could not map (they are in the JSON). */
    static void otherFields(int count) {
        if (count > 0) {
            System.err.println("Note: " + count + " value(s) of the answer could not be mapped; they are kept in"
                    + " otherFields (--json)");
        }
    }

    /** Refuses a {@code --save} directory before anything is read or written; nothing without {@code --save}. */
    static void checkSave(String saveDir) {
        if (saveDir != null) {
            SaveTarget.check(Path.of(saveDir));
        }
    }

    /** Saves one message's files under {@code --save} when given. */
    static void save(String saveDir, String messageId, Object model, String rawResponse, byte[] payload) {
        if (saveDir != null) {
            System.err.println("Saved " + MessageArchive.save(Path.of(saveDir), messageId, model, rawResponse, payload));
        }
    }

    /** {@code 2026-11-10 08:15:30 +01:00  systemError  SenderA/SI_A_Out → ReceiverB/SI_B_In  <id>  <key>}. */
    static String row(Message m) {
        return String.join("  ", time(m.startTime()), or(m.status()),
                endpoint(m.senderName(), m.senderInterface()) + " → " + endpoint(m.receiverName(), m.receiverInterface()),
                or(m.messageId()), or(m.messageKey()));
    }

    /** Local time with zone, {@code -} for none. */
    static String time(OffsetDateTime t) {
        return t == null ? "-" : TABLE_TIME.format(t.atZoneSameInstant(ZoneId.systemDefault()));
    }

    static String endpoint(String component, MessageInterface iface) {
        return or(component) + "/" + (iface == null ? "-" : or(iface.name()));
    }

    static String or(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
