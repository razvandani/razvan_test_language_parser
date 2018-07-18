package com.tourist.app;

import com.tourist.utils.Command;
import com.tourist.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public class TouristApp {

	private static final String FETCH_COMMAND = "fetch";
	private static final String FOREACH_COMMAND = "foreach";
	private static final String DOWNLOAD_COMMAND = "download";

	private static final Logger LOG = LoggerFactory.getLogger(TouristApp.class);
	private static RestTemplate restTemplate = new RestTemplate();

	public static void main(String[] args)
			throws Exception {
		if (args.length > 0) {
			List<Command> commands = new ArrayList<>();
			String filename = args[0];
			String line;
			BufferedReader bufferedReader = new BufferedReader(new FileReader(filename));

			while ((line = bufferedReader.readLine()) != null) {
				StringTokenizer stringTokenizer = new StringTokenizer(line);
				while (stringTokenizer.hasMoreElements()) {
					String command = stringTokenizer.nextElement().toString();
					String resultVariable = stringTokenizer.nextElement().toString();
					String parameter = stringTokenizer.nextElement().toString();
					//System.out.println(command + " " + resultVariable + " " + parameter);
					Command cmd = new Command(command, resultVariable, parameter);
					commands.add(cmd);
				}
			}

			populateCommandRelationships(commands);

			processCommands(commands, null);
		}
	}

	private static void populateCommandRelationships(List<Command> commands) {
		for (int i = 0; i < commands.size(); i++) {
			Command command = commands.get(i);

			if (i > 0) {
				command.setPreviousCommand(commands.get(i - 1));
			}

			if (i < commands.size() - 1) {
				command.setNextCommand(commands.get(i + 1));
			}

			if (command.getCommand().equals(FOREACH_COMMAND)) {
				command.setChildCommands(commands.subList(i + 1, commands.size()));
			}
		}
	}

	private static void processCommands(
			List<Command> commands, List variables)
			throws Exception {
		for (Command command : commands) {
			System.out.println("command.getCommand() = " + command.getCommand());
			System.out.println("command.getVariableName() = " + command.getVariableName());
			System.out.println("command.getParameter() = " + command.getParameter());

			if (command.getCommand().equals(FETCH_COMMAND)) {

				String url = command.getParameter();

				if (variables != null) {
					for (Object variable : variables) {
						url = convertUrl(url, variable);
					}
				}

				if (!url.contains("{")) {
					String jsonResponseAsString = getResponseByUrl(url);
					command.setJsonResponseAsString(jsonResponseAsString);
					System.out.println("jsonResponseAsString = " + jsonResponseAsString);
					if (variables == null) {
						variables = new ArrayList();
					}

					variables.add(jsonResponseAsString);
				} else {
					System.out.println("invalid placeholders for fetch");
				}
			} else if (command.getCommand().equals(FOREACH_COMMAND)) {
				Command previusCommand = command.getPreviousCommand();

				if (previusCommand.getJsonResponseAsString() != null) {
					String jsonPath = command.getParameter().substring(command.getParameter().indexOf(".") + 1);
					List<Map> jsonArray = getJsonArray(previusCommand.getJsonResponseAsString(), jsonPath);
					CountDownLatch countDownLatch = new CountDownLatch(jsonArray.size());

					for (Map jsonMap : jsonArray) {
						System.out.println("FOR EAXH jsonMap = " + jsonMap);
						if (variables == null) {
							variables = new ArrayList();
						}

						variables.add(jsonMap);



						CompletableFuture<String> future = CompletableFuture.supplyAsync(new MySupplier(command.getChildCommands(),
								new ArrayList(variables), countDownLatch));


//						processCommands(command.getChildCommands(), variables);
//						variables = null;
					}
					countDownLatch.await();
				} else {
					throw new RuntimeException("hmmmmmmmmmm?");
				}
			} else if (command.getCommand().equals(DOWNLOAD_COMMAND)) {
				System.out.println("\"download\" = " + "download");
				String url = command.getVariableName();
				String localPath = command.getParameter();
				if (variables != null) {
					for (Object variable : variables) {
						url = convertUrl(url, variable);
						localPath = convertUrl(localPath, variable);
					}
				}

				System.out.println("DOWNLOAD url = " + url);
				System.out.println("DOWNLOAD localPath = " + localPath);

				if (!url.contains("{")) {
					downloadImage(url, localPath);
				} else {
					System.out.println("invalid placeholders for download");
				}
			}
		}
	}

	private static class MySupplier implements Supplier<String> {
		private List<Command> commands;
		private List variables;
		private CountDownLatch countDownLatch;

		public MySupplier(List<Command> commands, List variables, CountDownLatch countDownLatch) {
			this.commands = commands;
			this.variables = variables;
			this.countDownLatch = countDownLatch;
		}

		@Override public String get() {
			try {
				System.out.println("\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\" = "
						+ "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
				processCommands(commands, variables);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				countDownLatch.countDown();
			}
			return null;
		}
	}

	static String getResponseByUrl(String url)
			throws Exception {
		System.out.println("url getResponseByUrl = " + url);
		String response = null;
		try {
			response = restTemplate.getForEntity(new URI(url), String.class).getBody();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return response;
	}

	static void downloadImage(
			String url, String path)
			throws UnsupportedEncodingException {
		String decodedURL = URLDecoder.decode(url, "UTF-8");
		try {
			InputStream in = new URL(decodedURL).openStream();
			Files.copy(in, Paths.get(path));

		} catch (NoSuchFileException e) {
			LOG.error("cannot find the file " + path, e);
		} catch (FileAlreadyExistsException e) {
			LOG.error("File already exists " + path, e);
		} catch (IOException e) {
			LOG.error("Something went wrong when downloading file " + decodedURL + " to path " + path, e);
		} catch (Exception e) {
			LOG.error(e.getMessage());

		}
	}

	private static List<Map> getJsonArray(
			String jsonContent, String jsonPath)
			throws IOException {
		List<Map> result = null;
		String[] pathElements = jsonPath.split("\\.");
		Map jsonMap = JsonUtils.getObjectMapper().readValue(jsonContent, Map.class);
		Map jsonSubmap = jsonMap;

		for (int i = 0; i < pathElements.length; i++) {
			String pathElement = pathElements[i];

			if (i < pathElements.length - 1) {
				jsonSubmap = (Map) jsonSubmap.get(pathElement);
			} else {
				result = (List<Map>) jsonSubmap.get(pathElement);
			}
		}

		return result;
	}

	private static Object getFirstJsonObject(
			Map jsonMap, String jsonPath) {
		Object resultObject = null;

		String[] pathElements = jsonPath.split("\\.");

		for (int i = 0; i < pathElements.length; i++) {
			String pathElement = pathElements[i];
			Integer arrayIndex = null;
			if (pathElement.endsWith("]")) { // is an array element
				String[] pathElementSubelements = pathElement.split("\\[");
				pathElement = pathElementSubelements[0];
				arrayIndex = new Integer(
						pathElementSubelements[1].substring(0, pathElementSubelements[1].length() - 1));
				//System.out.println("pathElement = " + pathElement);
				//System.out.println(arrayIndex);
			}

			Object jsonElement = jsonMap.get(pathElement);

			if (jsonElement != null) {
				if (arrayIndex != null) {
					List jsonElementList = (List) jsonElement;

					if (jsonElementList.size() > arrayIndex) {
						jsonMap = (Map) ((List) jsonElement).get(arrayIndex);
					} else {
						break;
					}
				} else if (jsonElement instanceof Map) {
					jsonMap = (Map) jsonElement;
				} else {
					resultObject = jsonElement;
				}
			} else {
				break;
			}
		}

		return resultObject;
	}

	//getFirstJsonObject(jsonArray.get(0), "address[0].coordinate.north");

	private static String convertUrl(
			String url, Object json)
			throws IOException {
		System.out.println("url convertUrl before = " + url);
 		Map jsonMap;

		if (json instanceof Map) {
			jsonMap = (Map) json;
		} else if (json instanceof String) {
			jsonMap = JsonUtils.getObjectMapper().readValue((String) json, Map.class);
		} else {
			throw new RuntimeException("invalid json");
		}

		String resultUrl = url;
		//fetch v https://api.foursquare.com/v2/venues/search?ll={c.address[0].coordinate.north,c.address[0].coordinate.east}&client_id=CLIENT_ID&client_secret=CLIENT_SECRET&intent=match&name={c.displayName}&v=20180401
		int openBracketIndex = resultUrl.indexOf("{");

		while (openBracketIndex > -1) {
			int closedBracketIndex = resultUrl.indexOf("}", openBracketIndex+1);
			String jsonVariables = resultUrl.substring(openBracketIndex + 1, closedBracketIndex);
			String[] jsonVariableArray = jsonVariables.split(",");
			StringBuilder replacementStringBuilder = new StringBuilder();
			boolean stuffFound = true;

			for (String jsonVariable : jsonVariableArray) {
				jsonVariable = jsonVariable.substring(jsonVariable.indexOf(".") + 1);
				Object jsonObject = getFirstJsonObject(jsonMap, jsonVariable);

				if (jsonObject == null) {
					stuffFound = false;
					break;
				}

				replacementStringBuilder.append(URLEncoder.encode(jsonObject.toString(), "UTF-8")).append(",");
			}

			if (stuffFound) {
				replacementStringBuilder.deleteCharAt(replacementStringBuilder.length() - 1);
				resultUrl = resultUrl.replace("{" + jsonVariables + "}", replacementStringBuilder.toString());

			}

			openBracketIndex = resultUrl.indexOf("{", openBracketIndex+1);
		}

		System.out.println("url convertUrl after = " + resultUrl);

		return resultUrl;
	}
}
