/*
 * Copyright (C) 2012 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package interactivespaces.activity.impl.ros;

import interactivespaces.InteractiveSpacesException;
import interactivespaces.activity.Activity;
import interactivespaces.activity.component.ros.RosMessageRouterActivityComponent;
import interactivespaces.activity.component.ros.RoutableInputMessageListener;
import interactivespaces.activity.execution.ActivityMethodInvocation;

import java.io.IOException;
import java.util.Map;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.ros.message.interactivespaces_msgs.GenericMessage;

/**
 * An {@link Activity} which provides a set of named input ROS topics and a set
 * of named output ROS topics which will communicate via strings or JSON.
 * 
 * @author Keith M. Hughes
 */
public class BaseRoutableRosActivity extends BaseRosActivity {

	/**
	 * The JSON mapper.
	 */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Router for input and output messages.
	 */
	private RosMessageRouterActivityComponent<GenericMessage> router;

	@Override
	public void commonActivitySetup() {
		super.commonActivitySetup();

		router = addActivityComponent(new RosMessageRouterActivityComponent<GenericMessage>(
				"interactivespaces_msgs/GenericMessage",
				new RoutableInputMessageListener<GenericMessage>() {

					@SuppressWarnings("unchecked")
					@Override
					public void onNewRoutableInputMessage(String channelName,
							GenericMessage message) {
						if ("json".equals(message.type)) {
							try {
								callOnNewInputJson(channelName, message);
							} catch (Exception e) {
								getLog().error(
										"Could not process input message", e);
							}
						} else {
							callOnNewInputString(channelName, message);
						}
					}
				}));
	}

	/**
	 * Convert a map to a JSON string.
	 * 
	 * @param chaName
	 *            the name of the output channel to send the message on
	 * @param message
	 *            the message to send
	 */
	public String jsonStringify(Map<String, Object> map) {
		try {
			return MAPPER.writeValueAsString(map);
		} catch (Exception e) {
			throw new InteractiveSpacesException(
					"Could not serialize JSON object as string", e);
		}
	}

	/**
	 * Parse a JSON string and return the map.
	 * 
	 * @param data
	 *            the JSON string
	 * @return the map for the string
	 */
	public Map<String, Object> jsonParse(String data) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) MAPPER.readValue(
					data, Map.class);
			return map;
		} catch (Exception e) {
			throw new InteractiveSpacesException("Could not parse JSON string",
					e);
		}
	}

	/**
	 * A new JSON message is coming in.
	 * 
	 * @param channelName
	 *            name of the inpt channel the message came in on
	 * @param message
	 *            the message that came in
	 */
	public void onNewInputJson(String channelName, Map<String, Object> message) {
		// Default is to do nothing.
	}

	/**
	 * A new string message is coming in.
	 * 
	 * @param channelName
	 *            name of the input channel the message came in on
	 * @param message
	 *            the message that came in
	 */
	public void onNewInputString(String channelName, String message) {
		// Default is to do nothing.
	}

	/**
	 * Send an output JSON message.
	 * 
	 * @param chaName
	 *            the name of the output channel to send the message on
	 * @param message
	 *            the message to send
	 */
	public void sendOutputJson(String channelName, Map<String, Object> message) {
		GenericMessage outgoing = new GenericMessage();

		try {
			outgoing.type = "json";
			outgoing.message = MAPPER.writeValueAsString(message);

			router.writeOutputMessage(channelName, outgoing);
		} catch (Exception e) {
			getLog().error(
					String.format(
							"Could not write JSON message on output channel %s",
							channelName), e);
		}
	}

	/**
	 * Send an output string message.
	 * 
	 * @param channelName
	 *            the name of the output channel to send the message on
	 * @param message
	 *            the message to send
	 */
	public void sendOutputString(String channelName, String message) {
		GenericMessage outgoing = new GenericMessage();
		try {
			outgoing.type = "string";
			outgoing.message = message;

			router.writeOutputMessage(channelName, outgoing);
		} catch (Exception e) {
			getLog().error(
					String.format(
							"Could not write message on output channel %s",
							channelName), e);
		}
	}

	/**
	 * Call the {@link #onNewInputJson(String, Map)} method.
	 * 
	 * @param channelName
	 *            the name of the channel
	 * @param message
	 *            the message in JSON format
	 * 
	 * @throws IOException
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 */
	private void callOnNewInputJson(String channelName, GenericMessage message)
			throws IOException, JsonParseException, JsonMappingException {
		ActivityMethodInvocation invocation = getExecutionContext()
				.enterMethod();

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) MAPPER.readValue(
					message.message, Map.class);
			onNewInputJson(channelName, map);
		} finally {
			getExecutionContext().exitMethod(invocation);
		}
	}

	/**
	 * Call {@link #onNewInputString(String, String)} in a safe manner.
	 * 
	 * @param channelName
	 *            name of the channel
	 * @param message
	 *            message for the channel
	 */
	private void callOnNewInputString(String channelName, GenericMessage message) {
		ActivityMethodInvocation invocation = getExecutionContext()
				.enterMethod();

		try {
			onNewInputString(channelName, message.message);
		} finally {
			getExecutionContext().exitMethod(invocation);
		}
	}
}
