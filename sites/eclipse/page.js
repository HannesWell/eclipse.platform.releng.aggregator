
const scriptBase = new URL(".", document.currentScript.src).href
const markdownBase = `${scriptBase}markdown/?file=`;

function getPageName() {
	//TODO: maybe find a nicer way? At least resolve camle case?
	let pathElements = window.location.pathname.split('/')
	const lastElement = pathElements.at(-1)
	if (lastElement == 'index.html' || lastElement == '') {
		return pathElements.at(-2)
	}
	return lastElement
}

let meta = toElements(`
<meta charset="utf-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="shortcut icon" href="https://eclipseide.org/favicon.ico"/>
`);

let defaultHeader = toElements(`
	<a href="https://www.eclipse.org/downloads/packages/">Eclipse IDE</a>
	<a href="https://eclipseide.org/working-group/">Working Group</a>
	<a href="https://eclipseide.org/release/noteworthy/">New &amp; Noteworthy</a>
	<a href="https://marketplace.eclipse.org/">Marketplace</a>
`);

let defaultBreadcrumb = toElements(`
	<a href="https://eclipse.org/">Home</a>
	<a href="https://www.eclipse.org/projects/">Projects</a>
	<a href="https://eclipse.dev/eclipse/">Eclipse</a>
`);

//TODO: specify the TOC as Aside!
// Make the name of the current page the head-line (like "I12345678-1234" or "Test results")
const projectAside = `
<a class="separator" href="https://projects.eclipse.org/projects/eclipse"><i class='fa fa-cube'></i> Eclipse Project</a>
<a href="https://projects.eclipse.org/projects/eclipse.equinox">Equinox</a>
<a href="https://projects.eclipse.org/projects/eclipse.platform">Platform</a>
<a href="${scriptBase}swt">&nbsp;&nbsp;&bullet;&nbsp;SWT</a>
<a href="https://projects.eclipse.org/projects/eclipse.jdt">Java Development Tools</a>
<a href="https://projects.eclipse.org/projects/eclipse.pde">Plug-in Development Environment</a>
`;

const defaultAside = toElements(`
${projectAside}
`);

const tocAside = toElements(`
<a class="separator" href="https://projects.eclipse.org/projects/eclipse"><i class='fa fa-cube'></i>${getPageName()}</a>
<a href="https://projects.eclipse.org/projects/eclipse.pde">Plug-in Development Environment</a>
`);

// start resource fetch early TODO: Check if this works as desired
// TODO: try to make the md file name modifyable (and nullable). But this would make it hard to fetch early.
// Or the early fetch would have to be started in the generate method
const markdownContentFetched = fetch('content.md')

let contentDataFetched = null

function loadContentData(buildDataPath) {
	contentDataFetched = fetch(buildDataPath).then(response => {
		if (!response.ok) {
			//TODO: might this be called too early?
			logException(response.statusText + ': ' + buildDataPath, response.statusText)
			throw new Error() //TODO: check this
		}
		return response.text()
	}).then(txt => JSON.parse(txt))
}

let markdownPostProcessor = (_markdownElement, _contentData) => { }

