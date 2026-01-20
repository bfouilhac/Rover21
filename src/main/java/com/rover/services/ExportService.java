package com.rover.services;

import com.rover.model.PlanetSwitchDevice;
import com.rover.utils.LanguageManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * @param file    the target CSV file
     * @throws IOException if an I/O error occurs during export
     */
    public void exportToCSV(List<PlanetSwitchDevice> devices, File file) throws IOException {
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
            throw e;
        }
    }

    /**
     * Exports a list of devices to an XLSX file.
     *
     * @param devices the list of devices to export
     * @param file    the target XLSX file
     * @throws IOException if an I/O error occurs during export
     */
    public void exportToXLSX(List<PlanetSwitchDevice> devices, File file) throws IOException {
        logger.info("Starting XLSX export to: {}", file.getAbsolutePath());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("PLANET Devices");

            // Create header row
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

            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }

            logger.info("XLSX export completed successfully: {} devices exported", devices.size());
        } catch (IOException e) {
            logger.error("Error during XLSX export", e);
            throw e;
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
        int cellNum = 0;

        row.createCell(cellNum++).setCellValue(safeString(device.getDeviceName()));
        row.createCell(cellNum++).setCellValue(safeString(device.getMacAddress()));
        row.createCell(cellNum++).setCellValue(safeString(device.getVersion()));
        row.createCell(cellNum++).setCellValue(safeString(device.getIpAddress()));
        row.createCell(cellNum++).setCellValue(safeString(device.getNetMask()));
        row.createCell(cellNum++).setCellValue(safeString(device.getGateway()));
        row.createCell(cellNum++).setCellValue(safeString(device.getDescription()));
        row.createCell(cellNum++).setCellValue(safeString(device.getSystemLocation()));
        row.createCell(cellNum++).setCellValue(safeString(device.getSystemUptime()));

        // Note: Protocol availability (Telnet, SSH, HTTP, HTTPS) would be added here if available in the model
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
                lang.get("table.uptime")
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
        return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s",
                escapeCsv(device.getDeviceName()),
                escapeCsv(device.getMacAddress()),
                escapeCsv(device.getVersion()),
                escapeCsv(device.getIpAddress()),
                escapeCsv(device.getNetMask()),
                escapeCsv(device.getGateway()),
                escapeCsv(device.getDescription()),
                escapeCsv(device.getSystemLocation()),
                escapeCsv(device.getSystemUptime())
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
}
