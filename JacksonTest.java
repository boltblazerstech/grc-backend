import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

public class JacksonTest {
    @Data
    public static class TestObj {
        @JsonProperty("isApplicable")
        private Boolean isApplicable;
    }
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TestObj obj = new TestObj();
        obj.setIsApplicable(true);
        System.out.println("JSON: " + mapper.writeValueAsString(obj));
    }
}
