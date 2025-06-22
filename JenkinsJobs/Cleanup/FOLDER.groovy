folder('Cleanup') {
  description('Cleanup Scripts.')
}

//TODO: Improve jobs names (and filenames!)

pipelineJob('Cleanup/rt.equinox.releng.cleanupDLsite'){
	properties {
		pipelineTriggers {
			triggers {
				cron { spec('45 7 * * *') }
			}
		}
	}
	definition {
		cpsScm {
			lightweight(true)
			scm {
				github('eclipse-platform/eclipse.platform.releng.aggregator', 'master')
			}
			scriptPath('JenkinsJobs/Cleanup/cleanupDLsite.jenkinsfile')
		}
	}
}

pipelineJob('Cleanup/dailyCleanOldBuilds'){
	displayName('Daily Cleanup for old Builds')
	description('''
This job runs several types of "cleanup" on the build machine and downloads server to remove old builds and other left overs from old build.
It acts as a simple cron job, currently running at 16:00 every day, to execute 
.../sdk/cleaners/dailyCleanBuildMachine.sh
and other such scripts.
''')
	properties {
		pipelineTriggers {
			triggers {
				cron { spec('''
0 4 * * *
0 16 * * *
				''') }
			}
		}
	}
	definition {
		cpsScm {
			lightweight(true)
			scm {
				github('eclipse-platform/eclipse.platform.releng.aggregator', 'master')
			}
			scriptPath('JenkinsJobs/Cleanup/dailyCleanOldBuilds.jenkinsfile')
		}
	}
}

pipelineJob('Cleanup/pruneDailyRepos'){
	displayName('Daily Repo Pruner')
	description('''
This job runs several types of "cleanup" on the build machine and downloads server to remove old builds and other left overs from old build.
It acts as a simple cron job, currently running at 16:00 every day, to execute 
.../sdk/cleaners/dailyCleanBuildMachine.sh
and other such scripts.
''')
	properties {
		pipelineTriggers {
			triggers {
				cron { spec('''
0 5 * * *
0 17 * * *
				''') }
			}
		}
	}
	definition {
		cpsScm {
			lightweight(true)
			scm {
				github('eclipse-platform/eclipse.platform.releng.aggregator', 'master')
			}
			scriptPath('JenkinsJobs/Cleanup/pruneDailyRepos.jenkinsfile')
		}
	}
}
