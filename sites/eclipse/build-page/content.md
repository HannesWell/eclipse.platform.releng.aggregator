## Eclipse <span class="data-ref">${release}</span> <span class="data-ref">${kind}</span> Build: <span class="data-ref">${label}</span>

This page provides access to the various deliverables of Eclipse Platform build along with its logs and tests.


<!-- TODO: Check what's always shown and what only for release/RC builds! -->
<a class="data-ref" href="https://eclipse.dev/eclipse/news/${releaseShort}">New and Noteworthy</a><br>
<a class="data-ref" href="https://eclipse.dev/eclipse/markdown/?f=news/${releaseShort}/acknowledgements.md">Acknowledgments</a><br>
<a class="data-ref" href="https://eclipse.dev/eclipse/development/readme.html?file=readme_eclipse_${releaseShort}.html">Eclipse Project ${releaseShort} Readme</a><br>
<a class="data-ref" href="https://eclipse.dev/eclipse/development/plans.html?file=plans/eclipse_project_plan_${releaseShort}.xml">Eclipse Project Plan</a><br>

### Logs and Test Links

- View the [logs for the current build](testresults).
- View the [integration and unit test results for the current build.](testresults).

### Summary of Unit Tests Results

<!-- TODO: inject this value! -->
6 of 6 integration and unit test configurations are complete.

<table id="test-results-summary-table">
	<thead>
		<tr>
			<th>Tested Platform</th>
			<th>Failed</th>
			<th>Passed</th>
			<th>Total</th>
			<th>Test Time (s)</th><!-- TODO: change this to minutes or just show the time at the values -->
		</tr>
	</thead>
</table>

### Related Links

- <a class="data-ref" href="https://eclipse.dev/eclipse/development/plans/eclipse_project_plan_${releaseShort}.xml#target_environments">Target Platforms and Environments.</a>
- [Git log](gitLog.php)
- [How to verify a download.](https://wiki.eclipse.org/Platform-releng/How_to_check_integrity_of_downloads)

### Eclipse p2 Repository
<!-- TODO: add link to details page: details.html#Repository -->
To update your Eclipse installation to this development stream, you can use the software repository at
- <a class="data-ref" href="https://download.eclipse.org/eclipse/updates/${releaseShort}/">https://download.eclipse.org/eclipse/updates/${releaseShort}</a>

To update your build to use this specific build, you can use the software repository at
- <a class="data-ref" href="https://download.eclipse.org/eclipse/updates/${releaseShort}/${identifier}/">https://download.eclipse.org/eclipse/updates/${releaseShort}/${identifier}</a>

| Platform | Download | Size |
| -------- | -------- | ---- |
| All | <a class="data-ref" href="https://www.eclipse.org/downloads/download.php?file=/eclipse/downloads/drops4/${identifier}/${p2Repository.filename}">${p2Repository.filename}</a><br> | <span class="data-ref">${p2Repository.fileSize}</span> |
<!-- TODO: add Add repo icon-->

### Eclipse SDK

### Tests and Testing Framework

### Platform Runtime Binary

### JDT Core Batch Compiler

### SWT Binary and Source
