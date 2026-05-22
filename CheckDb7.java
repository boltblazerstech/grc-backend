import java.sql.*;
public class CheckDb7 {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://35.234.211.181:5432/grc";
        String user = "postgres";
        String pass = "ShyamSundar@108";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM pan_hsn_config");
            if(rs.next()) {
                System.out.println("Total rows in pan_hsn_config: " + rs.getInt(1));
            }
        }
    }
}
