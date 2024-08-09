package com.task10;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.*;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.lang.UnsupportedOperationException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID;
import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID;

@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "${booking_userpool}")
@LambdaHandler(
		lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = false,
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables({
		@EnvironmentVariable(key = "tables_table", value = "${tables_table}"),
		@EnvironmentVariable(key = "reservations_table", value = "${reservations_table}"),
		@EnvironmentVariable(key = "Cognito_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_USER_POOL_ID),
		@EnvironmentVariable(key = "Client_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_CLIENT_ID)
})
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	private final AWSCognitoIdentityProvider cognitoClient;
	private final DynamoDB dynamoDB;
	private final String userPoolId;
	private final String clientId;
	private final Table tablesTable;
	private final Table reservationsTable;

	public ApiHandler() {
		cognitoClient = AWSCognitoIdentityProviderClientBuilder.standard().withRegion(System.getenv("region")).build();
		dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.standard().build());
		userPoolId = System.getenv("Cognito_ID");
		clientId = System.getenv("Client_ID");
		tablesTable = dynamoDB.getTable(System.getenv("tables_table"));
		reservationsTable = dynamoDB.getTable(System.getenv("reservations_table"));
	}

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		String path = (String) event.get("path");
		String httpMethod = (String) event.get("httpMethod");
		Map<String, Object> response = new HashMap<>();
		try {
			if ("/signup".equals(path) && "POST".equals(httpMethod)) {
				handleSignup(event, response);
			} else if ("/signin".equals(path) && "POST".equals(httpMethod)) {
				handleSignin(event, response);
			} else if ("/tables".equals(path) && "GET".equals(httpMethod)) {
				handleGetTables(response);
			} else if ("/tables".equals(path) && "POST".equals(httpMethod)) {
				handleCreateTable(event, response);
			} else if (path.matches("/tables/\\d+") && "GET".equals(httpMethod)) {
				handleGetTableById(path, response);
			} else if ("/reservations".equals(path) && "POST".equals(httpMethod)) {
				handleCreateReservation(event, response);
			} else if ("/reservations".equals(path) && "GET".equals(httpMethod)) {
				handleGetReservations(response);
			} else {
				throw new UnsupportedOperationException("Unsupported operation: " + path + " " + httpMethod);
			}
		} catch (Exception e) {
			response.put("statusCode", 400);
			response.put("body", "{\"message\": \"Something went wrong: " + e.getMessage() + "\"}");
		}
		return response;
	}

	private void handleSignup(Map<String, Object> event, Map<String, Object> response) throws Exception {
		Map<String, Object> body = parseRequestBody(event);
		String email = (String) body.get("email");
		String firstName = (String) body.get("firstName");
		String lastName = (String) body.get("lastName");
		String password = (String) body.get("password");

		AdminCreateUserRequest createUserRequest = new AdminCreateUserRequest()
				.withUserPoolId(userPoolId)
				.withUsername(email)
				.withUserAttributes(
						new AttributeType().withName("email").withValue(email),
						new AttributeType().withName("firstname").withValue(firstName),
						new AttributeType().withName("lastname").withValue(lastName))
				.withTemporaryPassword(password)
				.withMessageAction(MessageActionType.SUPPRESS);

		cognitoClient.adminCreateUser(createUserRequest);

		AdminSetUserPasswordRequest setUserPasswordRequest = new AdminSetUserPasswordRequest()
				.withUserPoolId(userPoolId)
				.withUsername(email)
				.withPassword(password)
				.withPermanent(true);

		cognitoClient.adminSetUserPassword(setUserPasswordRequest);
		response.put("statusCode", 200);
		response.put("body", "{\"message\": \"Sign-up process is successful\"}");
	}

	private void handleSignin(Map<String, Object> event, Map<String, Object> response) throws Exception {
		Map<String, Object> body = parseRequestBody(event);
		String email = (String) body.get("email");
		String password = (String) body.get("password");

		InitiateAuthRequest authRequest = new InitiateAuthRequest()
				.withAuthFlow(AuthFlowType.USER_PASSWORD_AUTH)
				.withAuthParameters(new HashMap<String, String>() {{
					put("USERNAME", email);
					put("PASSWORD", password);
				}})
				.withClientId(clientId);

		InitiateAuthResult authResult = cognitoClient.initiateAuth(authRequest);
		String accessToken = authResult.getAuthenticationResult().getAccessToken();
		response.put("statusCode", 200);
		response.put("body", "{\"accessToken\": \"" + accessToken + "\"}");
	}

	private void handleGetTables(Map<String, Object> response) throws Exception {
		ItemCollection<ScanOutcome> items = tablesTable.scan(new ScanSpec());
		List<Item> itemList = new ArrayList<>();
		items.forEach(itemList::add);
		itemList.sort(Comparator.comparing(item -> item.getInt("id")));

		ObjectMapper mapper = new ObjectMapper();
		String body = mapper.writeValueAsString(Collections.singletonMap("tables", itemList));
		response.put("statusCode", 200);
		response.put("body", body);
	}

	private void handleCreateTable(Map<String, Object> event, Map<String, Object> response) throws Exception {
		Map<String, Object> body = parseRequestBody(event);
		Item item = new Item()
				.withPrimaryKey("id", body.get("id"))
				.withString("number", (String) body.get("number"))
				.withString("places", (String) body.get("places"))
				.withBoolean("isVip", (Boolean) body.get("isVip"))
				.withNumber("minOrder", (Number) body.get("minOrder"));

		tablesTable.putItem(item);
		response.put("statusCode", 200);
		response.put("body", "{\"id\": \"" + body.get("id") + "\"}");
	}

	private void handleGetTableById(String path, Map<String, Object> response) throws Exception {
		int tableId = Integer.parseInt(path.split("/")[2]);
		Item item = tablesTable.getItem("id", tableId);
		if (item != null) {
			ObjectMapper mapper = new ObjectMapper();
			String body = mapper.writeValueAsString(item.asMap());
			response.put("statusCode", 200);
			response.put("body", body);
		} else {
			response.put("statusCode", 404);
			response.put("body", "{\"message\": \"Table not found\"}");
		}
	}

	private void handleCreateReservation(Map<String, Object> event, Map<String, Object> response) throws Exception {
		Map<String, Object> body = parseRequestBody(event);
		int tableNumber = (int) body.get("tableNumber");
		String reservationDate = (String) body.get("date");
		String slotTimeStart = (String) body.get("slotTimeStart");
		String slotTimeEnd = (String) body.get("slotTimeEnd");
		SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
		Date proposedTimeStart = timeFormat.parse(slotTimeStart);
		Date proposedTimeEnd = timeFormat.parse(slotTimeEnd);
		ItemCollection<ScanOutcome> reservations = reservationsTable.scan(new ScanSpec());

		for (Item item : reservations) {
			if (tableNumber == item.getInt("tableNumber") &&
					reservationDate.equals(item.getString("date"))) {
				Date bookedStartTime = timeFormat.parse(item.getString("slotTimeStart"));
				Date bookedEndTime = timeFormat.parse(item.getString("slotTimeEnd"));
				if (!(proposedTimeEnd.before(bookedStartTime) || proposedTimeStart.after(bookedEndTime))) {
					response.put("statusCode", 400);
					response.put("body", "{\"message\": \"Table already booked for this time slot.\"}");
					return;
				}
			}
		}

		Item reservation = new Item()
				.withPrimaryKey("tableNumber", tableNumber)
				.withString("date", reservationDate)
				.withString("slotTimeStart", slotTimeStart)
				.withString("slotTimeEnd", slotTimeEnd)
				.withString("userId", (String) body.get("userId"));
		reservationsTable.putItem(reservation);
		response.put("statusCode", 200);
		response.put("body", "{\"message\": \"Reservation successful\"}");
	}

	private void handleGetReservations(Map<String, Object> response) throws Exception {
		ItemCollection<ScanOutcome> items = reservationsTable.scan(new ScanSpec());
		List<Item> itemList = new ArrayList<>();
		items.forEach(itemList::add);

		ObjectMapper mapper = new ObjectMapper();
		String body = mapper.writeValueAsString(Collections.singletonMap("reservations", itemList));
		response.put("statusCode", 200);
		response.put("body", body);
	}

	private Map<String, Object> parseRequestBody(Map<String, Object> event) throws Exception {
		String body = (String) event.get("body");
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readValue(body, Map.class);
	}
}
