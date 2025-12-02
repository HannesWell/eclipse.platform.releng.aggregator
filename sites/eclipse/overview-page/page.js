// 1. Create a new XMLHttpRequest object
const request = new XMLHttpRequest();

// 2. Configure the request: set the third argument to 'false' for synchronous
const url = '../build-page/page.js'
request.open('GET', url, false); // *** The 'false' makes the request synchronous ***

// 3. Send the request
request.send(null);

// 4. Check if the request was successful
if (request.status === 200) {
    console.log(`Synchronously loaded script: ${url}`);

    // 5. Execute the fetched script content immediately using eval()
    // This executes the code in the global scope.
    eval(request.responseText);

} else {
    // Handle errors (e.g., file not found, network issue)
    console.error(`Error loading script synchronously: ${url} (Status: ${request.status})`);
}