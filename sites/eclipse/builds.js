
async function loadMarkdown() {
    try {
        const response = await fetch('content.md');
        if (!response.ok) {
            throw new Error('Failed to load markdown file');
        }
        const markdown = await response.text();
        document.getElementById('content').innerHTML = marked.parse(markdown);
    } catch (error) {
        document.getElementById('content').innerHTML = `<p style="color: red;">Error: ${error.message}</p>`;
    }
}


function generateDefaultBreadcrumb(element) {
	return prependChildren(element, 'breadcrumb', ...defaultBreadcrumb);
}


function prependChildren(element, id, ...children) {
	element.id = id;
	element.prepend(...children);
	return element;
}

let meta = toElements(`
<meta charset="utf-8">
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="shortcut icon" href="https://eclipseide.org/favicon.ico"/>
`);

function generate() {
	selfContent = document.documentElement.innerHTML;
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
	} catch (exception) {
		document.body.prepend(...toElements(`<span>Failed to generate content: <span><b style="color: FireBrick">${exception.message}</b><br/>`));
		console.log(exception);
	}
}