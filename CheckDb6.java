import java.sql.*;
public class CheckDb6 {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://35.234.211.181:5432/grc";
        String user = "postgres";
        String pass = "ShyamSundar@108";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("INSERT INTO pan_hsn_config (pan, is_applicable) VALUES ('AAHCT7814P', true) ON CONFLICT (pan) DO UPDATE SET is_applicable = true");
            System.out.println("Updated AAHCT7814P to true");
        }
    }
}
