
/*******************************************************************************
 *  Copyright (c) 2026, 2026 Hannes Wellmann and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     Hannes Wellmann - initial API and implementation
 *******************************************************************************/

import module java.base;

public class WikiCrawler {
	private static Path OUTPUT_ROOT;

	public static void main(String[] args) throws IOException {
		OUTPUT_ROOT = Path.of(args[0]).toRealPath();
		long start = System.currentTimeMillis();
		URI startURL = URI.create("https://wiki.eclipse.org/Category:Eclipse_Project");

		writeREADME();

		Set<URI> visitedSites = new HashSet<>(Set.of(startURL));
		List<URI> erroneousPages = new ArrayList<>();
		Queue<URI> toVisit = new ArrayDeque<>(visitedSites);
		while (!toVisit.isEmpty()) {
			URI page = toVisit.remove();
			if (!mirrorPage(page, visitedSites, toVisit)) {
				erroneousPages.add(page);
			}
		}
		IO.println("Successfully mirrored pages: " + (visitedSites.size() - erroneousPages.size()));
		IO.println("Erroneous pages:\n"
				+ erroneousPages.stream().sorted().map(URI::toString).collect(Collectors.joining("\n")));
		IO.println("Overall took: " + (System.currentTimeMillis() - start) + "ms");
	}

	static final Pattern HREF_PATTERN = Pattern.compile("href\\s*=\\s*[\"'](?<url>[^\"']+)[\"']");
	static final String HREF_URL_GROUP = "url";

	static boolean mirrorPage(final URI page, Set<URI> visitedSites, Queue<URI> toVisit) {
		IO.println("Mirroring: " + page);
		try (InputStream stream = openStream(page)) {
			byte[] bytes = stream.readAllBytes();
			String stringContent = new String(bytes, StandardCharsets.UTF_8);
			if (isUnspecifiedPermanentRedirect(stringContent)) {
				System.err.println("Encountered unspecific permanent redirect");
				return false;
			}
			Path filePath = filePathOf(page, stringContent);
			stringContent = HREF_PATTERN.matcher(stringContent).replaceAll(match -> {
				String href = match.group();
				final String rawURL = match.group(HREF_URL_GROUP).trim();
				if (!IGNORED_RAW_LINKS.contains(rawURL) && !rawURL.startsWith("#")) {
					URI url;
					try {
						url = page.resolve(new URI(rawURL));
						if (url.getScheme().equals("http")) { // redirect to https immediately
							url = new URI("https:" + url.toString().substring(5));
						}
					} catch (URISyntaxException e) {
						return Matcher.quoteReplacement(href); // malformed reference, ignore
					}
					if (isMirroredURL(url)) {
						final String path = url.getRawPath();
						if (rawURL.contains(":") && path.contains(":")) {
							for (String pathSegment : path.split("/")) {
								if (pathSegment.contains(":")) {
									if (substringOccurances(pathSegment, href) != 1) {
										throw new IllegalStateException("Path segment <" + pathSegment
												+ "> occures not exactly once in " + rawURL);
									}
									String pathSegmentReplacement = pathSegment.replace(":", COLON_PATH_REPLACEMENT);
									href = href.replace(pathSegment, pathSegmentReplacement);
								}
							}
						}
						href = escapePath("%3F", QUESTION_MARK_PATH_REPLACEMENT, href); // replace ?
						href = escapePath("%22;", DOUBLE_QUOTES_PATH_REPLACEMENT, href); // replace "

						URI trimmedURL = removeFragment(url);
						if (visitedSites.add(trimmedURL)) {
							toVisit.add(trimmedURL);
						}
					}
				}
				return Matcher.quoteReplacement(href);
			});
			Files.createDirectories(filePath.getParent());
			if (filePath.getFileName().toString().endsWith(".html")) {
				Files.writeString(filePath, stringContent);
			} else {
				Files.write(filePath, bytes);
			}
			Thread.sleep(200);
			return true; // Completed successfully
		} catch (HttpException e) { // Ignore
			System.err.println("Failed: " + e.getMessage());
			return true; // Not an actionable error
		} catch (Exception e) {
			System.err.println("Exception at " + page);
			e.printStackTrace();
		}
		return false; // Failed
	}

