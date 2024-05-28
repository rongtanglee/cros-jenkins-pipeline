import groovy.transform.Field
@Field def jobListNonWait = []
@Field def all_tests_parsed = false
def test_bundle_yaml_file = 'test-bundle.yaml'
@NonCPS
def getUpstreamEnvVar(variable) {
    def upstreamBuild = currentBuild.upstreamBuilds.get(0).getRawBuild()
    def upstreamEnvVars = upstreamBuild.getEnvironment(TaskListener.NULL)
    println "upstreamEnvVars=${upstreamEnvVars}"
    return upstreamEnvVars["${variable}"]
}
@NonCPS
def waitForAllJobs(jobList)
{
    def all_job_done = false
    if (all_tests_parsed && (jobList.size() == 0))
        return
    while (!all_job_done) {
        if (jobList.size() != 0) {
            jobList.each { job ->
                def jobName = job.jobName
                def buildID = job.buildID
                def build = Jenkins.instance.getItemByFullName(jobName).getBuildByNumber(buildID)
                println(build)
                def result = build.getResult()
                println(result)
                job['buildResult'] = result
                if (job['return_test_result'] && result != null) {
                    def jobBuild = job['jobBuild']
                    env.TEST_RESULT = jobBuild.getBuildVariables()["TEST_RESULT"]
                    all_job_done = true
                    return
                }
            }
            if (!all_job_done)
                all_job_done = jobList.every { it.buildResult != null }
        }
        echo "Job List: ${jobList}"
        Thread.sleep(1000)
    }
}
def buildTriggerByUpstream()
{
    return currentBuild.getBuildCauses()[0]['shortDescription'].contains('upstream')
}
def runJob(jobName, params, ctl_map)
{
    def pre_delay = ctl_map['pre_delay']
    def post_delay = ctl_map['post_delay']
    def wait_for_completion = ctl_map['wait_for_completion']
    def ignore_error = ctl_map['ignore_error']
    def return_test_result = ctl_map['return_test_result']
    try {
        if (pre_delay > 0) {
            echo "Pre-delay for ${pre_delay} seconds"
            sleep(pre_delay)
        }
        // If no wait for job completion, but wait for the job starting
        def jobBuild = build job: jobName, parameters: params, wait: wait_for_completion, waitForStart: !wait_for_completion
        if (post_delay > 0) {
            echo "Post-delay for ${post_delay} seconds"
            sleep(post_delay)
        }
        if (! wait_for_completion) {
            jobListNonWait << [jobBuild: jobBuild, jobName: jobName, buildID: jobBuild.getNumber(), buildResult: null, testResult: null, returnTestResult: return_test_result]
        }
        return jobBuild
    } catch (Exception e) {
        echo "Failed to trigger downstream job: ${e.message}"
        if (! ignore_error) {
            currentBuild.result = 'FAILURE'
        }
        return null
    }
}
def findFirstYamlFile() {
    def workspacePath = pwd()
    def yamlFile = ''
    def files = new File(workspacePath).listFiles().findAll { it.isFile() && it.name.endsWith('.yaml') }
    if (files) {
        yamlFile = files[0].name
    }
    return yamlFile
}
pipeline {
    agent {
        label 'jenkins-master'
    }
    options { timestamps () }
    parameters {
        string defaultValue: '192.168.1.104', description: 'DUT IP address', name: 'dut_ip'
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
                    if (buildTriggerByUpstream()) {
                        def testBundleFile = getUpstreamEnvVar('test_type_params')
                        echo "testBundleFile=${testBundleFile}"
                        def upstreamProject = currentBuild.upstreamBuilds[0].projectName //buildCause.substring(buildCause.indexOf('"') + 1, buildCause.lastIndexOf('"'))
                        def upstreamBuildID = currentBuild.upstreamBuilds[0].number
                        echo "upstreamProject: ${upstreamProject} #${upstreamBuildID}"
                        copyArtifacts filter: "${testBundleFile}", projectName: "${upstreamProject}", selector: specific("${upstreamBuildID}")
                        test_bundle_yaml_file = testBundleFile.tokenize('.')[0] + '.yaml'
                        echo "test_bundle_yaml_file=${test_bundle_yaml_file}"
                        sh "unzip ${testBundleFile}"
                        sh "ls -la"
                    } else {
                        echo 'Build was not trigged by upstream'
                        //test_bundle_yaml_file = env.bundle_file_FILENAME.tokenize('.')[0] + '.yaml'
                        //echo "test_bundle_yaml_file=${test_bundle_yaml_file}"
                        withFileParameter(name: 'bundle_file', allowNoFile: false) {
                            sh "unzip $bundle_file"
                            sh "ls -la"
                        }
                        test_bundle_yaml_file = findFirstYamlFile()
                        echo "test_bundle_yaml_file=${test_bundle_yaml_file}"
                        if (fileExists(test_bundle_yaml_file)) {
                            echo "Test Bundle YAML file exist: ${test_bundle_yaml_file}"
                        } else {
                            error "Test Bundle YAML file not exist: ${test_bundle_yaml_file}"
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
stage('Parallel Stages') {
    parallel {
        stage('Parse YAML and Run Test') {
            steps {
                script {
                    def yamlMap = readYaml file: "${test_bundle_yaml_file}"
                    def testCaseName = yamlMap.name
                    echo "Test Case Name: ${testCaseName}"
                    def testCases = yamlMap.test_cases
                    println(testCases)
                    for (testItem in testCases) {
                        println "Test Item: ${testItem.test}: ${testItem.name}"
                        def _pre_delay = "${testItem.pre_delay ?: 0}"    // default is 0
                        def _post_delay = "${testItem.post_delay ?: 0}"  // default is 0               
                        def _wait_for_completion = (testItem.wait_for_completion == false) ? false : true       // default is true
                        def _ignore_error = (testItem.ignore_error == true) ? true : false  // default is false
                        def _timeout = "${testItem.timeout ?: 0}"         // default is infinite (999999)
                        def _return_test_result = (testItem.return_test_result == true) ? true : false       // default is false
                        def ctl_map = [pre_delay: _pre_delay, post_delay: _post_delay, wait_for_completion: _wait_for_completion, ignore_error: _ignore_error, return_test_result: _return_test_result, timeout: _timeout]
                        println " ctl_map=${ctl_map}"
                        def buildJob = null
                        def test_result = null
                        try {
                            if (testItem.test == 'command') {
                                def command = "${testItem.command}"
                                println "  command: ${command}"
                                buildJob = runJob('run-command-on-dut', [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'dut_cmd', value: "${command}"), string(name: 'cmd_timeout', value: "${_timeout}")], ctl_map)
                            } else if (testItem.test == 'script') {
                                def file = "${testItem.file}"
                                def args = "${testItem.args}"
                                println "  file: ${file}"
                                println "  args: ${args}"
                                def testScript = readFile("${env.WORKSPACE}/${testItem.file}")
                                buildJob = runJob('run-script-on-dut', [string(name: 'dut_ip', value: "${params.dut_ip}"), base64File(name: 'script_file', base64: Base64.encoder.encodeToString(testScript.bytes)), string(name: 'args', value: "${args}"), string(name: 'script_timeout', value: "${_timeout}")], ctl_map)
                            } else if (testItem.test == 'log') {
                                def fail_msg = "${testItem.fail_msg}"
                                def pass_msg = "${testItem.pass_msg}"
                                def log_file = "${testItem.log_file ?: '/var/log/messages'}"
                                println "  fail_msg: ${fail_msg}"
                                println "  pass_msg: ${pass_msg}"
                                println "  log_file: ${log_file}"
                                buildJob = runJob('check-log-on-dut', [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'failed_msg', value: "${fail_msg}"), string(name: 'passed_msg', value: "${pass_msg}"), string(name: 'log_file', value: "${log_file}"), string(name: 'log_timeout', value: "${_timeout}")], ctl_map)
                            } else if (testItem.test == 'factory') {
                                def item = "${testItem.item}"
                                def round = "${testItem.round}"
                                println "  Item: ${item}"
                                println "  Round: ${round}"
                                buildJob = runJob('run-factory-test', [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: "${item}")], ctl_map)
                            } else if (testItem.test == 'tast') {
                                def item = "${testItem.item}"
                                def var = "${testItem.var}"
                                println "  Item: ${item}"
                                println "  Var: ${var}"
                                buildJob = runJob('run-tast-test', [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: "${item}"), string(name: 'var', value: "${var}"), booleanParam(name: 'verbose', value: true)], ctl_map)
                            } else if (testItem.test == 'autotest') {
                                def test_suite = "${testItem.test_suite}"
                                def test_args = testItem.args ?: ""
                                def test_iterations = testItem.iterations ?: ""
                                println "  Test Suite: ${test_suite}"
                                println "  Test args: ${test_args}"
                                println "  Test iterations: ${test_iterations}"
                                buildJob = runJob('run-autotest', [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'board', value: 'auto'), string(name: 'test_suite', value: "${test_suite}"), string(name: 'args', value: "${test_args}"), string(name: 'iterations', value: "${test_iterations}")], ctl_map)
                            } else if (testItem.test == 'replay-hid') {
                                def file = "${testItem.file}"
                                def round = "${testItem.round}"
                                def delay = "${testItem.delay}"
                                println "  File: ${file}"
                                println "  Round: ${round}"
                                println "  Delay: ${delay}"
                                buildJob = runJob('replay-hid', [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'replay_iter', value: "${round}"), string(name: 'delay', value: "${delay}")], ctl_map)
                            }
                            echo "jobListNonWait=${jobListNonWait}"
                            if (buildJob) {
                                test_result = buildJob.getBuildVariables()["TEST_RESULT"]
                            }
                            echo "${testItem.test} test result: ${test_result}"
                            // We get the test result already, and need to report the result
                            if (test_result != null && _return_test_result) {
                                echo "Return test result: ${test_result}"
                                env.TEST_RESULT = test_result
                                break
                            }
                        } catch (Exception ex) {
                            echo "Exception Caught: ${ex}"
                            env.TEST_RESULT = 'BUILD_ABORT'
                            if (! _ignore_error)
                                break
                        }
                    }
                    all_tests_parsed = true
                }
            }
        }
        stage('Check Test Items Result') {
            steps {
                script {
                    echo 'Waiting for all test items completed'
                    def all_job_done = false
                    while (!all_job_done) {
                        sleep 5
                        if (jobListNonWait.size() != 0) {
                            jobListNonWait.each { job ->
                                def jobName = job.jobName
                                def buildID = job.buildID
                                def build = Jenkins.instance.getItemByFullName(jobName).getBuildByNumber(buildID)
                                println(build)
                                def result = build.getResult()   // Get Build Result
                                println(result)
                                if (result == null) {
                                    echo "Test Item: ${jobName} #${buildID} is still running"
                                } else {
                                     job['buildResult'] = result
                                     job['testResult'] = job['jobBuild'].getBuildVariables()["TEST_RESULT"]
                                     echo "jobListNonWait=${jobListNonWait}"
                                     if (job['returnTestResult']) {
                                        env.TEST_RESULT = job['testResult']
                                        return
                                     }
                                }
                            }
                            def nonwait_jobs_done = jobListNonWait.every { it.buildResult != null }
                            if (nonwait_jobs_done && all_tests_parsed) {
                                echo "All tests done !!!"
                                all_job_done = true
                            }
                        }
                        if (all_tests_parsed && jobListNonWait.size() == 0) {
                            echo "All tests parsed and No nonwait Job"
                            all_job_done = true
                        }
                    }
                    echo 'Check Test Item Result completed'
                }
            }
        }
    }  // parallel
}
        stage('Check Test Bundle Result') {
            steps {
                script {
                    echo "TEST_RESULT=${env.TEST_RESULT}"
                    // TEST_RESULT is not available, check Non-wait jobs
                    //if (env.TEST_RESULT == null) {
                    //    waitForAllJobs(jobListNonWait)
                    //}
                    //echo "TEST_RESULT=${env.TEST_RESULT}"
                }
            }
        }
    }
}
