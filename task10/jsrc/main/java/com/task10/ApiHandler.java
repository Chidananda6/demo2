package com.task10;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.AuthFlowType;
import com.amazonaws.services.cognitoidp.model.InitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.InitiateAuthResult;
import com.amazonaws.services.cognitoidp.model.SignUpRequest;
import com.amazonaws.services.cognitoidp.model.SignUpResult;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;

@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "simple-booking-userpool")
@LambdaHandler(
		lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = false,
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "REGION", value = "eu-central-1"),
		@EnvironmentVariable(key = "COGNITO_ID", value = "simple-booking-userpool"),
		@EnvironmentVariable(key = "CLIENT_ID", value = "simple-booking-userpool")
})
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private final AWSCognitoIdentityProvider cognitoClient;
	private final AmazonDynamoDB dynamoDBClient;
	private final DynamoDB dynamoDB;
	private final String clientId;

	public ApiHandler() {
		this.cognitoClient = AWSCognitoIdentityProviderClientBuilder.standard()
				.withRegion(System.getenv("REGION"))
				.build();

		this.dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
				.withRegion(System.getenv("REGION"))
				.build();

		this.dynamoDB = new DynamoDB(dynamoDBClient);
		this.clientId = System.getenv("CLIENT_ID");
	}

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		String resource = (String) request.get("resource");
		String httpMethod = (String) request.get("httpMethod");
		Map<String, Object> response = new HashMap<>();

		try {
			if ("/signup".equals(resource) && "POST".equals(httpMethod)) {
				response = handleSignup(request);
			} else if ("/signin".equals(resource) && "POST".equals(httpMethod)) {
				response = handleSignin(request);
			} else if ("/tables".equals(resource) && "GET".equals(httpMethod)) {
				response = handleGetTables(request);
			} else if ("/tables".equals(resource) && "POST".equals(httpMethod)) {
				response = handleCreateTable(request);
			} else if (resource.startsWith("/tables/") && "GET".equals(httpMethod)) {
				response = handleGetTableById(request);
			} else if ("/reservations".equals(resource) && "POST".equals(httpMethod)) {
				response = handleCreateReservation(request);
			} else if ("/reservations".equals(resource) && "GET".equals(httpMethod)) {
				response = handleGetReservations(request);
			} else {
				response.put("statusCode", 400);
				response.put("body", "Invalid request");
			}
		} catch (Exception e) {
			response.put("statusCode", 500);
			response.put("body", "Internal server error: " + e.getMessage());
		}

		return response;
	}

	private Map<String, Object> handleSignup(Map<String, Object> request) {
		JSONObject requestBody = new JSONObject((String) request.get("body"));
		String firstName = requestBody.getString("firstName");
		String lastName = requestBody.getString("lastName");
		String email = requestBody.getString("email");
		String password = requestBody.getString("password");

		SignUpRequest signUpRequest = new SignUpRequest()
				.withClientId(clientId)
				.withUsername(email)
				.withPassword(password)
				.withUserAttributes(
						new AttributeType().withName("given_name").withValue(firstName),
						new AttributeType().withName("family_name").withValue(lastName),
						new AttributeType().withName("email").withValue(email)
				);

		SignUpResult signUpResult = cognitoClient.signUp(signUpRequest);

		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("body", "Sign-up process is successful");
		return response;
	}

	private Map<String, Object> handleSignin(Map<String, Object> request) {
		JSONObject requestBody = new JSONObject((String) request.get("body"));
		String email = requestBody.getString("email");
		String password = requestBody.getString("password");

		Map<String, String> authParams = new HashMap<>();
		authParams.put("USERNAME", email);
		authParams.put("PASSWORD", password);

		InitiateAuthRequest authRequest = new InitiateAuthRequest()
				.withClientId(clientId)
				.withAuthFlow(AuthFlowType.USER_PASSWORD_AUTH)
				.withAuthParameters(authParams);

		InitiateAuthResult authResult = cognitoClient.initiateAuth(authRequest);
		String idToken = authResult.getAuthenticationResult().getIdToken();

		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("body", new JSONObject().put("accessToken", idToken).toString());
		return response;
	}

	private Map<String, Object> handleGetTables(Map<String, Object> request) {
		Table table = dynamoDB.getTable("Tables");
		ScanRequest scanRequest = new ScanRequest().withTableName("Tables");
		ScanResult result = dynamoDBClient.scan(scanRequest);

		JSONArray tables = new JSONArray();
		result.getItems().forEach(item -> tables.put(new JSONObject(item)));

		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("body", new JSONObject().put("tables", tables).toString());
		return response;
	}

	private Map<String, Object> handleCreateTable(Map<String, Object> request) {
		JSONObject requestBody = new JSONObject((String) request.get("body"));
		String id = requestBody.getString("id");
		String number = requestBody.getString("number");
		String places = requestBody.getString("places");
		String isVip = requestBody.getString("isVip");
		String minOrder = requestBody.optString("minOrder");

		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", new AttributeValue(id));
		item.put("number", new AttributeValue(number));
		item.put("places", new AttributeValue(places));
		item.put("isVip", new AttributeValue(isVip));
		if (minOrder != null && !minOrder.isEmpty()) {
			item.put("minOrder", new AttributeValue(minOrder));
		}

		PutItemRequest putItemRequest = new PutItemRequest()
				.withTableName("Tables")
				.withItem(item);

		dynamoDBClient.putItem(putItemRequest);

		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("body", new JSONObject().put("id", id).toString());
		return response;
	}

	private Map<String, Object> handleGetTableById(Map<String, Object> request) {
		String tableId = request.get("pathParameters").toString();

		GetItemSpec spec = new GetItemSpec().withPrimaryKey("id", tableId);

		Table table = dynamoDB.getTable("Tables");
		JSONObject item = new JSONObject(table.getItem(spec).toJSON());

		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("body", item.toString());
		return response;
	}

	private Map<String, Object> handleCreateReservation(Map<String, Object> request) {
		JSONObject requestBody = new JSONObject((String) request.get("body"));
		String tableNumber = requestBody.getString("tableNumber");
		String clientName = requestBody.getString("clientName");
		String phoneNumber = requestBody.getString("phoneNumber");
		String date = requestBody.getString("date");
		String slotTimeStart = requestBody.getString("slotTimeStart");
		String slotTimeEnd = requestBody.getString("slotTimeEnd");

		String reservationId = java.util.UUID.randomUUID().toString();
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("reservationId", new AttributeValue(reservationId));
		item.put("tableNumber", new AttributeValue(tableNumber));
		item.put("clientName", new AttributeValue(clientName));
		item.put("phoneNumber", new AttributeValue(phoneNumber));
		item.put("date", new AttributeValue(date));
		item.put("slotTimeStart", new AttributeValue(slotTimeStart));
		item.put("slotTimeEnd", new AttributeValue(slotTimeEnd));

		PutItemRequest putItemRequest = new PutItemRequest()
				.withTableName("Reservations")
				.withItem(item);

		dynamoDBClient.putItem(putItemRequest);

		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("body", new JSONObject().put("reservationId", reservationId).toString());
		return response;
	}

	private Map<String, Object> handleGetReservations(Map<String, Object> request) {
		Table table = dynamoDB.getTable("Reservations");
		ScanRequest scanRequest = new ScanRequest().withTableName("Reservations");
		ScanResult result = dynamoDBClient.scan(scanRequest);

		JSONArray reservations = new JSONArray();
		result.getItems().forEach(item -> reservations.put(new JSONObject(item)));

		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("body", new JSONObject().put("reservations", reservations).toString());
		return response;
	}
}