	static class HttpException extends IOException {
		private static final long serialVersionUID = 1L;

		public HttpException(String message) {
			super(message);
		}
	}

	static InputStream openStream(URI page) throws IOException, URISyntaxException, MalformedURLException {
		URLConnection connection = page.toURL().openConnection();
		if (connection instanceof HttpURLConnection httpConnection) {
			httpConnection.setInstanceFollowRedirects(true);
			int status = httpConnection.getResponseCode();
			switch (status) {
			case HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP -> {
				URI redirectionTarget = URI.create(connection.getHeaderField("Location"));
				httpConnection.disconnect();
				return openStream(redirectionTarget);
			}
			case HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_NOT_FOUND ->
			throw new HttpException("Server returned HTTP response code: " + status);
			default -> { // Continue
			}
			}
		}
		return connection.getInputStream();
	}

	static String escapePath(String htmlEncodedSymbol, String replacement, String href) {
		if (href.contains(htmlEncodedSymbol) || href.contains(htmlEncodedSymbol.toLowerCase(Locale.ROOT))) {
			href = href.replaceAll("(?i)" + htmlEncodedSymbol, replacement);
		}
		return href;
	}

	private static int substringOccurances(String substring, String string) {
		int index = 0;
		int occurances = 0;
		while ((index = string.indexOf(substring, index) + 1) > 0) {
			occurances++;
		}
		return occurances;
	}

	private static final List<String> UNSPECIFIED_REDIRECT = """
			<html>
			<head><title>301 Moved Permanently</title></head>
			<body>
			<center><h1>301 Moved Permanently</h1></center>
			""".lines().toList();

	private static boolean isUnspecifiedPermanentRedirect(String stringContent) {
		Iterator<String> lines = stringContent.lines().iterator();
		for (String redirectLine : UNSPECIFIED_REDIRECT) {
			if ((!lines.hasNext() || !redirectLine.equals(lines.next()))) {
				return false;
			}
		}
		return true;
	}

	static final Set<String> IGNORED_RAW_LINKS = Set.of("#content", "#", "#mw-head", "#p-search", "#top");
	static final Set<String> IGNORED_WIKI_PATHS = Set.of("/index.php", "/api.php", "/load.php", "/opensearch_desc.php",
			"/login", "/signup");
	static final List<String> IGNORED_WIKI_PATH_PREFIXES = List.of("/special:", "/talk:", "/file:", "/user:",
			"/template_", "/index.php/", //
			"/jetty/", "/jetty_", "/memoryanalyzer/", "/cosmos", "/ecf_", "/wtp_", "/swordfish_", "/dtp_", "/edt_",
			"/tigerstripe", "/eclipselink/", "/birt/", "/mdt/", "/ocl/", "/papyrus/", "/passage/", "/pdt/", "/rap/",
			"/triquetrum/", "/polarsys/", "/acceleo/", "/atl/", "/cdo/", "/smila/", "/dsdp/", "/modisco/", "/sca/",
			"/stp/", "/babel_/", "/ptp/", "/tm/", "/mdt-", "/virgo", "/edt:", "/openpass");

	static boolean isMirroredURL(URI url) {
		if (url.getScheme() == null || !url.getScheme().startsWith("http")) {
			return false;
		}
		if (!"wiki.eclipse.org".equals(url.getHost())) {
			return false;
		}
		String path = url.getPath().toLowerCase(Locale.ENGLISH);
		if (IGNORED_WIKI_PATHS.contains(path) || IGNORED_WIKI_PATH_PREFIXES.stream().anyMatch(path::startsWith)) {
			return false;
		}
		return true;
	}

