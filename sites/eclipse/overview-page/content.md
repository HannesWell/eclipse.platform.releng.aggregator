The overview page listing all available build

#TODO: Create one json file that contains all the information of all listed builds:
- Name/path
- Number of expected and completed tests
- A potential instability of the build


The data are retried by scanning the storage server in the updateIndex job.
This can also handle the format of old pages in the old format.
At the same time the page of each build relies only on the information supplied by each test-configuraiton in a separate file so that the `updateTestResultIndex` is obsolete and the tests just upload their data on completeion and trigger an index update
Most files should exist from the beginning but their content should indicate that the results are not yet available (e.g. should be empty)