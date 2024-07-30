package com.task05;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	private final DynamoDB dynamoDB = new DynamoDB(client);
	private final Table table = dynamoDB.getTable("Events");

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
		ObjectMapper objectMapper = new ObjectMapper();
		String principalId = String.valueOf(input.get("principalId"));
		Map<String, String> content = (Map<String, String>) input.get("content");

		String eventId = UUID.randomUUID().toString();
		String createdAt = Instant.now().toString();

		Item item = new Item()
				.withPrimaryKey("id", eventId)
				.withNumber("principalId", Integer.parseInt(principalId))
				.withString("createdAt", createdAt)
				.withMap("body", content);

		table.putItem(item);

		return Map.of(
				"statusCode", 201,
				"event", item.asMap()
		);
	}
}