	static URI removeFragment(URI uri) {
		if (uri.getFragment() == null) {
			return uri;
		}
		try {
			return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(),
					uri.getQuery(), null);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Failed to remove fragment from: " + uri);
		}
	}

	static final String COLON_PATH_REPLACEMENT = "--";
	static final String QUESTION_MARK_PATH_REPLACEMENT = "-qm-";
	static final String DOUBLE_QUOTES_PATH_REPLACEMENT = "-dq-";

	static Path filePathOf(URI page, String stringContent) {
		String path = page.getPath();
		if (path.endsWith("/")) { // Assume it's a directory name
			path += "index.html";
		} else {
			String lastSegment = path.substring(path.lastIndexOf('/') + 1);
			if (!isFilename(lastSegment, stringContent)) {
				// Assume it's a directory name
				path += "/index.html";
			}
		}
		path = path.startsWith("/") ? path.substring(1) : path; // Remove leading slash
		path = path.replace(":", COLON_PATH_REPLACEMENT); // On Windows colons in paths are not (well) supported
		path = path.replace("?", QUESTION_MARK_PATH_REPLACEMENT); // On Windows question-marks are not supported
		path = path.replace("\"", DOUBLE_QUOTES_PATH_REPLACEMENT); // On Windows double-quotes are not supported
		return OUTPUT_ROOT.resolve(page.getHost(), path);
	}

	static boolean isFilename(String filename, String stringContent) {
		int lastDot = filename.lastIndexOf('.');
		boolean hasLetterOnlyExtension = lastDot > 0
				&& filename.substring(lastDot + 1).chars().allMatch(Character::isLetter);
		if (hasLetterOnlyExtension) {
			if (!filename.endsWith(".html") && !filename.endsWith(".xhtml") && isHTMLContent(stringContent)) {
				return false; // filename is of a directory, append a index.html segment
			}
		}
		return hasLetterOnlyExtension;
	}

	static boolean isHTMLContent(String stringContent) {
		return stringContent.startsWith("<html") || stringContent.startsWith("<!DOCTYPE html");
	}

	static boolean isIgnored(String url) {
		boolean isIgnored = IGNORED_RAW_LINKS.contains(url) //
				|| url.startsWith("/index.php?title=") || url.startsWith("https://wiki.eclipse.org/index.php?title=")
				|| url.startsWith("/load.php?") || url.startsWith("/api.php?") || url.startsWith("/File:")
				|| url.startsWith("/Special:") //
				|| url.contains("title=Special:") || url.contains("action=edit") || url.contains("action=history") //
				|| url.endsWith(".png") || url.endsWith(".gif") || url.endsWith(".jpeg") || url.endsWith(".jpg");
		if (url.contains("/index.php?title=")) {
			IO.println("Still found index.php");
		}
		return isIgnored;
	}

	static void writeREADME() throws IOException {
		Files.writeString(OUTPUT_ROOT.resolve("README.md"),
		"""
		# Eclipse Wiki archive

		The colocated zip file contains the archived Eclipse Wiki pages related to the Eclipse project.

		To view it, download the archive, extract it and run a (local) webserver from the `wiki.eclipse.org` directory and access it through a browser.
		For example by running `jwebserver` (part of a JDK):
		```
		cd wiki.eclipse.org
		jwebserver
		```
		By default this will server the wiki archive at `localhost:8000`, but the port is configurable.
		Run `jwebserver --help` for more details.

		## Notes on Windows

		Some wiki pages have addresses that are distinguished only by different letter cases.
		Therefore when extracting the archive at a Windows filesystem, these pages are replaced by each other and the first ones will be missing.
		In some cases pages with only different letter cases have the same content and are effectively aliases, but in other cases they are really different.
		If such page is of interest, extract it separately from the archive to a separate directory.
		""");
	}

}
