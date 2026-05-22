import java.sql.*;
public class CheckDb3 {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://35.234.211.181:5432/grc";
        String user = "postgres";
        String pass = "ShyamSundar@108";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT pan, is_applicable FROM pan_hsn_config WHERE pan LIKE '%AAAAI0686D%'");
            if(!rs.next()) {
                System.out.println("No partial match found for AAAAI0686D");
            } else {
                do {
                    System.out.println("PAN: '" + rs.getString("pan") + "', is_applicable: " + rs.getBoolean("is_applicable"));
                } while(rs.next());
            }
        }
    }
}
