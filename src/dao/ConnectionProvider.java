package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class ConnectionProvider {
    private static final String DB_NAME = "attendanceJframebd";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/" + DB_NAME + "?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "pass123";

    private static Connection con = null;

    public static Connection getcon() {
        try {
            if (con != null && !con.isClosed()) {
                return con; // reuse existing connection
            }

            Class.forName("com.mysql.cj.jdbc.Driver");

            // Temporary connection to check/create DB
            try (Connection tempCon = DriverManager.getConnection("jdbc:mysql://localhost:3306/?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", DB_USERNAME, DB_PASSWORD);
                 Statement stmt = tempCon.createStatement()) {

                ResultSet rs = stmt.executeQuery("SHOW DATABASES LIKE '" + DB_NAME + "'");
                if (!rs.next()) {
                    stmt.executeUpdate("CREATE DATABASE " + DB_NAME);
                    System.out.println("✅ Database '" + DB_NAME + "' created successfully.");
                }
            }

            // Now connect to actual DB
            con = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
            System.out.println("✅ Connected to database: " + DB_NAME);
            return con;

        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("❌ Database connection failed: " + ex.getMessage());
            return null;
        }
    }
}
