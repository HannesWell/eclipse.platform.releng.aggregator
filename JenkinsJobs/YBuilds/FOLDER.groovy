def config = new groovy.json.JsonSlurper().parseText(readFileFromWorkspace('JenkinsJobs/JobDSL.json'))

folder('YPBuilds') {
  displayName('Y and P Builds')
  description('Builds and tests for the beta java builds.')
}

for (STREAM in config.Streams){
	def BRANCH = config.Branches[STREAM]

	pipelineJob('YPBuilds/Y-build-' + STREAM){
		//TODO: disable job initially if it doesn't exist before?
		description('Daily Maintenance Builds.')
		properties {
			pipelineTriggers {
				triggers {
					cron {
						spec('''TZ=America/Toronto
# format: Minute Hour Day Month Day-of-week (1-7)
# Normal Schedule: 10 AM every second day
0 10 * * 2,4,6
# RC Schedule: 10 AM every day
0 10 15-29 8 *
''')
					}
				}
			}
		}
		definition {
			cpsScm {
				lightweight(true)
				scm {
					github('eclipse-platform/eclipse.platform.releng.aggregator', BRANCH)
				}
				scriptPath('JenkinsJobs/Builds/build.jenkinsfile')
			}
		}
	}

}
