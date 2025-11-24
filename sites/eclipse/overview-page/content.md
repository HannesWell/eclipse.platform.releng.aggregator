# The Eclipse Project Downloads

<!--TODO: check all the links and migrate them if necessary-->
On this page you can find the latest builds produced by the [Eclipse Project](https://www.eclipse.org/eclipse/).
To get started, run the program and go through the user and developer documentation provided in the help system or see the [web-based help system](http://help.eclipse.org/).
If you have problems installing or getting the workbench to run, [check out the Eclipse Project FAQ](https://wiki.eclipse.org/The_Official_Eclipse_FAQs), or try posting a question to the [forum](https://www.eclipse.org/forums/).

See the [main Eclipse Foundation download site](https://www.eclipse.org/downloads/) for convenient all-in-one packages.
The [archive site](http://archive.eclipse.org/eclipse/downloads/) contains older releases (including the last 3.x version, [3.8.2](http://archive.eclipse.org/eclipse/downloads/drops/R-3.8.2-201301310800/)).
For reference, see also the [p2 repositories provided](https://wiki.eclipse.org/Eclipse_Project_Update_Sites), meaning of [kinds of builds (P,M,I,S, and R)](https://download.eclipse.org/eclipse/downloads/build_types.html), and the [build schedule](https://www.eclipse.org/eclipse/platform-releng/buildSchedule.html).

### Latest Downloads

| Build Name    | Build Status  | Build Date   |
| ------------- | :------------- |------------- |
| 4.37          | Cell 2, Row 1 | Col 3        |
| 4.37          | Cell 2, Row 1 | Col 3        |


<table class="data-table" data-path="latest">
	<thead>
		<tr>
			<th>Build Name</th>
			<th>Build Status</th>
			<th>Build Date</th>
		</tr>
	</thead>
	<tr>
		<td><span class="data-ref" data-path="name"></span></td>
		<td>(<span class="data-ref" data-path="testStatus"></span> platforms)</td>
		<td><span class="data-ref" data-path="date"></span></td>
	</tr>
</table> 


asda
sda
sd
asd

#############################################################################
TODO: remove the following
The overview page listing all available build

#TODO: Create one json file that contains all the information of all listed builds:
- Name/path
- Number of expected and completed tests
- A potential instability of the build


The data are retried by scanning the storage server in the updateIndex job.
This can also handle the format of old pages in the old format.
At the same time the page of each build relies only on the information supplied by each test-configuraiton in a separate file so that the `updateTestResultIndex` is obsolete and the tests just upload their data on completeion and trigger an index update
Most files should exist from the beginning but their content should indicate that the results are not yet available (e.g. should be empty)