function generate() {
	// selfContent = document.documentElement.innerHTML;	//TODO: remove?
	try {
		const head = document.head;
		var referenceNode = head.querySelector('script');
		for (const element of [...meta]) {
			head.insertBefore(element, referenceNode.nextElementSibling)
			referenceNode = element;
		}

		const generators = document.querySelectorAll('[data-generate]');
		for (const element of generators) {
			const generator = element.getAttribute('data-generate');
			const generate = new Function(generator);
			generate.call(element, element);
		}

		const generatedBody = generateBody();
		document.body.replaceChildren(...generatedBody);

		const markdownElement = document.getElementById('markdown-target');
		if (markdownElement) {
			markdownContentFetched.then(response => {
				if (!response.ok) {
					const statusText = response.statusText
					markdownElement.innerHTML = `<span><b>Failed to fetch markdown content: </b><span><b style="color: FireBrick">${statusText}</b><br/>`
				} else {
					response.text().then(text => {
						//TODO: Move some parts to separate method?
						marked.use(markedGfmHeadingId.gfmHeadingId());
						marked.use({
							hooks: {
								postprocess(html) { return `${html}`; }
							}
						});
						markdownElement.innerHTML = marked.parse(text);
						// Populate TOC
						//TODO: check all other references to 'toc' in markdown/index.html
						//TODO: Or do it like in generateAside() ?
						const headings = markedGfmHeadingId.getHeadingList();
						if (headings) {
							const headingText = `
							<ul id="table-of-contents">
							${headings.map(({ id, raw, level }) => `<li class="tl${level}"><a href="#${id}">${raw}</a></li>`).join(' ')}
							</ul>
							`;
							document.getElementById('toc-target').replaceChildren(...toElements(headingText));
						} else {
							document.getElementById('toc-container').remove()
						}
						if (contentDataFetched) {
							contentDataFetched.then(contentData => {
								//TODO: consider entire document, includead bread-crumb etc
								resolveDataReferences(markdownElement, contentData)
								markdownPostProcessor(markdownElement, contentData)
							})
						}
					}).catch(error => {
						markdownElement.innerHTML = `<span>Failed to parse markdown content: <span><b style="color: FireBrick">${error.message}</b><br/>`
					})
				}
			})
		}
	} catch (exception) {
		logException(exception.message, exception)
	}
}

//TODO: remove this?
function resolveDataTables(rootElement, data) {
	const dataTables = rootElement.getElementsByClassName("data-table")
	for (const table of dataTables) {
		if (table.tBodies.length != 1) {
			throw new Error("Data table with more than one body")
		}
		let tbody = table.tBodies[0]
		if (tbody.rows.length != 1) {
			throw new Error("Data table with more than one row")
		}
		const templateRow = tbody.rows[0]
		const dataPath = table.getAttribute('data-path');
		const tableData = getValue(data, dataPath)
		for (const dataRow of tableData) {
			const row = templateRow.cloneNode(true)
			resolveDataReferences(row, dataRow)
			tbody.appendChild(row)
		}
		tbody.deleteRow(0) // Remove the template row
	}
}

const dataReferencePattern = /\${(?<path>[\w-]+)}/g

function resolveDataReferences(contextElement, contextData) {
	const dataElements = Array.from(contextElement.getElementsByClassName("data-ref"))
	for (const element of dataElements) {
		element.classList.remove("data-ref") // Prevent multiple processing in subsequent passes with different context (therefore a copy is created from the list)
		//TODO: or outer html to also handle attributes?
		element.outerHTML = element.outerHTML.replaceAll(dataReferencePattern, (_match, pathGroup, _offset, _string) => {
			return getValue(contextData, pathGroup)
		})
	}
}

function getValue(data, path) {
	let value = data
	for (const key of path.split('.')) {
		if (!value.hasOwnProperty(key)) {
			throw new Error(`Key '${key}' not found in ${JSON.stringify(value)}`)
		}
		value = value[key]
	}
	return value;
}

function logException(message, loggedObject) {
	document.body.prepend(...toElements(`<span>Failed to generate content: <span><b style="color: FireBrick">${message}</b><br/>`));
	console.log(loggedObject);
}

