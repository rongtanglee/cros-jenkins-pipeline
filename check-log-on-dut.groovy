
def buildTriggerByUpstream()
{
    return currentBuild.getBuildCauses()[0]['shortDescription'].contains('upstream')
}

pipeline {
    agent any

    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        string defaultValue: "Test Failed", description: 'Test Failed once this message was found in log', name: 'failed_msg'
        string defaultValue: "Test Passed", description: 'Test Passed once this message was found in log', name: 'passed_msg'
        editableChoice choices: ['/var/log/messages', '/var/log/eventlog.txt', '/var/log/cros_ec.log', '/var/log/typecd.log', '/var/log/power_manager/powerd.LATEST'], choicesWithText: '''/var/log/messages
/var/log/eventlog.txt
/var/log/cros_ec.log
/var/log/typecd.log
/var/log/power_manager/powerd.LATEST
''', defaultValue: '/var/log/messages', description: 'Select or type the log file in DUT', name: 'log_file', withDefaultValue: [defaultValue: '/var/log/messages']
        string description: 'Set the timeout in seconds, default is no timeout(infinite) if not specified.', name: 'timeout'
    }

    stages {
        stage('Check DUT') {
            when {
                expression { return !buildTriggerByUpstream() }
            }
            steps {
                sh 'printenv'
                echo "Check DUT IP: ${params.dut_ip}"
                sh "ssh root@${params.dut_ip} 'crossystem'"

                script {
                    def product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim().toLowerCase()
                    def (_m, _f) = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s system-family'", returnStdout: true).trim().toLowerCase().split('_')
                    def family_name = _f
                    echo "product_name: ${product_name}"
                    echo "family_name: ${family_name}"

                    env.DUT_IP = "${params.dut_ip}"
                    env.DUT_BOARD = "${family_name}"
                    env.DUT_NAME = "${product_name}"

                    sh 'printenv'

                }
            }
        }

        stage('Check log file') {
            steps {
                script {
                    echo "Check log file ${params.log_file}"

                    def args = "\"${params.passed_msg}\" \"${params.failed_msg}\" ${params.log_file}"
                    echo "args=${args}"

                    def testScript = readFile("${env.HOME}/tools/scripts/check-log.sh")
                    def buildJob = build job: 'run-script-on-dut', wait: true, parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), base64File(name: 'script_file', base64: Base64.encoder.encodeToString(testScript.bytes)), string(name: 'args', value: "${args}"), string(name: 'timeout', value: "${params.timeout}")]

                    env.TEST_RESULT = buildJob.getBuildVariables()["TEST_RESULT"]

                    echo "TEST_RESULT=${env.TEST_RESULT}"
                }
            }
        }
    }

    post {

        failure {
            script {
                env.TEST_RESULT = "BUILD_FAIL"
                echo "TEST_RESULT=BUILD_FAIL"
            }
        }
        aborted {
            script {
                env.TEST_RESULT = "BUILD_ABORT"
                echo "TEST_RESULT=BUILD_ABORT"
            }
        }
    }
}
