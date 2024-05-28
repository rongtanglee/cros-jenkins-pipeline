def output
def status
def result
def buildTriggerByUpstream()
{
    return currentBuild.getBuildCauses()[0]['shortDescription'].contains('upstream')
}
pipeline {
    agent any
    parameters {
        string defaultValue: '192.168.1.115', description: 'DUT IP address', name: 'dut_ip'
        string defaultValue: 'suspend_stress_test -c 1', description: 'Type the command running on DUT', name: 'dut_cmd'
        string description: 'Set the timeout in seconds, default is infinite if no specified.', name: 'timeout'
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
        stage('Run command on DUT') {
            steps {
                script {
                    try {
                        if (params.timeout && params.timeout.trim() != '0') {
                            timeout(time: params.timeout, unit: 'SECONDS') {
                                status = sh label: 'run-dut-cmd', returnStatus: true, script: """
                                    ssh root@${params.dut_ip} '${params.dut_cmd}'
                                """
                            }
                        } else {
                            status = sh label: 'run-dut-cmd', returnStatus: true, script: """
                                ssh root@${params.dut_ip} '${params.dut_cmd}'
                            """
                        }
                    } catch (Exception ex) {
                        echo "Exception Caught: ${ex}"
                        env.TEST_RESULT = 'TEST_TIMEOUT'
                        currentBuild.result = 'SUCCESS'
                        return
                    }
                    echo "status=${status}"
                    if (status == 127) {
                        error("Command not found")
                    } else if (status == 0) {
                        env.TEST_RESULT = 'TEST_OK'
                    } else {
                        env.TEST_RESULT = 'TEST_FAIL'
                    }
                    echo "TEST_RESULT=${env.TEST_RESULT}"
                }
            }
        }
    }
    post {
        failure {
            script {
                env.TEST_RESULT = 'BUILD_FAIL'
                echo "TEST_RESULT=BUILD_FAIL"
            }
        }
        aborted {
            script {
                env.TEST_RESULT = 'BUILD_ABORT'
                echo "TEST_RESULT=BUILD_ABORT"
            }
        }
    }
}
