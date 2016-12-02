package com.github.pcre;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <h1>PURPOSE</h1>
 * <p>
 * <a href="http://www.politicalcompass.org/test">politicalcompass.org</a> provides a test, whose purpose is to quantify one's
 * political tendencies with a 2D coordinate value. Many people have criticized
 * the test for being biased, but criticisms against the test have so far been
 * mostly qualitative, perhaps due to the fact that the website has not published
 * the scoring mechanism for the test. This module aims to provide people with
 * the most basic tool for quantitatively analyzing the test by
 * revealing the details of the test's scoring mechanism.
 * 
 * <h1>DESCRIPTION OF THE SCORING MECHANISM</h1>
 * <p>
 * Each question answered increments either the economic or social score 
 * (but not both) by an integral amount specified by the answer choice for 
 * that question. The increments are different for different questions, although
 * they are monotonic for every question. For example, if the value associated
 * with "Agree" is greater than that with "Disagree", then that with "Strongly Agree"
 * is also greater than that with "Agree".
 * 
 * At the end of the test, the each point total is adjusted for bias and scaled
 * so that the maximum raw score corresponds to +10.00 and the minimum raw score
 * corresponds to -10.00. This is done like following:
 * 
 * <p>
 * <i>(raw - mean) / (max - min) * 20</i>
 * 
 * <p>
 * where the <i>mean</i> is the average of <i>max</i> and <i>min</i>.
 * 
 * <h1>HOW TO USE THE MODULE</h1>
 * <p>
 * <i>NOTE: YOU WILL NEED TO BE CONNECTED TO THE INTERWEBS</i>
 * 
 * <p>
 * Call {@link #pages1Through5()} and {@link #page6()} in {@link #main(String[])}.
 * 
 * {@link #pages1Through5()} prints to console tab-delimited rows that 
 * look like following:
 * 
 * <p>
 * %d %d [ES] 0 %d %d %d
 * 
 * <p>
 * Each row shows scoring detail of a question.
 * <li>
 * The 1st value represents the page the question belongs to (1-6).
 * <li>
 * The 2nd value represents the number of the question on that page.
 * <li>
 * The 3rd value represents the axis affected by the question. 
 * "E" is for economic, "S" is for social.
 * <li>
 * The 4th-7th values represent scores given for 
 * "Strongly Disagree", "Disagree", "Agree" and "Strongly Agree", respectively.
 * "Strongly Disagree" is chosen as reference and is set to 0 for every question.
 * </li>
 * 
 * <p>
 * {@link #page6()} prints to console tab-delimited rows that 
 * look like following:
 * 
 * <p>
 * %d %d [ES] 0.00 %.2f %.2f %.2f
 * 
 * <p>
 * These rows are the same as the ones produced by {@link #pages1Through5()} except
 * the score values are given as floats. You'll need to convert these values to
 * integer values by scaling them. There might be a systematic way of doing this,
 * but it's easy enough to do it by inspection.
 * 
 * <p>
 * To do the test programmatically, call {@link #run(Map)}.
 * 
 * Populate a {@link Map} with question ({@code String}) - answer ({@code int})
 * pairs.
 * 
 * Valid question names are found in {@link #QUESTIONS}.
 * 
 * Valid answer values are:
 * <li> Strongly Disagree: 0
 * <li> Disagree: 1
 * <li> Agree: 2
 * <li> Strongly Agree: 3
 * 
 * @author Kurt Ahn
 */
public class PCReversal {
	private static final String[][] QUESTIONS = {
		{
			"globalisationinevitable",
			"countryrightorwrong",
			"proudofcountry",
			"racequalities",
			"enemyenemyfriend",
			"militaryactionlaw",
			"fusioninfotainment"
		},
		{
			"classthannationality",
			"inflationoverunemployment",
			"corporationstrust",
			"fromeachability",
			"bottledwater",
			"landcommodity",
			"manipulatemoney",
			"protectionismnecessary",
			"companyshareholders",
			"richtaxed",
			"paymedical",
			"penalisemislead",
			"freepredatormulinational",
			"freermarketfreerpeople",
		},
		{
			"abortionillegal",
			"questionauthority",
			"eyeforeye",
			"taxtotheatres",
			"schoolscompulsory",
			"ownkind",
			"spankchildren",
			"naturalsecrets",
			"marijuanalegal",
			"schooljobs",
			"inheritablereproduce",
			"childrendiscipline",
			"savagecivilised",
			"abletowork",
			"represstroubles",
			"immigrantsintegrated",
			"goodforcorporations",
			"broadcastingfunding",
		},
		{
			"libertyterrorism",
			"onepartystate",
			"serveillancewrongdoers",
			"deathpenalty",
			"societyheirarchy",
			"abstractart",
			"punishmentrehabilitation",
			"wastecriminals",
			"businessart",
			"mothershomemakers",
			"plantresources",
			"peacewithestablishment",
		},
		{
			"astrology",
			"moralreligious",
			"charitysocialsecurity",
			"naturallyunlucky",
			"schoolreligious",
		},
		{
			"sexoutsidemarriage",
			"homosexualadoption",
			"pornography",
			"consentingprivate",
			"naturallyhomosexual",
			"opennessaboutsex"
		}
	};
	
	private static class ScoreInt {
		public final int economic, social;
		
		public ScoreInt(int economic, int social) {
			this.economic = economic;
			this.social = social;
		}
		
		public ScoreInt sub(ScoreInt s) {
			return new ScoreInt(economic - s.economic, social - s.social);
		}
		
		@Override
		public String toString() {
			return String.format("E=%d, S=%d", economic, social);
		}
	}
	
	private static class ScoreFloat {
		public final float economic, social;
		
		public ScoreFloat(float economic, float social) {
			this.economic = economic;
			this.social = social;
		}
		
		public ScoreFloat sub(ScoreFloat s) {
			return new ScoreFloat(economic - s.economic, social - s.social);
		}
		
		@Override
		public String toString() {
			return String.format("E=%.2f, S=%.2f", economic, social);
		}
	}
	
	private static class QuestionInt {
		public static int ECONOMIC = 0, SOCIAL = 1;
		
		public final int axis;
		
		public final int[] increments;
		
		public QuestionInt(int axis, int[] increments) {
			if (axis != ECONOMIC && axis != SOCIAL)
				throw new IllegalArgumentException(String.format(
						"Axis (%d) needs to be either ECONOMIC (%d) or SOCIAL(%d).",
						axis, ECONOMIC, SOCIAL));
			if (increments.length != 3)
				throw new IllegalArgumentException(String.format(
						"Length of increments (%d) needs to be 3.",
						increments.length));
			
			this.axis = axis;
			this.increments = increments;
		}
		
		@Override
		public String toString() {
			return String.format("%s\t0\t%d\t%d\t%d",
					axis == ECONOMIC ? "E" : "S",
					increments[0], increments[1], increments[2]);
		}
	}
	
	private static class QuestionFloat {
		public static int ECONOMIC = 0, SOCIAL = 1;
		
		public final int axis;
		
		public final float[] increments;
		
		public QuestionFloat(int axis, float[] increments) {
			if (axis != ECONOMIC && axis != SOCIAL)
				throw new IllegalArgumentException(String.format(
						"Axis (%d) needs to be either ECONOMIC (%d) or SOCIAL(%d).",
						axis, ECONOMIC, SOCIAL));
			if (increments.length != 3)
				throw new IllegalArgumentException(String.format(
						"Length of increments (%d) needs to be 3.",
						increments.length));
			
			this.axis = axis;
			this.increments = increments;
		}
		
		@Override
		public String toString() {
			return String.format("%s\t0\t%.2f\t%.2f\t%.2f",
					axis == ECONOMIC ? "E" : "S",
					increments[0], increments[1], increments[2]);
		}
	}
	
	private static String parameters(Map<String, Integer> p) {
		StringJoiner joiner = new StringJoiner("&");
		p.entrySet().stream().forEach(
				e -> {
			try {
				joiner.add(
						URLEncoder.encode(e.getKey(), "UTF-8") + "=" +
						URLEncoder.encode(e.getValue().toString(), "UTF-8"));
			} catch (Exception x) {
				x.printStackTrace();
			}
		});
		return joiner.toString();
	}
	
	private static int extractInt(String source, String regex) {
		Matcher broad = Pattern
				.compile(regex)
				.matcher(source);
		if (broad.find()) {
			Matcher narrow = Pattern.compile("-?\\d+").matcher(broad.group(0));
			if (narrow.find())
				return Integer.parseInt(narrow.group(0));
		}
		throw new RuntimeException("AHHH?");
	}
	
	private static float extractFloat(String source, String regex) {
		Matcher broad = Pattern
				.compile(regex)
				.matcher(source);
		if (broad.find()) {
			Matcher narrow = Pattern.compile("-?\\d+\\.\\d+").matcher(broad.group(0));
			if (narrow.find())
				return Float.parseFloat(narrow.group(0));
		}
		throw new RuntimeException("AHHH?");
	}
	
	private static HttpURLConnection connect(Map<String, Integer> response)
			throws IOException {
		URL url = new URL("https://www.politicalcompass.org/test");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		String parameters = parameters(response);
//		System.out.println(parameters);
		connection.setRequestProperty(
				"Content-Type",
				"application/x-www-form-urlencoded; charset=UTF-8");
		connection.setRequestProperty(
				"Content-Length",
				String.valueOf(parameters.length()));
		connection.connect();
		try (OutputStream output = connection.getOutputStream()) {
			output.write(parameters.getBytes());
		}
		return connection;
	}
	
	private static ScoreInt score1Through5(
			int page, Map<String, Integer> response) 
					throws IOException {
		return score1Through5(page, response, new ScoreInt(0, 0));
	}
	
	private static ScoreInt score1Through5(
			int page, Map<String, Integer> response, ScoreInt carry) 
					throws IOException {
		if (page < 1 || page > 5)
			throw new IllegalArgumentException(String.format(
					"You entered %d for page? " +
					"You didn't learn page number has to be in [1, 5] in " +
					"primary school?",
					page));
		
		Map<String, Integer> responseCopy = new HashMap<>(response);
		responseCopy.put("page", page);
		responseCopy.put("carried_ec", carry == null ? 0 : carry.economic);
		responseCopy.put("carried_soc", carry == null ? 0 : carry.social);
		
		HttpURLConnection connection = connect(responseCopy);
		try (InputStream input = connection.getInputStream()) {
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(input, "UTF-8"));
			StringBuilder source = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null)
				source.append(line + "\n");
			
			int e = extractInt(
					source.toString(),
					"'carried_ec' type='hidden' value='-?\\d+'");
			int s = extractInt(
					source.toString(),
					"'carried_soc' type='hidden' value='-?\\d+'");
			return new ScoreInt(e, s);
		}
	}
	
	private static ScoreFloat score6(Map<String, Integer> response)
			throws IOException {
		return score6(response, new ScoreInt(0, 0));
	}
	
	private static ScoreFloat score6(Map<String, Integer> response, ScoreInt carry)
			throws IOException {
		Map<String, Integer> responseCopy = new HashMap<>(response);
		responseCopy.put("page", 6);
		responseCopy.put("carried_ec", carry == null ? 0 : carry.economic);
		responseCopy.put("carried_soc", carry == null ? 0 : carry.social);
		
		HttpURLConnection connection = connect(responseCopy);
		try (InputStream input = connection.getInputStream()) {
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(input, "UTF-8"));
			StringBuilder source = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null)
				source.append(line + "\n");
//			System.out.println(source);
			float e = extractFloat(
					source.toString(),
					"ec=-?\\d+\\.\\d+");
			float s = extractFloat(
					source.toString(),
					"soc=-?\\d+\\.\\d+");
			return new ScoreFloat(e, s);
		}
	}
	
	private static void pages1Through5() throws IOException {
		for (int page = 1; page < 6; ++page) {
			Map<String, Integer> baseResponse = new HashMap<>();
			for (String q : QUESTIONS[page - 1])
				baseResponse.put(q, 0);
			ScoreInt base = score1Through5(page, baseResponse);
//			System.out.println("Base: " + base);
			
			for (int q = 0; q < QUESTIONS[page - 1].length; ++q) {
				Map<String, Integer> response = new HashMap<>();
				
				int axis = QuestionInt.ECONOMIC;
				int[] increments = new int[3];
				
				for (int answer = 1; answer < 4; ++answer) {
					for (int qq = 0; qq < QUESTIONS[page - 1].length; ++qq)
						response.put(QUESTIONS[page - 1][qq], qq == q ? answer : 0);
					
					ScoreInt difference = score1Through5(page, response).sub(base);
					if (difference.economic == 0) {
						axis = QuestionInt.SOCIAL;
						increments[answer - 1] = difference.social;
					} else {
						assert difference.social == 0;
						axis = QuestionInt.ECONOMIC;
						increments[answer - 1] = difference.economic;
					}
				}
				
				System.out.println(String.format(
						"%d\t%d\t%s",
						page,
						q + 1,
						new QuestionInt(axis, increments)));
			}
		}
	}
	
	private static void page6() throws IOException {
		Map<String, Integer> baseResponse = new HashMap<>();
		for (String q : QUESTIONS[5])
			baseResponse.put(q, 0);
		ScoreFloat base = score6(baseResponse);
		
		for (int q = 0; q < QUESTIONS[5].length; ++q) {
			Map<String, Integer> response = new HashMap<>();
			
			int axis = QuestionInt.ECONOMIC;
			float[] increments = new float[3];
			
			for (int answer = 1; answer < 4; ++answer) {
				for (int qq = 0; qq < QUESTIONS[5].length; ++qq)
					response.put(QUESTIONS[5][qq], qq == q ? answer : 0);
				
				ScoreFloat difference = score6(response).sub(base);
				if (difference.economic == 0) {
					axis = QuestionInt.SOCIAL;
					increments[answer - 1] = difference.social;
				} else {
					assert difference.social == 0;
					axis = QuestionInt.ECONOMIC;
					increments[answer - 1] = difference.economic;
				}
			}
			
			System.out.println(String.format(
					"%d\t%d\t%s",
					6,
					q + 1,
					new QuestionFloat(axis, increments)));
		}
	}
	
	private static ScoreFloat run(Map<String, Integer> response) throws IOException {
		ScoreInt carry = null;
		for (int page = 1; page < 6; ++page) {
			Map<String, Integer> pageResponse = new HashMap<>();
			for (String q : QUESTIONS[page - 1])
				pageResponse.put(q, response.get(q));
			carry = score1Through5(page, pageResponse, carry);
		}
		
		Map<String, Integer> pageResponse = new HashMap<>();
		for (String q : QUESTIONS[5])
			pageResponse.put(q, response.get(q));
		return score6(pageResponse, carry);
	}
	
	public static void main(String[] args) throws IOException {
//		System.out.println(run(
//				Arrays.stream(QUESTIONS)
//						.<String> flatMap(a -> Arrays.stream(a))
//						.collect(Collectors.toMap(q -> q, q -> 2))
//		));
		
		pages1Through5();
		page6();
	}
}
