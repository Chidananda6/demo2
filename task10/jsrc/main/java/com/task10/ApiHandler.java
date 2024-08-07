package com.task10;


import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.HashMap;
import java.util.Map;

public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private final DynamoDB dynamoDB;
	private final String TABLES_TABLE_NAME = "Tables";
	private final String RESERVATIONS_TABLE_NAME = "Reservations";

	public ApiHandler() {
		this.dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());
	}

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
		String path = (String) input.get("path");
		String method = (String) input.get("method");
		Map<String, Object> response = new HashMap<>();

		try {
			switch (path) {
				case "/signup":
					response = handleSignup(input);
					break;
				case "/signin":
					response = handleSignin(input);
					break;
				case "/tables":
					response = method.equals("GET") ? getTables() : putTable(input);
					break;
				case "/reservations":
					response = method.equals("GET") ? getReservations() : putReservation(input);
					break;
				default:
					response.put("statusCode", 404);
					response.put("body", "Endpoint not found");
			}
		} catch (Exception e) {
			response.put("statusCode", 500);
			response.put("body", "Error processing request: " + e.getMessage());
		}

		return response;
	}

	private Map<String, Object> handleSignup(Map<String, Object> input) {
		// Implement Cognito signup logic here
		// This is a placeholder
		return Map.of("statusCode", 200, "body", "Signup successful");
	}

	private Map<String, Object> handleSignin(Map<String, Object> input) {
		// Implement Cognito signin logic here
		// This is a placeholder
		return Map.of("statusCode", 200, "body", "Signin successful", "accessToken", "example-token");
	}

	private Map<String, Object> getTables() {
		Table table = dynamoDB.getTable(TABLES_TABLE_NAME);
		Map<String, Object> result = new HashMap<>();
		// Implement logic to scan or query the DynamoDB table
		// This is a placeholder
		result.put("statusCode", 200);
		result.put("tables", "List of tables");
		return result;
	}

	private Map<String, Object> putTable(Map<String, Object> input) {
		Table table = dynamoDB.getTable(TABLES_TABLE_NAME);
		// Implement logic to put item into the DynamoDB table
		// This is a placeholder
		return Map.of("statusCode", 200, "body", "Table added");
	}

	private Map<String, Object> getReservations() {
		Table table = dynamoDB.getTable(RESERVATIONS_TABLE_NAME);
		Map<String, Object> result = new HashMap<>();
		// Implement logic to scan or query the DynamoDB table
		// This is a placeholder
		result.put("statusCode", 200);
		result.put("reservations", "List of reservations");
		return result;
	}

	private Map<String, Object> putReservation(Map<String, Object> input) {
		Table table = dynamoDB.getTable(RESERVATIONS_TABLE_NAME);
		// Implement logic to put item into the DynamoDB table
		// This is a placeholder
		return Map.of("statusCode", 200, "body", "Reservation added");
	}
}
