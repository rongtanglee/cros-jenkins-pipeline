def product_name
def build_agent
def autotest_cmd
def resultFolder
def buildOutputFile
def board_name
pipeline {
    agent any
    parameters {
        string defaultValue: '192.168.1.149', description: 'DUT IP address', name: 'dut_ip'
        choice choices: ['auto', 'brya', 'brask', 'nissa', 'dedede', 'rex', 'brox'], description: 'Choose Reference Board', name: 'board'
        string defaultValue: 'f:.*power_UiResume/control', description: 'Specify test suite to run autotest', name: 'test_suite'
        string defaultValue: '', description: 'Specify argument', name: 'args'
        string description: 'Number of times to run the tests specified', name: 'iterations'
    }
    stages {
        stage('Check DUT') {
            steps {
                sh 'printenv'
                echo 'Check DUT'
                sh "ssh root@${params.dut_ip} 'crossystem'"
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim().toLowerCase()
                    def (_m, _f) = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s system-family'", returnStdout: true).trim().toLowerCase().split('_')
                    def family_name = _f
                    echo "product_name: ${product_name}"
                    echo "family_name: ${family_name}"
                    if (params.board == 'auto') {
                        board_name = family_name
                    } else {
                        board_name = ${params.board}
                    }
                    if (board_name == 'brask') {
                        build_agent = 'cros-brya-d12'
                    } else {
                        build_agent = "cros-${family_name}-d12"
                    }
                    echo "build_agent=${build_agent}"
                    echo "verbose=${params.verbose}"
                }
            }
        }
        stage ('Run autotest') {
            agent {
                label "${build_agent}"
            }
            steps {
                sh 'printenv'
                cleanWs()
                script {
                    autotest_cmd = "test_that -b ${board_name} ${params.dut_ip} ${params.test_suite}"
                    if ((!params.args.isEmpty())) {
                        autotest_cmd = "${autotest_cmd}" + " --args=\'${params.args}\'"
                    }
                    if ((!params.iterations.isEmpty())) {
                        autotest_cmd = "${autotest_cmd}" + " --iterations ${params.iterations}"
                    }
                    echo "autotest_cmd=${autotest_cmd}"
                    dir("${env.WORKSPACE}") {
                        buildOutputFile = "${env.WORKSPACE}/build_output.log"
                        sh "touch ${buildOutputFile}"
                    }
                    dir("${env.HOME}/cros-tot") {
                        sh returnStdout: false, script: "${env.HOME}/bin/depot_tools/cros_sdk ${autotest_cmd} | tee " + "${buildOutputFile}"
                        def savedOutput = readFile(buildOutputFile).trim()
                        //println(savedOutput)
                        def result_line = sh returnStdout: true, script: "grep \'Results can be found in\' ${buildOutputFile}"
                        echo "result_line = ${result_line}"
                        def match = result_line =~ /(\/tmp\/test_that_results_[^ ]+)/
                        resultFolder = match ? match[0][1] : null
                        echo "resultFolder=${resultFolder}"
                    }
                }
            }
            post {
                success {
                    dir("${env.HOME}/cros-tot/out/${resultFolder}") {
                        archiveArtifacts artifacts: 'test_report.*', followSymlinks: false
                        stash includes: 'test_report.log', name: 'test-report-artifact'
                    }
                }
            }
        }
        stage ('Check test result') {
            agent {
                label "${build_agent}"
            }
            steps {
                script {
                    def pass_count
                    def fail_count
                    unstash 'test-report-artifact'
                    try {
                        pass_count = sh(returnStdout: true, script: "grep -c \'\\[  PASSED  \\]\' test_report.log").trim().toInteger()
                        fail_count = sh(returnStdout: true, script: "grep -c \'\\[  FAILED  \\]\' test_report.log").trim().toInteger()
                    } catch (Exception ex) {
                        echo "Exception Caught: ${ex}"
                        if (pass_count == null)
                            pass_count = 0
                        if (fail_count == null)
                            fail_count = 0
                    }
                    echo "pass_count=${pass_count}, fail_count=${fail_count}"
                    if (pass_count > 0 && fail_count == 0) {
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
            }
        }
        aborted {
            script {
                env.TEST_RESULT = 'BUILD_ABORT'
            }
        }
    }
}
