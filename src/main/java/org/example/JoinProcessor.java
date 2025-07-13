package org.example;

import java.io.File;
import java.io.RandomAccessFile;
import java.sql.*;
import java.util.*;

public class JoinProcessor {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/metadata";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "userpw";

    public static void performMergeJoin(String tableA, String tableB) {
        try {
            List<AttributeInfo> aAttrs = getAttributes(tableA);
            List<AttributeInfo> bAttrs = getAttributes(tableB);
            AttributeInfo aKeyAttr = aAttrs.get(0);
            AttributeInfo bKeyAttr = bAttrs.get(0);

            List<Record> aRecords = loadRecords(tableA, aAttrs);
            List<Record> bRecords = loadRecords(tableB, bAttrs);

            // 헤더 출력
            System.out.println("\nMerge Join 결과:");
            for (AttributeInfo attr : aAttrs) System.out.print("|" + String.format("%-" + attr.getLength() + "s", attr.getName()));
            for (AttributeInfo attr : bAttrs) System.out.print("|" + String.format("%-" + attr.getLength() + "s", attr.getName()));
            System.out.println("|");

            int i = 0, j = 0;
            while (i < aRecords.size() && j < bRecords.size()) {
                String aKey = aRecords.get(i).key;
                String bKey = bRecords.get(j).key;
                int cmp = aKey.compareTo(bKey);

                if (cmp < 0) i++;
                else if (cmp > 0) j++;
                else {
                    int ai = i;
                    int bj = j;
                    while (ai < aRecords.size() && aRecords.get(ai).key.equals(aKey)) {
                        bj = j;
                        while (bj < bRecords.size() && bRecords.get(bj).key.equals(bKey)) {
                            System.out.println(aRecords.get(ai).formatted + bRecords.get(bj).formatted);
                            bj++;
                        }
                        ai++;
                    }
                    while (i < aRecords.size() && aRecords.get(i).key.equals(aKey)) i++;
                    while (j < bRecords.size() && bRecords.get(j).key.equals(bKey)) j++;
                }
            }

            System.out.println("\nMySQL JOIN 결과:");
            try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
                 Statement stmt = conn.createStatement()) {
                String sql = String.format("SELECT * FROM %s a JOIN %s b ON a.%s = b.%s ORDER BY a.%s",
                        tableA, tableB, aKeyAttr.getName(), bKeyAttr.getName(), aKeyAttr.getName());

                ResultSet rs = stmt.executeQuery(sql);
                ResultSetMetaData meta = rs.getMetaData();

                for (int c = 1; c <= meta.getColumnCount(); c++) {
                    System.out.print("|" + meta.getColumnName(c));
                }
                System.out.println("|");

                while (rs.next()) {
                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                        System.out.print("|" + rs.getString(c));
                    }
                    System.out.println("|");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<AttributeInfo> getAttributes(String table) throws SQLException {
        List<AttributeInfo> attrs = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null, null, table, null);
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int length = rs.getInt("COLUMN_SIZE");
                attrs.add(new AttributeInfo(name, length));
            }
        }
        return attrs;
    }

    private static List<Record> loadRecords(String tableName, List<AttributeInfo> attributesInfo) throws Exception {
        List<Record> records = new ArrayList<>();
        String location = System.getProperty("user.dir") + "/" + tableName + ".txt";
        File file = new File(location);
        long totalLength = file.length();
        int blockCount = 3;
        int blockSize = (int) Math.ceil((double) totalLength / blockCount);

        StringBuilder leftover = new StringBuilder(); // try 밖에서 선언

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long offset = 8;

            while (offset < totalLength) {
                int bytesToRead = (int) Math.min(blockSize, totalLength - offset);
                byte[] blockBuffer = new byte[bytesToRead];
                raf.seek(offset);
                raf.readFully(blockBuffer);

                String block = leftover + new String(blockBuffer);
                block = block.replace("\n", "");
                leftover.setLength(0); // 초기화

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

                    int totalRecordLength = 8 + dataLength + 8;
                    if (pointer - 8 + totalRecordLength > block.length()) {
                        leftover.append(block.substring(pointer - 8));
                        break;
                    }

                    String recordData = block.substring(pointer, pointer + dataLength);
                    pointer += dataLength;
                    pointer += 8; // skip offset

                    String key = recordData.substring(0, attributesInfo.get(0).getLength()).trim();
                    String formatted = formatRecord(recordData, nullBitmap, attributesInfo);
                    records.add(new Record(key, formatted));
                }

                offset += bytesToRead;
            }


        }



        return records;
    }

    private static String formatRecord(String data, String nullBitmap, List<AttributeInfo> attributesInfo) {
        int dataPointer = 0;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < attributesInfo.size(); i++) {
            AttributeInfo attr = attributesInfo.get(i);
            int len = attr.getLength();

            if (i < nullBitmap.length() && nullBitmap.charAt(i) == '1') {
                sb.append("|").append(String.format("%-" + len + "s", ""));
            } else {
                String val = (dataPointer + len <= data.length()) ?
                        data.substring(dataPointer, dataPointer + len) : "";
                sb.append("|").append(String.format("%-" + len + "s", val.trim()));
                dataPointer += len;
            }
        }
        return sb.toString();
    }

    private static class AttributeInfo {
        private final String name;
        private final int length;

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

    private static class Record {
        String key;
        String formatted;

        Record(String key, String formatted) {
            this.key = key;
            this.formatted = formatted;
        }
    }
}
