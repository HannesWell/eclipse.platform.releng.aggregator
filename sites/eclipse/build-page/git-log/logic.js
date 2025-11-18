
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
