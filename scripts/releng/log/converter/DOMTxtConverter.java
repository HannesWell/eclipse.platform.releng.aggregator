/*******************************************************************************
 * Copyright (c) 2006, 2017 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 *******************************************************************************/

package log.converter;

import java.io.IOException;
import java.io.Writer;

public class DOMTxtConverter extends AbstractDOMConverter {

	public DOMTxtConverter() {
		messages = new Messages("log.converter.txt_messages"); //$NON-NLS-1$
	}

	@Override
	public String getUnderLine(final String sourceBefore, final String sourceOfError) {
		final StringBuilder buffer = new StringBuilder();
		char[] chars = sourceBefore.toCharArray();
		for (char element : chars) {
			switch (element) {
			case '\t':
				buffer.append('\t');
				break;
			default:
				buffer.append(' ');
			}
		}
		chars = sourceOfError.toCharArray();
		for (char element : chars) {
			buffer.append('^');
		}
		return String.valueOf(buffer);
	}

	@Override
	public void writeAnchorsReferences(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeAnchorsReferencesDiscouragedRulesWarnings(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeAnchorsReferencesErrors(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeAnchorsReferencesForbiddenRulesWarnings(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeAnchorsReferencesOtherWarnings(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeDiscouragedRulesWarningsAnchor(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeErrorAnchor(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeForbiddenRulesWarningsAnchor(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeOtherWarningsAnchor(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeTopAnchor(final Writer writer) throws IOException {
		// do nothing
	}

	@Override
	public void writeAnchorsReferencesInfos(Writer writer) throws IOException {
		// do nothing

	}

	@Override
	public void writeInfosAnchor(Writer writer) throws IOException {
		// do nothing

	}
}
