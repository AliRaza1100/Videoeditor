import com.lumen.studio.AdsConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdsConfigurationChecks {
    static JSONObject fixture() {
        JSONObject c=new JSONObject().put("schema_version",1).put("delivery_mode","admob_mediation")
            .put("version",42).put("ads_enabled",true).put("test_mode",true)
            .put("cooldown_seconds",120).put("max_per_session",3).put("every_n_exports",3)
            .put("network_requests",new JSONArray("[\"admob\",\"meta\",\"ironsource\",\"unity\",\"applovin\"]"));
        JSONArray placements=new JSONArray();
        String[][] definitions={{"home_banner","banner"},{"projects_banner","banner"},{"export_complete","interstitial"},{"export_reward","rewarded"},{"preview_reward","rewarded"},{"app_open","app_open"}};
        for(String[] p:definitions) placements.put(new JSONObject().put("id",p[0]).put("type",p[1])
            .put("network",p[0].equals("app_open")?"admob":"ironsource").put("enabled",true)
            .put("ad_unit_id","ca-app-pub-1234567890123456/1234567890"));
        return c.put("placements",placements);
    }
    static void require(boolean result) { if(!result) throw new AssertionError(); }
    static void rejected(JSONObject input) {
        try { AdsConfiguration.Companion.parse(input); }
        catch(IllegalArgumentException|org.json.JSONException expected) { return; }
        throw new AssertionError("Malformed configuration was accepted");
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0) {
            var actual=AdsConfiguration.Companion.parse(new JSONObject(java.nio.file.Files.readString(java.nio.file.Path.of(args[0]))));
            require(actual.getPlacements().size()==6);
            System.out.println("Running dashboard payload accepted by Android parser: v"+actual.getVersion());
        }
        var valid=AdsConfiguration.Companion.parse(fixture());
        require(valid.getVersion()==42 && valid.getPlacements().size()==6);
        require(valid.getPlacements().get("home_banner").getNetwork().equals("ironsource"));
        rejected(fixture().put("schema_version",2));
        rejected(fixture().put("delivery_mode","remote-code"));
        rejected(fixture().put("version",0));
        rejected(fixture().put("cooldown_seconds",0));
        rejected(fixture().put("max_per_session",20));
        rejected(fixture().put("every_n_exports",1));
        rejected(fixture().put("network_requests",new JSONArray("[\"admob\"]")));
        var bad=fixture();bad.getJSONArray("placements").getJSONObject(0).put("ad_unit_id","meta-placement-id");rejected(bad);
        bad=fixture();bad.getJSONArray("placements").getJSONObject(0).put("type","interstitial");rejected(bad);
        bad=fixture();bad.getJSONArray("placements").getJSONObject(5).put("network","meta");rejected(bad);
        bad=fixture();bad.getJSONArray("placements").getJSONObject(0).put("id","app_open");rejected(bad);
        bad=fixture();bad.getJSONArray("placements").getJSONObject(0).put("enabled",false).put("ad_unit_id","");
        require(!AdsConfiguration.Companion.parse(bad).getPlacements().get("home_banner").getEnabled());
        var backed=fixture();backed.getJSONArray("placements").getJSONObject(0).put("fallback_network","meta").put("fallback_ad_unit_id","ca-app-pub-1234567890123456/1234567891");
        backed.put("export_rules",new JSONObject().put("offer_reward",true).put("resolutions",new JSONArray("[480,1080,2000]")));
        var routed=AdsConfiguration.Companion.parse(backed);require(routed.getOfferExportReward());require(routed.getRewardedResolutions().contains(1080));require(routed.getPlacements().get("home_banner").getFallbackNetwork().equals("meta"));
        var badBackup=new JSONObject(backed.toString());badBackup.getJSONArray("placements").getJSONObject(0).put("fallback_network","ironsource");rejected(badBackup);
        badBackup=new JSONObject(backed.toString());badBackup.getJSONArray("placements").getJSONObject(0).put("fallback_ad_unit_id","bad-id");rejected(badBackup);
        badBackup=new JSONObject(backed.toString());badBackup.getJSONObject("export_rules").put("resolutions",new JSONArray("[999]"));rejected(badBackup);
        System.out.println("Ads configuration parser: five-network model, export rules, backup route and 14 rejection cases passed");
    }
}
