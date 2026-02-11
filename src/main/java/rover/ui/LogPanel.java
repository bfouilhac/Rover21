package rover.ui;

import rover.logging.SwingLogAppender;
import rover.logging.SwingLogAppender.LogEntry;
import rover.utils.LanguageManager;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

/**
 * Panel displaying application logs with filtering and search capabilities.
 * <p>
 * Features include level filtering, auto-scroll toggle, clear logs,
 * copy to clipboard, and color-coded log levels.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21
 */
public class LogPanel extends JPanel implements SwingLogAppender.LogEventListener {

    private static final Font LOG_FONT = new Font("Consolas", Font.PLAIN, 12);
    private static final int MAX_ROWS = 5000;

    private final transient LanguageManager lang = LanguageManager.getInstance();
    private final DefaultTableModel tableModel;
    private final JTable logTable;
    private final Set<LogLevel> enabledLevels = EnumSet.allOf(LogLevel.class);

    private boolean autoScroll = true;

    private enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Creates a new log panel.
     */
    public LogPanel() {
        setLayout(new BorderLayout(0, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        add(createToolbar(), BorderLayout.NORTH);

        String[] columns = {
                lang.get("logs.column_time"),
                lang.get("logs.column_level"),
                lang.get("logs.column_logger"),
                lang.get("logs.column_message")
        };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        logTable = new JTable(tableModel);
        logTable.setFont(LOG_FONT);
        logTable.setRowHeight(20);
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        logTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        logTable.getColumnModel().getColumn(0).setMaxWidth(80);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(75);
        logTable.getColumnModel().getColumn(1).setMaxWidth(60);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(300);
        logTable.getColumnModel().getColumn(2).setMaxWidth(400);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(500);

        logTable.getColumnModel().getColumn(1).setCellRenderer(new LevelCellRenderer());

        JScrollPane scrollPane = new JScrollPane(logTable);
        add(scrollPane, BorderLayout.CENTER);

        SwingLogAppender.addListener(this);
        loadBufferedLogs();
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        JCheckBox debugCheck = new JCheckBox("DEBUG", true);
        JCheckBox infoCheck = new JCheckBox("INFO", true);
        JCheckBox warnCheck = new JCheckBox("WARN", true);
        JCheckBox errorCheck = new JCheckBox("ERROR", true);

        debugCheck.addActionListener(_ -> toggleLevel(LogLevel.DEBUG, debugCheck.isSelected()));
        infoCheck.addActionListener(_ -> toggleLevel(LogLevel.INFO, infoCheck.isSelected()));
        warnCheck.addActionListener(_ -> toggleLevel(LogLevel.WARN, warnCheck.isSelected()));
        errorCheck.addActionListener(_ -> toggleLevel(LogLevel.ERROR, errorCheck.isSelected()));

        toolbar.add(new JLabel(lang.get("logs.filter") + ":"));
        toolbar.add(debugCheck);
        toolbar.add(infoCheck);
        toolbar.add(warnCheck);
        toolbar.add(errorCheck);

        toolbar.add(Box.createHorizontalStrut(20));

        JCheckBox autoScrollCheckbox = new JCheckBox(lang.get("logs.auto_scroll"), true);
        autoScrollCheckbox.addActionListener(_ -> autoScroll = autoScrollCheckbox.isSelected());
        toolbar.add(autoScrollCheckbox);

        toolbar.add(Box.createHorizontalStrut(20));

        JButton clearButton = new JButton(lang.get("logs.clear"));
        clearButton.addActionListener(_ -> clearLogs());
        toolbar.add(clearButton);

        JButton copyButton = new JButton(lang.get("logs.copy"));
        copyButton.addActionListener(_ -> copyLogsToClipboard());
        toolbar.add(copyButton);

        return toolbar;
    }

    @Override
    public void onLogEvent(LogEntry entry) {
        LogLevel level = parseLevel(entry.level());
        if (level == null || !enabledLevels.contains(level)) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date(entry.timestamp()));
            String shortLogger = shortenLoggerName(entry.loggerName());

            tableModel.addRow(new Object[]{time, entry.level(), shortLogger, entry.message()});

            if (tableModel.getRowCount() > MAX_ROWS) {
                tableModel.removeRow(0);
            }

            if (autoScroll && logTable.getRowCount() > 0) {
                int lastRow = logTable.getRowCount() - 1;
                logTable.scrollRectToVisible(logTable.getCellRect(lastRow, 0, true));
            }
        });
    }

    private LogLevel parseLevel(String level) {
        try {
            return LogLevel.valueOf(level);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String shortenLoggerName(String fullName) {
        if (fullName == null) return "";
        int lastDot = fullName.lastIndexOf('.');
        return lastDot > 0 ? fullName.substring(lastDot + 1) : fullName;
    }

    private void toggleLevel(LogLevel level, boolean enabled) {
        if (enabled) {
            enabledLevels.add(level);
        } else {
            enabledLevels.remove(level);
        }
    }

    private void clearLogs() {
        tableModel.setRowCount(0);
        SwingLogAppender.clearBuffer();
    }

    private void copyLogsToClipboard() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            sb.append(tableModel.getValueAt(i, 0)).append(" ");
            sb.append(String.format("%-5s", tableModel.getValueAt(i, 1))).append(" ");
            sb.append(tableModel.getValueAt(i, 2)).append(" - ");
            sb.append(tableModel.getValueAt(i, 3)).append("\n");
        }

        StringSelection selection = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    private void loadBufferedLogs() {
        for (LogEntry entry : SwingLogAppender.getBufferedLogs()) {
            onLogEvent(entry);
        }
    }

    /**
     * Disposes of this panel and unregisters the log listener.
     * Call this method when the panel is no longer needed.
     */
    public void dispose() {
        SwingLogAppender.removeListener(this);
    }

    /**
     * Custom renderer for color-coded log levels.
     */
    private static class LevelCellRenderer extends DefaultTableCellRenderer {

        private static final Color COLOR_ERROR = new Color(220, 50, 50);
        private static final Color COLOR_WARN = new Color(200, 150, 0);
        private static final Color COLOR_INFO = new Color(30, 130, 70);
        private static final Color COLOR_DEBUG = Color.GRAY;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setHorizontalAlignment(SwingConstants.CENTER);

            if (!isSelected) {
                String level = value != null ? value.toString() : "";
                switch (level) {
                    case "ERROR" -> setForeground(COLOR_ERROR);
                    case "WARN" -> setForeground(COLOR_WARN);
                    case "INFO" -> setForeground(COLOR_INFO);
                    case "DEBUG" -> setForeground(COLOR_DEBUG);
                    default -> setForeground(Color.BLACK);
                }
            }
            return this;
        }
    }
}
