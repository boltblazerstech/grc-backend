import java.sql.*;
public class CheckDbType {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://35.234.211.181:5432/grc";
        String user = "postgres";
        String pass = "ShyamSundar@108";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "pan_hsn_config", "is_applicable");
            if (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                int columnSize = columns.getInt("COLUMN_SIZE");
                System.out.println("Column: " + columnName + ", Type: " + columnType + "(" + columnSize + ")");
            }
        }
    }
}