function generateBody() {
	const col = document.getElementById('aside') ? 'col-md-18' : ' col-md-24';
	//TODO: generate the toc content instead of just calling 'generateAside' below
	// Actually just instead a <div id="toc-target"></div>
	//TODO: check if something is constant and can always be inlined
	return toElements(`
<div>
	${generateHeader()}
	<main id="content">
		<div class="novaContent container" id="novaContent">
			<div class="row">
				<div class="${col} main-col-content">
					<div class="novaContent" id="novaContent">
						<div class="row">
							${generateBreadcrumb()}
						</div>
						<div class=" main-col-content">
							<div id="midcolumn">
							${generateMainContent()}
							</div>
						</div>
					</div>
				</div>
				<div id="toc-container" class="col-md-6">
					<aside>
						<ul class="ul-left-nav">
							<div class="sideitem">
								<h2>Table of Contents</h2>
								<div id="toc-target">
								</div>
							</div>
						</ul>
					</aside>
				</div>
			</div>
		</div>
	</main>
	<footer id="footer">
		<div class="container">
			<div class="footer-sections row equal-height-md font-bold">
				<div id="footer-eclipse-foundation" class="footer-section col-md-5 col-sm-8">
					<div class="menu-heading">Eclipse Foundation</div>
					<ul class="nav">
						<ul class="nav">
							<li><a href="http://www.eclipse.org/org/">About</a></li>
							<li><a href="https://projects.eclipse.org/">Projects</a></li>
							<li><a href="http://www.eclipse.org/collaborations/">Collaborations</a></li>
							<li><a href="http://www.eclipse.org/membership/">Membership</a></li>
							<li><a href="http://www.eclipse.org/sponsor/">Sponsor</a></li>
						</ul>
					</ul>
				</div>
				<div id="footer-legal" class="footer-section col-md-5 col-sm-8">
					<div class="menu-heading">Legal</div>
					<ul class="nav">
						<ul class="nav">
							<li><a href="http://www.eclipse.org/legal/privacy.php">Privacy Policy</a></li>
							<li><a href="http://www.eclipse.org/legal/termsofuse.php">Terms of Use</a></li>
							<li><a href="http://www.eclipse.org/legal/compliance/">Compliance</a></li>
							<li><a href="http://www.eclipse.org/org/documents/Community_Code_of_Conduct.php">Code of
									Conduct</a></li>
							<li><a href="http://www.eclipse.org/legal/">Legal Resources</a></li>
						</ul>
					</ul>
				</div>
				<div id="footer-more" class="footer-section col-md-5 col-sm-8">
					<div class="menu-heading">More</div>
					<ul class="nav">
						<ul class="nav">
							<li><a href="http://www.eclipse.org/security/">Report a Vulnerability</a></li>
							<li><a href="https://www.eclipsestatus.io/">Service Status</a></li>
							<li><a href="http://www.eclipse.org/org/foundation/contact.php">Contact</a></li>
							<li><a href="http://www.eclipse.org//projects/support/">Support</a></li>
						</ul>
					</ul>
				</div>
			</div>
			<div class="col-sm-24">
				<div class="row">
					<div id="copyright" class="col-md-16">
						<p id="copyright-text">Copyright © Eclipse Foundation AISBL. All Rights Reserved.</p>
					</div>
				</div>
			</div>
			<a href="#" class="scrollup" onclick="scrollToTop()">Back to the top</a>
		</div>
	</footer>
</div>
`);
}

function generateMainContent() {
	const main = document.body.querySelector('main')
	if (main != null) {
		//TODO: return innerHTML instead?
		const inner = main.outerHTML
		return main.outerHTML
	}
	return "<main>The body specifies no content.</main>";
}

function generateDefaultHeader(element) {
	return prependChildren(element, 'header', ...defaultHeader);
}

