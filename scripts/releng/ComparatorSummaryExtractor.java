
/*******************************************************************************
 *  Copyright (c) 2013, 2025 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Hannes Wellmann - Convert to plain Java scripts
 *******************************************************************************/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import utilities.OS;

/**
 * This class is responsible for extracting the relevent "Debug" messages from
 * the huge maven debug log.
 *
 * @author davidw
 */
public class ComparatorSummaryExtractor {

	private static final String EOL = System.lineSeparator();

	public static void main(String[] args) throws IOException {
		ComparatorSummaryExtractor extractor = new ComparatorSummaryExtractor();
		extractor.buildDirectory = OS.readProperty("buildDirectory");
		extractor.comparatorRepo = OS.readProperty("comparatorRepo");
		buildLogsDirectory = Path.of(extractor.buildDirectory, "buildlogs");
		comparatorLogsDirectory = buildLogsDirectory.resolve("comparatorlogs");
		extractor.processBuildfile();
	}

	private String comparatorRepo = "comparatorRepo";
	private String buildDirectory;
	private static Path buildLogsDirectory;
	private static Path comparatorLogsDirectory;

	private static final Pattern MAIN_PATTERN = Pattern.compile(
			"^\\[WARNING\\].*eclipse.platform.releng.aggregator/(.*): baseline and build artifacts have same version but different contents");
	private static final Predicate<String> NO_CLASSIFIER_PATTERN = l -> l.contains("no-classifier:");
	private static final Predicate<String> CLASSIFIER_SOURCES_PATTERN = l -> l.contains("classifier-sources:");
	private static final Predicate<String> CLASSIFIER_SOURCES_FEATURE_PATTERN = l -> l
			.contains("classifier-sources-feature:");
	private static final Predicate<String> SIGN_PATTERN = Pattern.compile("META-INF/(ECLIPSE_|CODESIGN).(RSA|SF)")
			.asPredicate();
	private static final Predicate<String> DOC_NAME_PATTERN = Pattern.compile("eclipse\\.platform\\.common.*\\.doc\\.")
			.asPredicate();
	// jar pattern added for bug 416701
	private static final Predicate<String> JAR_PATTERN = l -> l.contains(".jar");
	private static final List<Predicate<String>> PURE_SIGNATURE_PATTERNS = List.of(NO_CLASSIFIER_PATTERN,
			CLASSIFIER_SOURCES_PATTERN, CLASSIFIER_SOURCES_FEATURE_PATTERN, SIGN_PATTERN);
	private static final List<Predicate<String>> PURE_SIGNATURE_PLUS_INNER_JAR_PATTERNS = List.of(NO_CLASSIFIER_PATTERN,
			CLASSIFIER_SOURCES_PATTERN, CLASSIFIER_SOURCES_FEATURE_PATTERN, SIGN_PATTERN, JAR_PATTERN);

	private int count;
	private int countSign;
	private int countDoc;
	private int countOther;
	private int countSignPlusInnerJar;
	private int countJDTCore;

	public void processBuildfile() throws IOException {
		Files.createDirectories(comparatorLogsDirectory);

		try (BufferedReader input = Files.newBufferedReader(buildLogsDirectory.resolve("mb220_buildSdkPatch.sh.log"));
				Writer output = Files
						.newBufferedWriter(comparatorLogsDirectory.resolve("buildtimeComparatorFull.log.txt"));
				Writer outputSign = Files
						.newBufferedWriter(comparatorLogsDirectory.resolve("buildtimeComparatorSignatureOnly.log.txt"));
				Writer outputDoc = Files
						.newBufferedWriter(comparatorLogsDirectory.resolve("buildtimeComparatorDocBundle.log.txt"));
				Writer outputOther = Files
						.newBufferedWriter(comparatorLogsDirectory.resolve("buildtimeComparatorUnanticipated.log.txt"));
				Writer outputSignWithJar = Files.newBufferedWriter(
						comparatorLogsDirectory.resolve("buildtimeComparatorSignatureOnlyWithInnerJar.log.txt"));
				Writer outputJDTCore = Files
						.newBufferedWriter(comparatorLogsDirectory.resolve("buildtimeComparatorJDTCore.log.txt"));) {

			writeHeader(output);
			writeHeader(outputSign);
			writeHeader(outputSignWithJar);
			writeHeader(outputDoc);
			writeHeader(outputOther);
			writeHeader(outputJDTCore);
			count = 0;
			countSign = 0;
			countSignPlusInnerJar = 0;
			countDoc = 0;
			countOther = 0;
			countJDTCore = 0;
			for (String inputLine = ""; inputLine != null;) {
				inputLine = input.readLine();
				if (inputLine != null) {
					final Matcher matcher = MAIN_PATTERN.matcher(inputLine);
					if (matcher.matches()) {
						String name = matcher.group(1);
						List<String> reasons = new ArrayList<>();
						List<String> infos = new ArrayList<>();
						// read and write differences, until next blank line
						do {
							inputLine = input.readLine();
							if ((inputLine != null) && (inputLine.length() > 0)) {
								reasons.add(inputLine);
							}
						} while ((inputLine != null) && (inputLine.length() > 0));
						// //output.write(EOL);
						// now, do one more, to get the "info" that says
						// what was copied, or not.
						do {
							inputLine = input.readLine();
							if ((inputLine != null) && (inputLine.length() > 0)) {
								// except leave out the first line, which is a
								// long [INFO] line repeating what we already
								// know.
								if (!inputLine.startsWith("[INFO]")) {
									infos.add(inputLine);
								}
							}
						} while ((inputLine != null) && (inputLine.length() > 0));
						// Write full log, for sanity check, if nothing else
						writeEntry(++count, output, name, reasons, infos);
						if (jdtCore(name)) {
							writeEntry(++countJDTCore, outputJDTCore, name, reasons, infos);
						} else if (docItem(name)) {
							writeEntry(++countDoc, outputDoc, name, reasons, infos);
						} else if (allLinesMatchAny(reasons, PURE_SIGNATURE_PATTERNS)) {
							writeEntry(++countSign, outputSign, name, reasons, infos);
						} else if (allLinesMatchAny(reasons, PURE_SIGNATURE_PLUS_INNER_JAR_PATTERNS)) {
							writeEntry(++countSignPlusInnerJar, outputSignWithJar, name, reasons, infos);
						} else {
							writeEntry(++countOther, outputOther, name, reasons, infos);
						}
					}
				}
			}
		}
	}

	private void writeHeader(Writer output) throws IOException {
		output.write("Comparator differences from current build" + EOL);
		output.write("\t" + buildDirectory + EOL);
		output.write("compared to reference repo at " + EOL);
		output.write("\t" + comparatorRepo + EOL + EOL);
	}

	private boolean docItem(String name) {
		return DOC_NAME_PATTERN.test(name);
	}

	private boolean jdtCore(String name) {
		return name.equals("eclipse.jdt.core/org.eclipse.jdt.core/pom.xml");
	}

	boolean allLinesMatchAny(List<String> reasons, List<Predicate<String>> patterns) {
		// if all lines match one of these critical patterns, then assume difference.
		// If even one of them does not match, assume not.
		return reasons.stream().allMatch(reason -> patterns.stream().anyMatch(p -> p.test(reason)));
	}

	private void writeEntry(int thistypeCount, Writer output, String name, List<String> reasons, List<String> infolist)
			throws IOException {
		output.write(thistypeCount + ".  " + name + EOL);
		for (final String reason : reasons) {
			output.write(reason + EOL);
		}
		for (final String info : infolist) {
			output.write(info + EOL);
		}
		output.write(EOL);
	}
}
