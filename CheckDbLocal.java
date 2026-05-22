import java.sql.*;
public class CheckDbLocal {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/grc";
        String user = "postgres";
        String pass = "postgres";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT count(*) FROM pan_hsn_config WHERE is_applicable = true");
            if(rs.next()) {
                System.out.println("Local DB rows with is_applicable=true: " + rs.getInt(1));
            }
        } catch(Exception e) {
            System.out.println("Could not connect to local DB: " + e.getMessage());
        }
    }
}
