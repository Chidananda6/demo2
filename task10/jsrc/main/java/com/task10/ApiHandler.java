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

import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

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
	private final String clientId;

	public ApiHandler() {
		this.cognitoClient = AWSCognitoIdentityProviderClientBuilder.standard()
				.withRegion(System.getenv("REGION"))
				.build();

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
}
