def config = new groovy.json.JsonSlurper().parseText(readFileFromWorkspace('JenkinsJobs/JobDSL.json'))

folder('Builds') {
  description('Eclipse periodic build jobs.')
}

for (STREAM in config.Streams){
	def BRANCH = config.Branches[STREAM]

	pipelineJob('Builds/I-build-' + STREAM){
		//TODO: disable job initially if it doesn't exist before?
		description('Daily Eclipse Integration builds.')
		properties {
			pipelineTriggers {
				triggers {
					cron {
						spec('''TZ=America/Toronto
# format: Minute Hour Day Month Day-of-week (1-7)
# - - - Integration Eclipse SDK builds - - - 
# Normal Schedule: 6 PM every day
0 18 * * *

# RC Schedule: Additional build at 6 AM every day
0 6 15-29 8 *
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

pipelineJob('Builds/Build-Docker-images'){
	description('Build and publish custom Docker images')
	properties {
		pipelineTriggers {
			triggers {
				cron { spec('@weekly') }
			}
		}
	}
	definition {
		cpsScm {
			lightweight(true)
			scm {
				github('eclipse-platform/eclipse.platform.releng.aggregator', 'master')
			}
			scriptPath('JenkinsJobs/Builds/DockerImagesBuild.jenkinsfile')
		}
	}
}
