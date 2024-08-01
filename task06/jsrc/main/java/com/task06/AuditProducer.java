package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.fasterxml.uuid.Generators;
import java.util.Map;

public class AuditProducer implements RequestHandler<DynamodbEvent, String> {

	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
	private final DynamoDB dynamoDB = new DynamoDB(client);
	private final String auditTableName = System.getenv("TARGET_TABLE");

	@Override
	public String handleRequest(DynamodbEvent event, Context context) {
		for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
			if ("INSERT".equals(record.getEventName())) {
				handleInsert(record);
			} else if ("MODIFY".equals(record.getEventName())) {
				handleModify(record);
			}
		}
		return "Successfully processed " + event.getRecords().size() + " records.";
	}

	private void handleInsert(DynamodbEvent.DynamodbStreamRecord record) {
		Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage = record.getDynamodb().getNewImage();
		String id = Generators.timeBasedGenerator().generate().toString();
		String itemKey = newImage.get("key").getS();
		long value = Long.parseLong(newImage.get("value").getN());

		Map<String, AttributeValue> item = Map.of(
				"id", new AttributeValue(id),
				"itemKey", new AttributeValue(itemKey),
				"modificationTime", new AttributeValue(Long.toString(System.currentTimeMillis())),
				"newValue", new AttributeValue().withM(Map.of(
						"key", new AttributeValue(itemKey),
						"value", new AttributeValue(Long.toString(value))
				))
		);

		client.putItem(auditTableName, item);
	}

	private void handleModify(DynamodbEvent.DynamodbStreamRecord record) {
		Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> oldImage = record.getDynamodb().getOldImage();
		Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage = record.getDynamodb().getNewImage();

		String id = Generators.timeBasedGenerator().generate().toString();
		String itemKey = newImage.get("key").getS();
		long oldValue = Long.parseLong(oldImage.get("value").getN());
		long newValue = Long.parseLong(newImage.get("value").getN());

		Map<String, AttributeValue> item = Map.of(
				"id", new AttributeValue(id),
				"itemKey", new AttributeValue(itemKey),
				"modificationTime", new AttributeValue(Long.toString(System.currentTimeMillis())),
				"updatedAttribute", new AttributeValue("value"),
				"oldValue", new AttributeValue(Long.toString(oldValue)),
				"newValue", new AttributeValue(Long.toString(newValue))
		);

		client.putItem(auditTableName, item);
	}
}
