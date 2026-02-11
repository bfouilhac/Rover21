package rover.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Logback appender that routes log events to registered Swing listeners.
 * <p>
 * This appender buffers log events and notifies UI components on the EDT.
 * It uses a publish-subscribe pattern to decouple logging from UI components.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public class SwingLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_BUFFER_SIZE = 10000;
    private static final List<LogEventListener> listeners = new CopyOnWriteArrayList<>();
    private static final List<LogEntry> buffer = new CopyOnWriteArrayList<>();

    /**
     * Listener interface for UI components that want to receive log events.
     */
    public interface LogEventListener {

        /**
         * Called when a new log event is received.
         *
         * @param entry the log entry to display
         */
        void onLogEvent(LogEntry entry);
    }

    /**
     * Immutable log entry for UI display.
     *
     * @param timestamp the event timestamp in milliseconds
     * @param level the log level (DEBUG, INFO, WARN, ERROR)
     * @param loggerName the logger name
     * @param message the log message
     * @param formattedMessage the fully formatted message including timestamp and level
     */
    public record LogEntry(
            long timestamp,
            String level,
            String loggerName,
            String message,
            String formattedMessage
    ) {}

    /**
     * Registers a listener to receive log events.
     * The listener will immediately receive all buffered events.
     *
     * @param listener the listener to register
     */
    public static void addListener(LogEventListener listener) {
        listeners.add(listener);
        for (LogEntry entry : buffer) {
            listener.onLogEvent(entry);
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public static void removeListener(LogEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns a copy of all buffered log entries.
     *
     * @return immutable list of buffered log entries
     */
    public static List<LogEntry> getBufferedLogs() {
        return List.copyOf(buffer);
    }

    /**
     * Clears the log buffer.
     */
    public static void clearBuffer() {
        buffer.clear();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String formatted = event.getFormattedMessage();

        LogEntry entry = new LogEntry(
                event.getTimeStamp(),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getFormattedMessage(),
                formatted.trim()
        );

        if (buffer.size() >= MAX_BUFFER_SIZE) {
            buffer.removeFirst();
        }
        buffer.add(entry);

        if (!listeners.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                for (LogEventListener listener : listeners) {
                    listener.onLogEvent(entry);
                }
            });
        }
    }
}
