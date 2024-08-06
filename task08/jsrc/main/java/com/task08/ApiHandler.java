package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		OpenMeteoClient client = new OpenMeteoClient();
		APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

		try {
			String forecast = client.getWeatherForecast(50.4375, 30.5);
			response.setStatusCode(200);
			response.setBody(forecast);
		} catch (IOException e) {
			response.setStatusCode(500);
			response.setBody("{\"error\":\"" + e.getMessage() + "\"}");
		}

		return response;
	}
}
