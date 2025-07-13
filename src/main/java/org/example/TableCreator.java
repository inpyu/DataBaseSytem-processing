package org.example;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TableCreator {

    static String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
    static String DB_URL = "jdbc:mysql://localhost:3306/metadata";
    static String USER = "root";
    static String PWD = "userpw";

    public class ColumnDefinition {
        String columnName;
        String dataType;
        Integer length;

        public ColumnDefinition(String columnName, String dataType, Integer length) {
            this.columnName = columnName;
            this.dataType = dataType;
            this.length = length;
        }

        public String getColumnName() {
            return columnName;
        }

        public String getDataType() {
            return dataType;
        }

        public Integer getLength() {
            return length;
        }
    }

    public void createTable(BufferedReader br) throws IOException {

        // 테이블 이름 입력
        System.out.println("Input the table name:");
        System.out.print(">> ");
        String tableName = br.readLine().toLowerCase();

        // 컬럼 정보 입력
        List<ColumnDefinition> columns = new ArrayList<>();
        System.out.println("Input column info(column name, data type).");
        System.out.println("Example: id, int(11)");
        System.out.println("Press enter when you're done.");

        String line;
        while (!(line = br.readLine()).equals("")) {
            String[] parts = line.split(", ");
            String columnName = parts[0];
            String dataTypeWithLength = parts[1];

            String dataType = dataTypeWithLength.split("\\(")[0];
            int length = Integer.parseInt(dataTypeWithLength.split("\\(")[1].split("\\)")[0]);

            columns.add(new ColumnDefinition(columnName, dataType, length));
        }

        // 프라이머리 키 입력
        System.out.println("Input the primary key column name:");
        System.out.print(">> ");
        String primaryKey = br.readLine();

        // 실제 테이블 생성
        createTableInDatabase(tableName, columns, primaryKey);

        // Create file with header pointer
        FileOutputStream fos = new FileOutputStream(System.getProperty("user.dir") + "\\" + tableName + ".txt");
        // Free list pointer - indicates where the next record should be inserted
        String header = String.format("%08d", 8);
        fos.write(header.getBytes());
        fos.write("\n".getBytes());
        fos.close();

        System.out.println("Table " + tableName + " created successfully.");
    }

    private void createTableInDatabase(String tableName, List<ColumnDefinition> columns, String primaryKey) {
        try {
            Class.forName(JDBC_DRIVER);
            Connection conn = DriverManager.getConnection(DB_URL, USER, PWD);
            Statement stmt = conn.createStatement();

            // 테이블 생성 SQL 생성
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (");

            for (int i = 0; i < columns.size(); i++) {
                ColumnDefinition column = columns.get(i);
                sqlBuilder.append(column.getColumnName()).append(" ")
                        .append(column.getDataType()).append("(").append(column.getLength()).append(")");

                if (i < columns.size() - 1) {
                    sqlBuilder.append(", ");
                }
            }

            sqlBuilder.append(")");

            String sql = sqlBuilder.toString();
            System.out.println("Executing SQL: " + sql);
            stmt.executeUpdate(sql);

            // 테이블 구조 출력
            showTableStructure(stmt, tableName);

            stmt.close();
            conn.close();
        } catch (SQLException se) {
            se.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showTableStructure(Statement stmt, String tableName) {
        try {
            ResultSet rs = stmt.executeQuery("DESCRIBE " + tableName);
            System.out.println("\nTable structure for '" + tableName + "':");
            System.out.println("--------------------------------------------------");
            System.out.printf("%-20s %-20s %-10s %-10s\n", "Field", "Type", "Null", "Key");
            System.out.println("--------------------------------------------------");

            while (rs.next()) {
                System.out.printf("%-20s %-20s %-10s %-10s\n",
                        rs.getString("Field"),
                        rs.getString("Type"),
                        rs.getString("Null"),
                        rs.getString("Key"));
            }
            System.out.println("--------------------------------------------------");
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
