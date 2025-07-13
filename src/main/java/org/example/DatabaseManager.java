package org.example;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class DatabaseManager {
    private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/metadata";
    private static final String USER = "root";
    private static final String PWD = "userpw";

    private static class AttributeInfo {
        private String name;
        private int length;

        public AttributeInfo(String name, int length) {
            this.name = name;
            this.length = length;
        }

        public String getName() {
            return name;
        }

        public int getLength() {
            return length;
        }
    }

    // Get table attributes using JDBC metadata features
    private ArrayList<AttributeInfo> getAttributes(String tableName) {
        ArrayList<AttributeInfo> attributes = new ArrayList<>();

        try {
            Class.forName(JDBC_DRIVER);
            Connection conn = DriverManager.getConnection(DB_URL, USER, PWD);

            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, tableName, null);

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                int columnSize = columns.getInt("COLUMN_SIZE");
                attributes.add(new AttributeInfo(columnName, columnSize));
            }

            columns.close();
            conn.close();
        } catch (SQLException se) {
            se.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return attributes;
    }

    // Get table location and length using JDBC metadata
    private String[] getTableInfo(String tableName) {
        String[] info = new String[2]; // [location, length]

        try {
            Class.forName(JDBC_DRIVER);
            Connection conn = DriverManager.getConnection(DB_URL, USER, PWD);

            // Get table location (file path)
            info[0] = System.getProperty("user.dir") + "\\" + tableName + ".txt";

            // Calculate total length from column metadata
            int totalLength = 0;
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, tableName, null);

            while (columns.next()) {
                totalLength += columns.getInt("COLUMN_SIZE");
            }

            info[1] = String.valueOf(totalLength);

            columns.close();
            conn.close();
        } catch (SQLException se) {
            se.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return info;
    }

    public void insertRecord(BufferedReader reader, String tableName) throws IOException {
        ArrayList<AttributeInfo> attributesInfo = getAttributes(tableName);

        System.out.print("Enter the number of records to insert: ");
        int recordCount;
        try {
            recordCount = Integer.parseInt(reader.readLine());
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a valid number.");
            return;
        }

        System.out.println("Enter the values of the records in order, separated by semicolons.");
        for (AttributeInfo info : attributesInfo) {
            System.out.print("|" + String.format("%-" + info.getLength() + "s", info.getName()));
        }
        System.out.println("|");

        try (RandomAccessFile file = new RandomAccessFile(tableName + ".txt", "rw")) {
            long insertOffset;

            if (file.length() == 0) {
                file.writeBytes("00000008"); // Header pointer: 8 bytes
                insertOffset = 8;
            } else {
                insertOffset = readLastOffset(file, attributesInfo);
            }

            StringBuilder writeBuffer = new StringBuilder();
            int recordInBlock = 0;
            final int BLOCK_RECORD_COUNT = 3; // Block = 3 records

            // ... 기존 코드 유지 ...

            for (int recordNum = 1; recordNum <= recordCount; recordNum++) {
                System.out.println("\nRecord " + recordNum + ":");
                System.out.print(">> ");
                String values = reader.readLine();
                String[] valueArray = values.split(";");

                StringBuilder nullBitmap = new StringBuilder("xxxxxxxx");
                StringBuilder valueBuffer = new StringBuilder();
                List<String> cleanedValues = new ArrayList<>();

                for (int i = 0; i < attributesInfo.size(); i++) {
                    String value = (i < valueArray.length) ? valueArray[i].strip() : "";
                    if (value.isEmpty()) {
                        nullBitmap.setCharAt(i, '1');
                        cleanedValues.add(null);
                    } else {
                        nullBitmap.setCharAt(i, '0');
                        if (value.length() > attributesInfo.get(i).getLength()) {
                            System.out.println("Exceeded length. Skipping.");
                            continue;
                        }
                        valueBuffer.append(String.format("%-" + attributesInfo.get(i).getLength() + "s", value));
                        cleanedValues.add(value);
                    }
                }

                // ✅ 1. MySQL INSERT 동기화
                try (Connection conn = DriverManager.getConnection(DB_URL, USER, PWD)) {
                    StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " VALUES (");
                    for (int i = 0; i < attributesInfo.size(); i++) {
                        sql.append("?");
                        if (i != attributesInfo.size() - 1)
                            sql.append(", ");
                    }
                    sql.append(")");

                    try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
                        for (int i = 0; i < cleanedValues.size(); i++) {
                            if (cleanedValues.get(i) == null) {
                                pstmt.setNull(i + 1, Types.VARCHAR);
                            } else {
                                pstmt.setString(i + 1, cleanedValues.get(i));
                            }
                        }
                        pstmt.executeUpdate();
                        System.out.println("✅ MySQL Insert succeeded for Record " + recordNum);
                    }

                } catch (SQLException e) {
                    System.err.println("❌ MySQL Insert failed for Record " + recordNum + ": " + e.getMessage());
                    return;
                }

                // ✅ 2. 기존 파일 레코드 저장 처리 계속
                String recordBody = nullBitmap + valueBuffer.toString();
                int recordLength = recordBody.length() + 8 + 1;
                String offsetStr = String.format("%08d", insertOffset + writeBuffer.length() + recordLength);
                String fullRecord = recordBody + offsetStr + "\n";

                writeBuffer.append(fullRecord);
                recordInBlock++;

                if (recordInBlock == BLOCK_RECORD_COUNT) {
                    file.seek(insertOffset);
                    file.writeBytes(writeBuffer.toString());
                    insertOffset += writeBuffer.length();
                    writeBuffer.setLength(0);
                    recordInBlock = 0;
                }
            }

        }
    }

    private long readLastOffset(RandomAccessFile file, ArrayList<AttributeInfo> attributesInfo) throws IOException {
        long fileLength = file.length();
        long currentOffset = 8;

        if (fileLength <= 8) {
            return 8;
        }

        while (currentOffset < fileLength) {
            file.seek(currentOffset);

            if (currentOffset + 8 > fileLength) break;
            byte[] nullBitmap = new byte[8];
            file.readFully(nullBitmap);
            String bitmapStr = new String(nullBitmap);

            int dataLength = 0;
            for (int i = 0; i < attributesInfo.size(); i++) {
                if (bitmapStr.charAt(i) == '0') {
                    dataLength += attributesInfo.get(i).getLength();
                }
            }

            long offsetPos = currentOffset + 8 + dataLength;
            if (offsetPos + 8 > fileLength) break;

            file.seek(offsetPos);
            byte[] offsetBytes = new byte[8];
            file.readFully(offsetBytes);

            int nextOffset;
            try {
                nextOffset = Integer.parseInt(new String(offsetBytes).trim());
            } catch (NumberFormatException e) {
                break;
            }

            currentOffset = nextOffset + 1;
        }

        return currentOffset;
    }

    public void selectRecord(BufferedReader reader, String tableName) throws IOException {
        String[] tableInfo = getTableInfo(tableName);
        String location = tableInfo[0];
        ArrayList<AttributeInfo> attributesInfo = getAttributes(tableName);
        AttributeInfo firstAttr = attributesInfo.get(0);

        System.out.println("Enter range for " + firstAttr.getName() + ":");
        System.out.print("Minimum value: ");
        double min = Double.parseDouble(reader.readLine().trim());
        System.out.print("Maximum value: ");
        double max = Double.parseDouble(reader.readLine().trim());

        // 출력 헤더
        for (AttributeInfo attr : attributesInfo) {
            System.out.print("|" + String.format("%-" + attr.getLength() + "s", attr.getName()));
        }
        System.out.println("|");

        File file = new File(location);
        long totalLength = file.length();
        int blockCount = 3;
        int blockSize = (int) Math.ceil((double) totalLength / blockCount);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long offset = 8; // 첫 8바이트는 헤더 포인터 → 레코드 시작 offset
            StringBuilder leftover = new StringBuilder();

            while (offset < totalLength) {
                int bytesToRead = (int) Math.min(blockSize, totalLength - offset);
                byte[] blockBuffer = new byte[bytesToRead];
                raf.seek(offset);
                raf.readFully(blockBuffer);

                String block = leftover + new String(blockBuffer);
                block = block.replace("\n", "");
                int pointer = 0;

                while (pointer + 8 <= block.length()) {
                    String nullBitmap = block.substring(pointer, pointer + 8);
                    pointer += 8;

                    int dataLength = 0;
                    for (int i = 0; i < attributesInfo.size(); i++) {
                        if (i < nullBitmap.length() && nullBitmap.charAt(i) != '1') {
                            dataLength += attributesInfo.get(i).getLength();
                        }
                    }

                    int totalRecordLength = 8 + dataLength + 8; // nullBitmap + data + offset
                    if (pointer - 8 + totalRecordLength > block.length()) {
                        // 블록 끝에 걸친 레코드 → 다음 블록에서 이어 붙이기
                        leftover = new StringBuilder(block.substring(pointer - 8));
                        break;
                    }

                    String recordData = block.substring(pointer, pointer + dataLength);
                    pointer += dataLength;

                    String offsetStr = block.substring(pointer, pointer + 8);
                    pointer += 8;

                    boolean inRange = false;
                    if (nullBitmap.charAt(0) != '1') {
                        try {
                            String valStr = recordData.substring(0, firstAttr.getLength()).trim();
                            double val = Double.parseDouble(valStr);
                            inRange = (val >= min && val <= max);
                        } catch (NumberFormatException e) {
                            inRange = false;
                        }
                    }

                    if (inRange) {
                        printRecord(recordData, nullBitmap, attributesInfo);
                    }

                    leftover = new StringBuilder(); // 레코드 끝났으니 leftover 초기화
                }

                offset += bytesToRead; // 다음 블록으로 이동
            }
        }
    }


    private void printRecord(String data, String nullBitmap, ArrayList<AttributeInfo> attributesInfo) {
        int dataPointer = 0;

        for (int i = 0; i < attributesInfo.size(); i++) {
            AttributeInfo attr = attributesInfo.get(i);
            int len = attr.getLength();

            if (i < nullBitmap.length() && nullBitmap.charAt(i) == '1') {
                // NULL 값이면 공백
                System.out.print("|" + String.format("%-" + len + "s", ""));
            } else {
                // 값 있음
                String val = (dataPointer + len <= data.length()) ?
                        data.substring(dataPointer, dataPointer + len) : "";
                System.out.print("|" + String.format("%-" + len + "s", val.trim()));
                dataPointer += len;
            }
        }
        System.out.println("|");
    }


}