function generateHeader() {
	const elements = document.querySelectorAll('#header>a');
	const items = Array.from(elements).map(link => {
		link.classList.add('link-unstyled');
		return `
<li class="navbar-nav-links-item">
	${link.outerHTML}
</li>
`;
	});
	const mobileItems = Array.from(elements).map(link => {
		link.className = 'mobile-menu-item mobile-menu-dropdown-toggle';
		return `
<li class="mobile-menu-dropdown">
	${link.outerHTML}
</li>
`;
	});

	return `
<header class="header-wrapper" id="header">
	<div class="header-navbar-wrapper">
		<div class="container">
			<div class="header-navbar">
				<a class="header-navbar-brand" href="https://eclipseide.org/">
					<div class="logo-wrapper">
						<img src="https://eclipse.dev/eclipse.org-common/themes/solstice/public/images/logo/eclipse-ide/eclipse_logo.svg" alt="Eclipse Project" width="150"/>
					</div>
				</a>
				<nav class="header-navbar-nav">
					<ul class="header-navbar-nav-links">
						${items.join('\n')}
					</ul>
				</nav>
				<div class="header-navbar-end">
					<div class="float-right hidden-xs" id="btn-call-for-action">
						<a href="https://www.eclipse.org/sponsor/ide/" class="btn btn-huge btn-warning">
							<i class="fa fa-star"></i> Sponsor
						</a>
					</div>
					<button class="mobile-menu-btn" onclick="toggleMenu()">
						<i class="fa fa-bars fa-xl"/></i>
					</button>
				</div>
			</div>
		</div>
	</div>
	<nav id="mobile-menu" class="mobile-menu hidden" aria-expanded="false">
		<ul>
			${mobileItems.join('\n')}
		</ul>
	</nav>
</header>
`;
}

function generateDefaultBreadcrumb(element) {
	return prependChildren(element, 'breadcrumb', ...defaultBreadcrumb);
}

function generateBreadcrumb() {
	const breadcumbs = document.getElementById('breadcrumb')
	if (breadcumbs == null) {
		return '';
	}

	const elements = breadcumbs.children;
	const items = Array.from(elements).map(link => `<li>${link.outerHTML}</li>`);

	return `
<section class="default-breadcrumbs hidden-print breadcrumbs-default-margin"
	id="breadcrumb">
	<div class="container">
		<h3 class="sr-only">Breadcrumbs</h3>
		<div class="row">
			<div class="col-sm-24">
				<ol class="breadcrumb">
					${items.join('\n')}
				</ol>
			</div>
		</div>
	</div>
</section>
`;
}

function generateTOC(element) {
	const doc = document
	const tocElement = doc.getElementById('toc-target')
	const pageName = getPageName()

	//TODO: callback after page load to populate the TOC?
	const projectAside = `
	<a class="separator"><i class='fa fa-cube'></i>${pageName}</a>
	<a href="https://projects.eclipse.org/projects/eclipse.equinox">Equinox</a>
	<a href="https://projects.eclipse.org/projects/eclipse.pde">Plug-in Development Environment</a>
	`;
	const tocElements = toElements(`
	${projectAside}
	`);
	return prependChildren(element, 'aside', ...tocElements);
}

function generateDefaultAside(element) {
	return prependChildren(element, 'aside', ...defaultAside);
}

function generateTOCAside(element) {
	return prependChildren(element, 'aside', ...tocAside);
}

//TODO: generate aside (again?) after the TOC items where collected
function generateAside() {
	const elements = document.body.querySelectorAll('aside>*,#aside>*');
	if (elements.length == 0) {
		return '';
	}
	const items = Array.from(elements).map(element => {
		const main = element.classList.contains('separator')
		element.classList.add('link-unstyled');
		if (main) {
			element.classList.add('main-sidebar-heading');
			return `
<li class="main-sidebar-main-item main-sidebar-item-indented separator">
	${element.outerHTML}
</li>
`
		} else {
			return `
<li class="main-sidebar-item main-sidebar-item-indented">
	${element.outerHTML}
</li>
`
		}
	});

	return `
<div class="col-md-6 main-col-sidebar-nav">
	<aside class="main-sidebar-default-margin" id="main-sidebar">
		<ul class="ul-left-nav" id="leftnav" role="tablist" aria-multiselectable="true">
			${items.join('\n')}
	</aside>
</div>
`;
}

function toElements(text) {
	const wrapper = document.createElement('div');
	wrapper.innerHTML = text;
	return wrapper.children
}

function prependChildren(element, id, ...children) {
	element.id = id;
	element.prepend(...children);
	return element;
}
