package com.task05;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = false,
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED)
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());
	private static final String TABLE_NAME = "cmtr-36484994-Events-test";  // Updated table name
	private static final ObjectMapper objectMapper = new ObjectMapper();

	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
		Map<String, Object> response = new HashMap<>();
		try {
			// Validate input
			if (input.get("principalId") == null || input.get("content") == null) {
				throw new IllegalArgumentException("Missing required fields: 'principalId' or 'content'.");
			}

			Map<String, String> content = (Map<String, String>) input.get("content");
			Integer principalId = Integer.valueOf(input.get("principalId").toString());
			String eventId = UUID.randomUUID().toString();
			String createdAt = java.time.ZonedDateTime.now().toString();

			// Create an event item
			Item event = new Item()
					.withPrimaryKey("id", eventId)
					.withNumber("principalId", principalId)
					.withString("createdAt", createdAt)
					.withMap("body", content);

			// Save the event item in DynamoDB
			Table table = dynamoDB.getTable(TABLE_NAME);
			table.putItem(event);

			// Prepare the response
			response.put("statusCode", 201);  // Ensure statusCode is correctly set in the success path
			response.put("body", objectMapper.writeValueAsString(event.asMap()));
		} catch (IllegalArgumentException e) {
			context.getLogger().log("Validation Error: " + e.getMessage());
			response.put("statusCode", 400);  // Make sure to include statusCode in all branches
			response.put("body", e.getMessage());
		} catch (Exception e) {
			context.getLogger().log("Internal Error: " + e.getMessage());
			response.put("statusCode", 500);  // Make sure to include statusCode in all branches
			response.put("body", "Internal server error occurred.");
		}
		return response;
	}
}
