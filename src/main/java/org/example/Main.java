package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.sql.rowset.Joinable;

public class Main {
    static final TableCreator tableCreator = new TableCreator();
    static final JoinProcessor joinProcessor = new JoinProcessor();
    static final DatabaseManager dbManager = new DatabaseManager();

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.println("Select a command you want to execute.");
            System.out.println("(1.CREATE TABLE  2.INSERT  3.SELECT  4.EXIT  5.JOIN)");

            System.out.print(">> ");
            String command = br.readLine();

            switch (Integer.parseInt(command)) {
                case 1:
                    tableCreator.createTable(br);
                    break;
                case 2:
                    System.out.println("Enter a table for the record to be inserted.");
                    System.out.print(">> ");
                    String table = br.readLine();
                    dbManager.insertRecord(br, table.toLowerCase());
                    break;
                case 3:
                    System.out.println("Enter a table for the record to be selected.");
                    System.out.print(">> ");
                    String table3 = br.readLine();
                    dbManager.selectRecord(br, table3.toLowerCase());
                    break;
                case 4:
                    br.close();
                    return;
                case 5:
                    System.out.println("Enter first table name (R):");
                    System.out.print(">> ");
                    String rTable = br.readLine();

                    System.out.println("Enter second table name (S):");
                    System.out.print(">> ");
                    String sTable = br.readLine();

                    JoinProcessor.performMergeJoin(rTable.toLowerCase(), sTable.toLowerCase());
                    break;

                default:
                    System.out.println("Invalid Option");
                    break;
            }
        }
    }
}
