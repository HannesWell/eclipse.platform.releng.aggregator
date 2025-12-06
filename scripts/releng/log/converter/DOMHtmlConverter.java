/*******************************************************************************
 * Copyright (c) 2006, 2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package log.converter;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import log.converter.LogDocumentNode.ProblemSummaryNode;

public class DOMHtmlConverter {

	public static final String FORBIDDEN_REFERENCE = "ForbiddenReference"; //$NON-NLS-1$
	public static final String DISCOURAGED_REFERENCE = "DiscouragedReference"; //$NON-NLS-1$
	public static final Set<String> FILTERED_WARNINGS_IDS = Set.of(FORBIDDEN_REFERENCE, DISCOURAGED_REFERENCE);

	private final ResourceBundle messages = ResourceBundle.getBundle("log.converter.html_messages"); //$NON-NLS-1$

	private void writeAnchorsReferences(final Writer writer) throws IOException {
		writer.write(messages.getString("anchors.references.no_top"));//$NON-NLS-1$
	}

	private void writeAnchorsReferencesDiscouragedRulesWarnings(final Writer writer) throws IOException {
		writer.write(messages.getString("anchors.references.no_discouraged_warnings"));//$NON-NLS-1$
	}

	private void writeAnchorsReferencesErrors(final Writer writer) throws IOException {
		writer.write(messages.getString("anchors.references.no_errors"));//$NON-NLS-1$
	}

	private void writeAnchorsReferencesForbiddenRulesWarnings(final Writer writer) throws IOException {
		writer.write(messages.getString("anchors.references.no_forbidden_warnings"));//$NON-NLS-1$
	}

	private void writeAnchorsReferencesOtherWarnings(final Writer writer) throws IOException {
		writer.write(messages.getString("anchors.references.no_other_warnings"));//$NON-NLS-1$
	}

	private void writeAnchorsReferencesInfos(final Writer writer) throws IOException {
		writer.write(messages.getString("anchors.references.no_infos"));//$NON-NLS-1$
	}

	private void writeDiscouragedRulesWarningsAnchor(final Writer writer) throws IOException {
		writer.write(messages.getString("discouraged_warnings.title_anchor"));//$NON-NLS-1$
	}

	private void writeErrorAnchor(final Writer writer) throws IOException {
		writer.write(messages.getString("errors.title_anchor"));//$NON-NLS-1$
	}

	private void writeForbiddenRulesWarningsAnchor(final Writer writer) throws IOException {
		writer.write(messages.getString("forbidden_warnings.title_anchor"));//$NON-NLS-1$
	}

	private void writeOtherWarningsAnchor(final Writer writer) throws IOException {
		writer.write(messages.getString("other_warnings.title_anchor"));//$NON-NLS-1$
	}

	private void writeInfosAnchor(final Writer writer) throws IOException {
		writer.write(messages.getString("infos.title_anchor"));//$NON-NLS-1$
	}

	private void writeTopAnchor(final Writer writer) throws IOException {
		writer.write(messages.getString("problem.summary.title_anchor"));//$NON-NLS-1$
	}

	private String convertToHTML(final String s) {
		final StringBuilder buffer = new StringBuilder();
		for (int i = 0, max = s.length(); i < max; i++) {
			final char c = s.charAt(i);
			switch (c) {
			case '<' -> buffer.append("&lt;"); //$NON-NLS-1$
			case '>' -> buffer.append("&gt;"); //$NON-NLS-1$
			case '\"' -> buffer.append("&quot;"); //$NON-NLS-1$
			case '&' -> buffer.append("&amp;"); //$NON-NLS-1$
			case '^' -> buffer.append("&and;"); //$NON-NLS-1$
			default -> buffer.append(c);
			}
		}
		return String.valueOf(buffer);
	}

	public void dump(Path inputFilename, Path outputFileName, LogDocumentNode documentNode) {
		final ProblemSummaryNode summaryNode = documentNode.getSummaryNode();
		if ((summaryNode == null) || (summaryNode.numberOfProblems() == 0)) {
			return;
		}
		try (final Writer writer = Files.newBufferedWriter(outputFileName)) {
			final String pluginName = outputFileName.getParent().getFileName().toString();
			if (pluginName == null) {
				writer.write(messages.getString("header")); //$NON-NLS-1$
			} else {
				final String pattern = messages.getString("dom_header"); //$NON-NLS-1$
				writer.write(MessageFormat.format(pattern, pluginName, inputFilename.getFileName().toString()));
			}
			final ProblemSummaryNode problemSummaryNode = summaryNode;
			writeTopAnchor(writer);
			String pattern = messages.getString("problem.summary"); //$NON-NLS-1$
			writer.write(MessageFormat.format(pattern, Integer.toString(problemSummaryNode.numberOfProblems()),
					Integer.toString(problemSummaryNode.numberOfErrors()),
					Integer.toString(problemSummaryNode.numberOfWarnings()),
					Integer.toString(problemSummaryNode.numberOfInfos())));

			writeAnchorsReferences(writer);
			List<ProblemsNode> problemsNodes = documentNode.getProblems();
			int globalErrorNumber = 1;

			writeErrorAnchor(writer);
			writeAnchorsReferencesErrors(writer);
			// dump errors
			for (final ProblemsNode problemsNode : problemsNodes) {
				List<ProblemNode> problemNodes = problemsNode.getErrors();
				if (problemNodes.isEmpty()) {
					continue;
				}
				pattern = messages.getString("errors.header"); //$NON-NLS-1$

				final MessageFormat form = new MessageFormat(pattern);
				final double[] warningsLimits = { 1, 2 };
				final String[] warningParts = { messages.getString("one_error"), //$NON-NLS-1$
						messages.getString("multiple_errors") //$NON-NLS-1$
				};
				final ChoiceFormat warningForm = new ChoiceFormat(warningsLimits, warningParts);
				final String sourceFileName = extractRelativePath(problemsNode.sourceFileName, pluginName);
				form.setFormatByArgumentIndex(1, warningForm);
				final Object[] arguments = new Object[] { sourceFileName, problemsNode.numberOfErrors };
				writer.write(form.format(arguments));
				for (int j = 0; j < problemNodes.size(); j++) {
					final ProblemNode problemNode = problemNodes.get(j);
					if ((j & 1) != 0) {
						pattern = messages.getString("errors.entry.odd"); //$NON-NLS-1$
					} else {
						pattern = messages.getString("errors.entry.even"); //$NON-NLS-1$
					}
					problemNode.setSources();
					writer.write(MessageFormat.format(pattern, sourceFileName, Integer.toString(globalErrorNumber),
							Integer.toString(j + 1), problemNode.id, Integer.toString(problemNode.line),
							convertToHTML(problemNode.message), convertToHTML(problemNode.sourceCodeBefore),
							convertToHTML(problemNode.sourceCode), convertToHTML(problemNode.sourceCodeAfter), "",
							Integer.toString(problemNode.charStart), Integer.toString(problemNode.charEnd)));
					globalErrorNumber++;
				}
				writer.write(messages.getString("errors.footer")); //$NON-NLS-1$
			}

			writeOtherWarningsAnchor(writer);
			writeAnchorsReferencesOtherWarnings(writer);
			// dump other warnings
			for (final ProblemsNode problemsNode : problemsNodes) {
				List<ProblemNode> problemNodes = problemsNode.getOtherWarnings();
				if (problemNodes.isEmpty()) {
					continue;
				}

				pattern = messages.getString("other_warnings.header"); //$NON-NLS-1$
				final MessageFormat form = new MessageFormat(pattern);
				final double[] warningsLimits = { 1, 2 };
				final String[] warningParts = { messages.getString("one_warning"), //$NON-NLS-1$
						messages.getString("multiple_warnings") //$NON-NLS-1$
				};
				final ChoiceFormat warningForm = new ChoiceFormat(warningsLimits, warningParts);
				final String sourceFileName = extractRelativePath(problemsNode.sourceFileName, pluginName);
				form.setFormatByArgumentIndex(1, warningForm);
				final Object[] arguments = new Object[] { sourceFileName, problemsNode.numberOfWarnings };
				writer.write(form.format(arguments));
				for (int j = 0; j < problemNodes.size(); j++) {
					final ProblemNode problemNode = problemNodes.get(j);
					if ((j & 1) != 0) {
						pattern = messages.getString("warnings.entry.odd"); //$NON-NLS-1$
					} else {
						pattern = messages.getString("warnings.entry.even"); //$NON-NLS-1$
					}
					problemNode.setSources();
					writer.write(MessageFormat.format(pattern, sourceFileName, Integer.toString(globalErrorNumber),
							Integer.toString(j + 1), problemNode.id, Integer.toString(problemNode.line),
							convertToHTML(problemNode.message), convertToHTML(problemNode.sourceCodeBefore),
							convertToHTML(problemNode.sourceCode), convertToHTML(problemNode.sourceCodeAfter), "",
							Integer.toString(problemNode.charStart), Integer.toString(problemNode.charEnd)));
					globalErrorNumber++;
				}
				writer.write(messages.getString("other_warnings.footer")); //$NON-NLS-1$
			}

			// dump infos
			writeInfosAnchor(writer);
			writeAnchorsReferencesInfos(writer);
			for (final ProblemsNode problemsNode : problemsNodes) {
				List<ProblemNode> problemNodes = problemsNode.getInfos();
				if (problemNodes.isEmpty()) {
					continue;
				}

				pattern = messages.getString("infos.header"); //$NON-NLS-1$
				final MessageFormat form = new MessageFormat(pattern);
				final double[] warningsLimits = { 1, 2 };
				final String[] warningParts = { messages.getString("one_info"), //$NON-NLS-1$
						messages.getString("multiple_infos") //$NON-NLS-1$
				};
				final ChoiceFormat warningForm = new ChoiceFormat(warningsLimits, warningParts);
				final String sourceFileName = extractRelativePath(problemsNode.sourceFileName, pluginName);
				form.setFormatByArgumentIndex(1, warningForm);
				final Object[] arguments = new Object[] { sourceFileName, problemsNode.numberOfInfos };
				writer.write(form.format(arguments));
				for (int j = 0; j < problemNodes.size(); j++) {
					final ProblemNode problemNode = problemNodes.get(j);
					if ((j & 1) != 0) {
						pattern = messages.getString("infos.entry.odd"); //$NON-NLS-1$
					} else {
						pattern = messages.getString("infos.entry.even"); //$NON-NLS-1$
					}
					problemNode.setSources();
					writer.write(MessageFormat.format(pattern, sourceFileName, Integer.toString(globalErrorNumber),
							Integer.toString(j + 1), problemNode.id, Integer.toString(problemNode.line),
							convertToHTML(problemNode.message), convertToHTML(problemNode.sourceCodeBefore),
							convertToHTML(problemNode.sourceCode), convertToHTML(problemNode.sourceCodeAfter), "",
							Integer.toString(problemNode.charStart), Integer.toString(problemNode.charEnd)));
					globalErrorNumber++;
				}
				writer.write(messages.getString("infos.footer")); //$NON-NLS-1$
			}

			// dump forbidden accesses warnings
			writeForbiddenRulesWarningsAnchor(writer);
			writeAnchorsReferencesForbiddenRulesWarnings(writer);
			for (final ProblemsNode problemsNode : problemsNodes) {
				List<ProblemNode> problemNodes = problemsNode.getForbiddenWarnings();
				if (problemNodes.isEmpty()) {
					continue;
				}

				pattern = messages.getString("forbidden_warnings.header"); //$NON-NLS-1$
				final MessageFormat form = new MessageFormat(pattern);
				final double[] warningsLimits = { 1, 2 };
				final String[] warningParts = { messages.getString("one_warning"), //$NON-NLS-1$
						messages.getString("multiple_warnings") //$NON-NLS-1$
				};
				final ChoiceFormat warningForm = new ChoiceFormat(warningsLimits, warningParts);
				final String sourceFileName = extractRelativePath(problemsNode.sourceFileName, pluginName);
				form.setFormatByArgumentIndex(1, warningForm);
				final Object[] arguments = new Object[] { sourceFileName, problemsNode.numberOfWarnings };
				writer.write(form.format(arguments));
				for (int j = 0; j < problemNodes.size(); j++) {
					final ProblemNode problemNode = problemNodes.get(j);
					if ((j & 1) != 0) {
						pattern = messages.getString("warnings.entry.odd"); //$NON-NLS-1$
					} else {
						pattern = messages.getString("warnings.entry.even"); //$NON-NLS-1$
					}
					problemNode.setSources();
					writer.write(MessageFormat.format(pattern, sourceFileName, Integer.toString(globalErrorNumber),
							Integer.toString(j + 1), problemNode.id, Integer.toString(problemNode.line),
							convertToHTML(problemNode.message), convertToHTML(problemNode.sourceCodeBefore),
							convertToHTML(problemNode.sourceCode), convertToHTML(problemNode.sourceCodeAfter), "",
							Integer.toString(problemNode.charStart), Integer.toString(problemNode.charEnd)));
					globalErrorNumber++;
				}
				writer.write(messages.getString("forbidden_warnings.footer")); //$NON-NLS-1$
			}

			// dump discouraged accesses warnings
			writeDiscouragedRulesWarningsAnchor(writer);
			writeAnchorsReferencesDiscouragedRulesWarnings(writer);
			for (final ProblemsNode problemsNode : problemsNodes) {
				List<ProblemNode> problemNodes = problemsNode.getDiscouragedWarnings();
				if (problemNodes.isEmpty()) {
					continue;
				}

				pattern = messages.getString("discouraged_warnings.header"); //$NON-NLS-1$
				final MessageFormat form = new MessageFormat(pattern);
				final double[] warningsLimits = { 1, 2 };
				final String[] warningParts = { messages.getString("one_warning"), //$NON-NLS-1$
						messages.getString("multiple_warnings") //$NON-NLS-1$
				};
				final ChoiceFormat warningForm = new ChoiceFormat(warningsLimits, warningParts);
				final String sourceFileName = extractRelativePath(problemsNode.sourceFileName, pluginName);
				form.setFormatByArgumentIndex(1, warningForm);
				final Object[] arguments = new Object[] { sourceFileName, problemsNode.numberOfWarnings };
				writer.write(form.format(arguments));
				for (int j = 0; j < problemNodes.size(); j++) {
					final ProblemNode problemNode = problemNodes.get(j);
					if ((j & 1) != 0) {
						pattern = messages.getString("warnings.entry.odd"); //$NON-NLS-1$
					} else {
						pattern = messages.getString("warnings.entry.even"); //$NON-NLS-1$
					}
					problemNode.setSources();
					writer.write(MessageFormat.format(pattern, sourceFileName, Integer.toString(globalErrorNumber),
							Integer.toString(j + 1), problemNode.id, Integer.toString(problemNode.line),
							convertToHTML(problemNode.message), convertToHTML(problemNode.sourceCodeBefore),
							convertToHTML(problemNode.sourceCode), convertToHTML(problemNode.sourceCodeAfter), "",
							Integer.toString(problemNode.charStart), Integer.toString(problemNode.charEnd)));
					globalErrorNumber++;
				}
				writer.write(messages.getString("discouraged_warnings.footer")); //$NON-NLS-1$
			}

			writer.write(messages.getString("footer")); //$NON-NLS-1$
			writer.flush();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}

	private String extractRelativePath(final String sourceFileName, final String pluginName) {
		if (pluginName == null) {
			return sourceFileName;
		}
		final int index = pluginName.indexOf('_');
		if (index == -1) {
			return sourceFileName;
		}
		final String pluginShortName = pluginName.substring(0, index);
		final int index2 = sourceFileName.indexOf(pluginShortName);
		if (index2 == -1) {
			return sourceFileName;
		}
		return sourceFileName.substring(index2 + pluginShortName.length(), sourceFileName.length());
	}

}
