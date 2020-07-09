import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.hamcrest.collection.IsIn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.text.StringSubstitutor;
import com.moandjiezana.toml.Toml;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Testrerouting_puml {

	Map<String, Object> paramsMap = new HashMap();
	ScriptEngine engine;
	StringSubstitutor substitutor;

	// FIXME: no IS_WINDOWS please
	private static final boolean IS_WINDOWS = System.getProperty( "os.name" ).contains( "indow" );

	@BeforeEach
	public void setupTest() throws Exception {
		try {
			URL url = Thread.currentThread().getContextClassLoader().getResource("rerouting_config.toml");
			String configFile = url.getFile();
			String osAppropriatePath = IS_WINDOWS ? configFile.substring(1) : configFile;
			Path path = Paths.get(osAppropriatePath);

			paramsMap = unnestTomlMap("", new Toml().read(new String(Files.readAllBytes(path))).toMap());
			substitutor = new StringSubstitutor(paramsMap);
			engine = new ScriptEngineManager().getEngineByName("JavaScript");
		} catch(Exception exception) {
			System.out.println("An Error occured, possible during reading the TOML config file: " + exception);
			throw exception;
		}
	}

    @Test
	public void test() throws Exception {
		paramsMap.put("countryCode", "${countryCode1}");
		paramsMap.put("positionCountryCode", "${positionCountryCode1}");
		paramsMap.put("sourceEventType", "${sourceEventType1}");
		
		Response roundtrip1 = RestAssured.given()
		        .auth().basic(subst("${CRS.username}"), subst("${CRS.password}"))
		        .param("countryCode", subst("${countryCode}"))
		        .param("positionCountryCode", subst("${positionCountryCode}"))
		        .param("sourceEventType", subst("${sourceEventType}"))
		    .when()
		        .post(subst("${CRS.path}") + subst("/ccc/rerouteOptions"))
		    .then()
		        .assertThat()
		            .statusCode(IsIn.isIn(Arrays.asList(200)))        
		        	.and().extract().response();
		paramsMap.put("uiswitch", roundtrip1.jsonPath().getString("/UISWITCH"));
		paramsMap.put("reroute", roundtrip1.jsonPath().getString("/REROUTE"));
		paramsMap.put("warmhandover", roundtrip1.jsonPath().getString("/WARMHANDOVER"));


		
		if(eval("${voiceEstablished} == true")) {
			Response roundtrip2 = RestAssured.given()
			        .auth().basic(subst("${VM.username}"), subst("${VM.password}"))
			    .when()
			        .get(subst("${VM.path}") + subst("/ccc/events/${eventId}/isconnected"))
			    .then()
			        .assertThat()
			            .statusCode(IsIn.isIn(Arrays.asList(200)))        
			        	.and().extract().response();
			paramsMap.put("eventid1", roundtrip2.jsonPath().getString("/VoiceStatus/eventId1"));
			paramsMap.put("agent1", roundtrip2.jsonPath().getString("/VoiceStatus/agent1/connectionStatus"));
			paramsMap.put("agent2", roundtrip2.jsonPath().getString("/VoiceStatus/agent2/connectionStatus"));
		}


		
		if(eval("${voiceEstablished} == false")) {
			Response roundtrip3 = RestAssured.given()
			        .auth().basic(subst("${VM.username}"), subst("${VM.password}"))
			    .when()
			        .get(subst("${VM.path}") + subst("/ccc/events/${eventId}/isconnected"))
			    .then()
			        .assertThat()
			            .statusCode(IsIn.isIn(Arrays.asList(400, 404, 500)))        
			        	.and().extract().response();
		}
	}

    /// Helper method to make to templating in string variables above more clean.
	private String subst(String source) {
	    assert substitutor != null;
	    return substitutor.replace(source);
	}

	/// Helper method to make evaluation of conditions more clean.
	private boolean eval(String condition) throws ScriptException {
	    assert engine != null;
	    // First, run the templating engine over the condition.
	    // This is the whole reason why we do this "evaluate a JS string at runtime" at all.
	    String substCondition = subst(condition);
	    // Second, we can simply pipe the string through the JavaScript engine and get a result.
	    return (Boolean) engine.eval(substCondition);
	}

    /// Helper method to flatten the tree-like structure of a TOML file.
    /// Here, we use the path of an item as the key and the item itself as the value.
    /// The path of an item separated by dots, e.g. "A.B.item".
	private static Map<String, Object> unnestTomlMap(String prefix, Map<String, Object> tree) {
        Map<String, Object> resultMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : tree.entrySet()) {
            String identifierPath = prefix + entry.getKey();
            if(entry.getValue() instanceof Map){
                resultMap.putAll(unnestTomlMap(identifierPath + ".", (Map<String, Object>)entry.getValue()));
            } else {
                resultMap.put(identifierPath, entry.getValue());
            }
        }
        return resultMap;
	}
}
