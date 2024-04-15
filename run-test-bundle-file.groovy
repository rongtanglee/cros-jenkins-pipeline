pipeline {
    agent {
        label 'jenkins-master'
    }

    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        stashedFile description: 'Upload the test bundle file (.zip)', name: 'bundle_file'
    }

    stages {
        stage('Check DUT') {
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

        stage('Unzip bundle file') {
            steps {
                cleanWs()
                script {
                    def buildCause = currentBuild.getBuildCauses()[0]['shortDescription']
                    println ("Cause: " + buildCause)

                    if (buildCause.contains('upstream')) {
                        echo 'Build was triggered by upstream'

                        def testBundleFile = getUpstreamEnvVar('test_type_params')
                        echo "testBundleFile=${testBundleFile}"

                        def upstreamProject = buildCause.substring(buildCause.indexOf('"') + 1, buildCause.lastIndexOf('"'))
                        echo "upstreamProject: ${upstreamProject}"
                        copyArtifacts filter: "${testBundleFile}", projectName: "${upstreamProject}", selector: upstream()

                        sh "unzip ${testBundleFile}"
                        sh "ls -la"
                    } else {
                        echo 'Build was not trigged by upstream'
                        withFileParameter(name: 'bundle_file', allowNoFile: false) {
                            sh "unzip $bundle_file"
                            sh "ls -la"
                        }
                    }
                }
            }

            post {
                success {
                    archiveArtifacts artifacts: '*.hid, *.sh, *.yaml', followSymlinks: false
                }
            }
        }

        stage('Parse YAML') {
            steps {
                script {
                    def yamlMap = readYaml file: 'tests.yaml'

                    def testCases = yamlMap.test_cases
                    println(testCases)
                    for (testCase in testCases) {
                        println "Test Case: ${testCase.test}: ${testCase.name}"
                        def pre_delay = "${testCase.pre_delay ?: 0}"
                        def post_delay = "${testCase.post_delay ?: 0}"
                        def wait = (testCase.wait == false) ? false : true
                        def ignore_error = (testCase.ignore_error == true) ? true : false
                        def _timeout = "${testCase.timeout ?: 999999}"
                        def return_result = (testCase.return_result == true) ? true : false

                        println "  Pre-delay: ${pre_delay}"
                        println "  Post-delay: ${post_delay}"
                        println "  Wait: ${wait}"
                        println "  Ignore error: ${ignore_error}"
                        println "  Timeout: ${_timeout}"
                        println "  Return Result: ${return_result}"

                        if (pre_delay > 0) {
                            println "Delay for ${post_delay} seconds"
                            sleep time: "${post_delay}", unit: 'SECONDS'
                        }

                        def buildJob
                        timeout(time: "${_timeout}", unit: 'SECONDS') {
                            try {
                                if (testCase.test == 'command') {
                                    def command = "${testCase.command}"
                                    println "  Command: ${command}"

                                    buildJob = build wait: "${wait}", job: 'run-command-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'dut_cmd', value: "${command}")]
                                } else if (testCase.test == 'script') {
                                    def file = "${testCase.file}"
                                    def args = "${testCase.args}"
                                    println "  File: ${file}"
                                    println "  Args: ${args}"

                                    def testScript =readFile("${env.WORKSPACE}/${testCase.file}")
                                    buildJob = build wait: "${wait}", job: 'run-script-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), base64File(name: 'script_file', base64: Base64.encoder.encodeToString(testScript.bytes)), string(name: 'args', value: "${testCase.args}")]
                                } else if (testCase.test == 'factory') {
                                    def item = "${testCase.item}"
                                    def round = "${testCase.round}"
                                    println "  Item: ${item}"
                                    println "  Round: ${round}"

                                    buildJob = build wait: "${wait}", job: 'run-factory-test', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: 'RunIn.RunInDozingStress')]
                                } else if (testCase.test == 'tast') {
                                    def item = "${testCase.item}"
                                    def var = "${testCase.var}"
                                    println "  Item: ${item}"
                                    println "  Var: ${var}"

                                    buildJob = build wait: "${wait}", propagate: "${ignore_error ? false : true}", job: 'run-tast-test', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: "${item}"), string(name: 'var', value: "${var}"), booleanParam(name: 'verbose', value: true)]
                                } else if (testCase.test == 'replay-hid') {
                                    def file = "${testCase.file}"
                                    def round = "${testCase.round}"
                                    def delay = "${testCase.delay}"
                                    println "  File: ${file}"
                                    println "  Round: ${round}"
                                    println "  Delay: ${delay}"

                                    buildJob = build wait: "${wait}", propagate: "${ignore_error ? false : true}", job: 'replay-hid', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'replay_iter', value: "${round}"), string(name: 'delay', value: "${delay}")]
                                }
                            } catch (err) {
                                    echo "Caught: ${err}"
                                    if (ignore_error)
                                        currentBuild.result = 'UNSTABLE'
                                    else
                                        currentBuild.result = 'FAILURE'
                                }
                        }

                        if (post_delay > 0) {
                            println "Delay for ${post_delay} seconds"
                            sleep time: "${post_delay}", unit: 'SECONDS'
                        }

                        if (return_result && buildJob) {
                            env.TEST_RESULT = buildJob.getBuildVariables()["TEST_RESULT"]
                            currentBuild.result = "SUCCESS"
                        }

                    }
                }
            }

            post {
                success {
                    echo "This test is success"
                    echo "TEST_RESULT = ${env.TEST_RESULT}"
                }
                failure {
                    script {
                        env.TEST_RESULT = 'BUILD_FAIL'
                    }
                }
                aborted {
                    script {
                        env.TEST_RESULT = 'TEST_ABORT'
                    }
                }
                unstable {
                    echo "The test is unstable"
                    echo "TEST_RESULT = ${env.TEST_RESULT}"
                }
            }
        }

        stage('Check Test Result') {
            steps {
                echo "TEST_RESULT=${env.TEST_RESULT}"
            }
        }
    }
}

@NonCPS
def getUpstreamEnvVar(variable) {
    def upstreamBuild = currentBuild.upstreamBuilds.get(0).getRawBuild()
    def upstreamEnvVars = upstreamBuild.getEnvironment(TaskListener.NULL)
    println "upstreamEnvVars=${upstreamEnvVars}"
    return upstreamEnvVars["${variable}"]
}
