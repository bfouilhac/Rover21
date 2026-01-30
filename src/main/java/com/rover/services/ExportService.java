package com.rover.services;

import com.rover.model.PlanetSwitchDevice;
import com.rover.model.rstp.RSTPNode;
import com.rover.utils.LanguageManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.table.TableModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Service responsible for exporting PLANET switch device data to various file formats.
 * <p>
 * Supports exporting to CSV and XLSX formats with all device information.
 * </p>
 *
 * @author Benjamin FOUILHAC, ADM21 - January/February 2026
 */
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);
    private final LanguageManager lang = LanguageManager.getInstance();

    /**
     * Exports a list of devices to a CSV file.
     *
     * @param devices the list of devices to export
     * @param file the target CSV file
     */
    public void exportToCSV(List<PlanetSwitchDevice> devices, File file){
        logger.info("Starting CSV export to: {}", file.getAbsolutePath());

        try (FileWriter writer = new FileWriter(file)) {
            // Write CSV header
            writer.append(getCsvHeader());
            writer.append("\n");

            // Write device rows
            for (PlanetSwitchDevice device : devices) {
                writer.append(deviceToCsvRow(device));
                writer.append("\n");
            }

            writer.flush();
            logger.info("CSV export completed successfully: {} devices exported", devices.size());
        } catch (IOException e) {
            logger.error("Error during CSV export", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Exports a list of devices to an XLSX file.
     *
     * @param devices the list of devices to export
     * @param file the target XLSX file
     */
    public void exportToXLSX(List<PlanetSwitchDevice> devices, File file) {
        logger.info("Starting XLSX export to: {}", file.getAbsolutePath());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PLANET Devices");

            // Create a header row
            Row headerRow = sheet.createRow(0);
            String[] headers = getHeaders();
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            int rowNum = 1;
            for (PlanetSwitchDevice device : devices) {
                Row row = sheet.createRow(rowNum++);
                populateDeviceRow(row, device);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to a file
            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }

            logger.info("XLSX export completed successfully: {} devices exported", devices.size());
        } catch (IOException e) {
            logger.error("Error during XLSX export", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Creates a header style for XLSX files.
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * Populates a row with device data.
     */
    private void populateDeviceRow(Row row, PlanetSwitchDevice device) {
        row.createCell(0).setCellValue(safeString(device.getDeviceName()));
        row.createCell(1).setCellValue(safeString(device.getMacAddress()));
        row.createCell(2).setCellValue(safeString(device.getVersion()));
        row.createCell(3).setCellValue(safeString(device.getIpAddress()));
        row.createCell(4).setCellValue(safeString(device.getNetMask()));
        row.createCell(5).setCellValue(safeString(device.getGateway()));
        row.createCell(6).setCellValue(safeString(device.getDescription()));
        row.createCell(7).setCellValue(safeString(device.getSystemLocation()));
        row.createCell(8).setCellValue(safeString(device.getSystemUptime()));
        row.createCell(9).setCellValue(String.valueOf(device.isTelnetUp()));
        row.createCell(10).setCellValue(String.valueOf(device.isSSHUp()));
        row.createCell(11).setCellValue(String.valueOf(device.isHTTPUp()));
        row.createCell(12).setCellValue(String.valueOf(device.isHTTPSUp()));
    }

    /**
     * Returns the column headers.
     */
    private String[] getHeaders() {
        return new String[]{
                lang.get("table.device_name"),
                lang.get("table.mac"),
                lang.get("table.firmware"),
                lang.get("table.ipv4"),
                lang.get("table.netmask"),
                lang.get("table.gateway"),
                lang.get("table.description"),
                lang.get("table.location"),
                lang.get("table.uptime"),
                "Telnet",
                "SSH",
                "HTTP",
                "HTTPS"
        };
    }

    /**
     * Returns the CSV header row.
     */
    private String getCsvHeader() {
        String[] headers = getHeaders();
        return String.join(",", headers);
    }

    /**
     * Converts a device to a CSV row.
     */
    private String deviceToCsvRow(PlanetSwitchDevice device) {
        return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%b,%b,%b,%b",
                escapeCsv(device.getDeviceName()),
                escapeCsv(device.getMacAddress()),
                escapeCsv(device.getVersion()),
                escapeCsv(device.getIpAddress()),
                escapeCsv(device.getNetMask()),
                escapeCsv(device.getGateway()),
                escapeCsv(device.getDescription()),
                escapeCsv(device.getSystemLocation()),
                escapeCsv(device.getSystemUptime()),
                device.isTelnetUp(),
                device.isSSHUp(),
                device.isHTTPUp(),
                device.isHTTPSUp()
        );
    }

    /**
     * Escapes a value for CSV format.
     * Wraps in quotes if it contains commas, quotes, or newlines.
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }

    /**
     * Returns a safe string value (empty string if null).
     */
    private String safeString(String value) {
        return value != null ? value : "";
    }

    /**
     * Exports RSTP topology data to a CSV file from a table model.
     *
     * @param tableModel the table model containing RSTP data
     * @param file the target CSV file
     * @throws IOException if an I/O error occurs
     */
    public void exportRSTPToCSV(TableModel tableModel, File file) throws IOException {
        logger.info("Starting RSTP CSV export to: {}", file.getAbsolutePath());

        try (FileWriter writer = new FileWriter(file)) {
            // Write CSV header
            int columnCount = tableModel.getColumnCount();
            for (int col = 0; col < columnCount; col++) {
                if (col > 0) {
                    writer.append(",");
                }
                writer.append(escapeCsv(tableModel.getColumnName(col)));
            }
            writer.append("\n");

            // Write data rows
            int rowCount = tableModel.getRowCount();
            for (int row = 0; row < rowCount; row++) {
                for (int col = 0; col < columnCount; col++) {
                    if (col > 0) {
                        writer.append(",");
                    }
                    Object value = tableModel.getValueAt(row, col);
                    writer.append(escapeCsv(value != null ? value.toString() : ""));
                }
                writer.append("\n");
            }

            writer.flush();
            logger.info("RSTP CSV export completed successfully: {} rows exported", rowCount);
        } catch (IOException e) {
            logger.error("Error during RSTP CSV export", e);
            throw e;
        }
    }
}
