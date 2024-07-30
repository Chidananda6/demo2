package com.task05;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

public class ApiHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

	private static final DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());
	private static final String TABLE_NAME = "Events";
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Override
	public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
		Map<String, Object> body = (Map<String, Object>) input.get("body");
		Integer principalId = (Integer) body.get("principalId");
		Map content = (Map) body.get("content");

		String eventId = UUID.randomUUID().toString();
		String createdAt = java.time.ZonedDateTime.now().toString();

		try {
			Table table = dynamoDB.getTable(TABLE_NAME);
			Item item = new Item()
					.withPrimaryKey("id", eventId)
					.withNumber("principalId", principalId)
					.withString("createdAt", createdAt)
					.withMap("body", content);
			table.putItem(item);

			return new ApiGatewayResponse(201, OBJECT_MAPPER.writeValueAsString(item.asMap()));
		} catch (Exception e) {
			e.printStackTrace();
			return new ApiGatewayResponse(500, "{\"message\": \"Error saving the event.\"}");
		}
	}
}

class ApiGatewayResponse {
	private final int statusCode;
	private final String body;

	public ApiGatewayResponse(int statusCode, String body) {
		this.statusCode = statusCode;
		this.body = body;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public String getBody() {
		return body;
	}
// Getters and Setters
}