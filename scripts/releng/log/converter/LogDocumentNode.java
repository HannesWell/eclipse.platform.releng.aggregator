/*******************************************************************************
 * Copyright (c) 2006, 2016 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 *******************************************************************************/

package log.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LogDocumentNode {

	public record ProblemSummaryNode(int numberOfProblems, int numberOfErrors, int numberOfWarnings,
			int numberOfInfos) {

		@Override
		public String toString() {
			final StringBuilder buffer = new StringBuilder();
			buffer.append("problems : ") //$NON-NLS-1$
			.append(numberOfProblems).append(" errors : ") //$NON-NLS-1$
			.append(numberOfErrors).append(" warnings : ") //$NON-NLS-1$
			.append(numberOfWarnings).append(" infos : ") // $NON_NLS-1$
			.append(numberOfInfos);
			return buffer.toString();
		}
	}

	private final List<ProblemsNode> problems = new ArrayList<>();
	private ProblemSummaryNode summaryNode;

	public void addProblemsNode(final ProblemsNode node) {
		problems.add(node);
	}

	public List<ProblemsNode> getProblems() {
		return Collections.unmodifiableList(problems);
	}

	public ProblemSummaryNode getSummaryNode() {
		return summaryNode;
	}

	public void setProblemSummary(final ProblemSummaryNode node) {
		summaryNode = node;
	}
}
