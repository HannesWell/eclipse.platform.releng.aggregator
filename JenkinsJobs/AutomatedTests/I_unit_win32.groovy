def config = new groovy.json.JsonSlurper().parseText(readFileFromWorkspace('JenkinsJobs/JobDSL.json'))
def STREAMS = config.Streams

def BUILD_CONFIGURATIONS = [ 
  [arch: 'aarch64', javaVersion: '21', agentLabel: 'rie8t-win11-arm64', javaHome: 'C:\\\\Program Files (Arm)\\\\Microsoft\\\\jdk-21.0.2.13-hotspot' ]
  [arch: 'x86_64',  javaVersion: '17', agentLabel: 'qa6xd-win11',       javaHome: 'C:\\\\Program Files\\\\Eclipse Adoptium\\\\jdk-17.0.11+9' ]
]

for (STREAM in STREAMS){
for (B_CONFIG in BUILD_CONFIGURATIONS){	
  def MAJOR = STREAM.split('\\.')[0]
  def MINOR = STREAM.split('\\.')[1]

  pipelineJob('AutomatedTests/ep' + MAJOR + MINOR + 'I-unit-win32-' + B_CONFIG.arch + '-java' + B_CONFIG.javaVersion){
    description('Run Eclipse SDK Tests for the platform implied by this job\'s name')
    parameters { // Define parameters in job configuration to make them available from the very first build onwards
      stringParam('buildId', null, 'Build Id to test (such as I20240611-1800, N20120716-0800).')
    }

    authenticationToken('windows2012tests')
 
    definition {
      cps {
        sandbox()
        script('''
pipeline {
  options {
    timeout(time: 901, unit: 'MINUTES')
    timestamps()
    buildDiscarder(logRotator(numToKeepStr:'5'))
  }
  agent {
    label \'''' + B_CONFIG.agentLabel + '''\'
  }

  stages {
      stage('Run tests'){
          environment {
              // Declaring a jdk and ant the usual way in the 'tools' section, because of unknown reasons, breaks the usage of system commands like xvnc, pkill and sh
              JAVA_HOME = \'''' + B_CONFIG.javaHome + '''\'
              eclipseArch = \'''' + B_CONFIG.arch + ''''
          }
          steps {
              cleanWs() // workspace not cleaned by default
              bat \'\'\'
rem May want to try and restrict path, as we do on cron jobs, so we
rem have more consistent conditions.
rem export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:~/bin

rem tmp must already exist, for Java to make use of it, in subsequent steps
rem no -p (or /p) needed on Windows. It creates 
mkdir tmp

rem Note: currently this file always comes from master, no matter what branch is being built/tested.
wget -O getEBuilder.xml --no-verbose https://download.eclipse.org/eclipse/relengScripts/production/testScripts/hudsonBootstrap/getEBuilder.xml 2>&1
set buildId
wget -O buildProperties.properties https://download.eclipse.org/eclipse/downloads/drops4/%buildId%/buildproperties.properties
echo off
For /F "tokens=1* delims==" %%A IN (buildProperties.properties) DO (
 IF "%%A"=="STREAM " set STREAM=%%B
 IF "%%A"=="EBUILDER_HASH " set EBUILDER_HASH=%%B
) 
echo on
set STREAM
set EBUILDER_HASH
set JAVA_HOME

ant -f getEBuilder.xml -Djava.io.tmpdir=%WORKSPACE%/tmp -DbuildId=%buildId%  -DeclipseStream=%STREAM% -DEBUILDER_HASH=%EBUILDER_HASH% ^
  -DdownloadURL="https://download.eclipse.org/eclipse/downloads/drops4/%buildId%" ^
  -Dargs=all -Dosgi.os=win32 -Dosgi.ws=win32 -Dosgi.arch=%eclipseArch% ^
  -DtestSuite=all ^
   -Djvm="%JAVA_HOME%\\\\bin\\\\java.exe"

              \'\'\'
              archiveArtifacts '**/eclipse-testing/results/**, **/eclipse-testing/directorLogs/**, *.properties, *.txt'
              junit keepLongStdio: true, testResults: '**/eclipse-testing/results/xml/*.xml'
              build job: 'Releng/ep-collectResults', wait: false, parameters: [
                string(name: 'triggeringJob', value: "${JOB_BASE_NAME}"),
                string(name: 'buildURL', value: "${BUILD_URL}"),
                string(name: 'buildID', value: "${params.buildId}")
              ]
          }
      }
  }
}
        ''')
      }
    }
  }
}
}
