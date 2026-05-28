import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonTest2 {
    public static class TdsApplicableRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("isApplicable")
        private Boolean isApplicable;
        private String updatedBy;

        public Boolean getIsApplicable() { return isApplicable; }
        public void setIsApplicable(Boolean isApplicable) { this.isApplicable = isApplicable; }
    }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json1 = "{\"isApplicable\": true}";
        String json2 = "{\"isApplicable\": false}";
        TdsApplicableRequest req1 = mapper.readValue(json1, TdsApplicableRequest.class);
        TdsApplicableRequest req2 = mapper.readValue(json2, TdsApplicableRequest.class);
        System.out.println("req1.isApplicable = " + req1.getIsApplicable());
        System.out.println("req2.isApplicable = " + req2.getIsApplicable());
    }
}
