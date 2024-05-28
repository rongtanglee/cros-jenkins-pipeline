@Library("jenkinsci-unstashParam-library") _
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

def product_name = ''
def family_name = ''
def current_dt = ''
def kernel_release = ''
def kernel_package = ''
def kernel_version = ''
def prev_local_branch = ''
def cros_sdk_folder = '/home/ron/cros-tot'
def build_agent = ''

@NonCPS
String getLogFromRunWrapper(RunWrapper runWrapper, int logLines) {
    runWrapper.getRawBuild().getLog(logLines).join('\n')
}

def runBisectTest(testType, testParams) {
    def buildResult
    def buildJob
    switch (testType) {
        case 'interactive':
            def pass_or_fail = input id: 'select_passed_or_failed', message: "Is this test Passed or Failed ?", parameters: [choice(choices: ["Passed", "Failed"], description: 'Test Passed or Failed', name: 'interactive_test_result')]
            if (pass_or_fail == 'Passed') {
                buildResult = 'TEST_OK'
            } else {
                buildResult = 'TEST_FAIL'
            }
            break
        case 'command':
            def dut_cmd = testParams.replace(",", "")
            echo "DUT command=${dut_cmd}"
            buildJob = build job: 'run-command-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'dut_cmd', value: "${dut_cmd}"), string(name: 'timeout', value: "${params.timeout}")]
            break
        case 'script':
            dir("${env.WORKSPACE}") {
                def script_file = unstashParam "test_type_params"
                sh "cat ${script_file}"
                def testScript = readFile("${script_file}")
                buildJob = build job: 'run-script-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), base64File(name: 'script_file', base64: Base64.encoder.encodeToString(testScript.bytes)), string(name: 'timeout', value: "${params.timeout}")]
            }
            break
        case 'log':
            def fail_msg, pass_msg
           try {
                (fail_msg, pass_msg) = testParams.split(',')
            } catch (Exception ex) {
                echo "fail_msg=${fail_msg}"
                echo "pass_msg=${pass_msg}"
                fail_msg = fail_msg ?: ""
                pass_msg = pass_msg ?: ""
            }
            echo "fail_msg=${fail_msg}, pass_msg=${pass_msg}"
            buildJob = build job: 'check-log-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'failed_msg', value: "${fail_msg}"), string(name: 'passed_msg', value: "${pass_msg}"), string(name: 'log_file', value: '/var/log/messages'), string(name: 'timeout', value: "${params.timeout}")]
            break
        case 'factory':
            def test_item = testParams.replace(",", "")
            echo "factory test item: ${test_item}"
            buildJob = build job: 'run-factory-test', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: "${test_item}")]
            break
        case 'tast':
            def test_item = ''
            def vars = ''
            def substringCount = testParams.split(',').length
            echo "substringCount=${substringCount}"
            if (substringCount == 1) {
                test_item = testParams.replace(",", "")
            } else {
                (test_item, vars) = testParams.split(',')
            }
            echo "Tast test item: ${test_item}"
            echo "Tast vars: ${vars}"
            buildJob = build job: 'run-tast-test', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: "${test_item}"), string(name: 'var', value: "${vars}"), booleanParam(name: 'verbose', value: false)]
            break
        case 'autotest':
            def test_suite
            def test_args
            def test_iterations
            def substringCount = testParams.split(',').length
            echo "substringCount=${substringCount}"
            if (substringCount == 1) {
                test_suite = testParams.replace(",", "")
            } else {
                (test_suite, test_args, test_iterations) = testParams.split(',')
            }
            test_args = test_args ?: ""
            test_iterations = test_iterations ?: ""
            echo "autotest suite: ${test_suite}"
            echo "autotest args: ${test_args}"
            echo "autotest iterations: ${test_iterations}"
            buildJob = build job: 'run-autotest', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'board', value: 'auto'), string(name: 'test_suite', value: "${test_suite}"), string(name: 'args', value: "${test_args}"), string(name: 'iterations', value: "${test_iterations}")]
            break
        case 'custom':
            dir("${env.WORKSPACE}") {
                def bundle_file = unstashParam "test_type_params"
                archiveArtifacts artifacts: "${bundle_file}", followSymlinks: false
                buildJob = build job: 'run-test-bundle-file', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]
            }
            break
    }
    if (buildJob) {
        buildResult = buildJob.getBuildVariables()['TEST_RESULT']
        println(buildResult)
        def buildLog = getLogFromRunWrapper(buildJob, 2000)
        println(buildLog)
    }
    return buildResult
}

pipeline {
    agent any
    parameters {
        separator name: 'separator-dut-settings', sectionHeader: '''<h2 style="color: blue">DUT Settings</h2>
<ul>These parameters is Settings for DUT</ul>''', sectionHeaderStyle: 'font-size: 1em;', separatorStyle: 'border-color: orange;'
        string defaultValue: '192.168.1.146', description: 'DUT IP address', name: 'dut_ip'
        choice choices: ['auto', 'brya', 'brask', 'nissa', 'dedede', 'rex'], description: 'Choose Reference Board', name: 'board'
        separator name: 'separator-kernel-building', sectionHeader: '''<h2 style="color: blue">Kernel Building</h2>
<ul>These parameters setup the kernel image building</ul>''', sectionHeaderStyle: 'font-size: 1em;', separatorStyle: 'border-color: orange;'
        choice choices: ['auto', 'v5.10', 'v5.15', 'v6.1', 'v6.6', 'upstream'], description: 'Linux kernel version', name: 'kernel_version'
        string defaultValue: '', description: 'New Commit ID', name: 'new_commit_id'
        editableChoice choices: ['new', 'bad', 'slow', 'broken'], choicesWithText: '''new
bad
slow
broken
''', defaultValue: 'new', description: 'Use you own terms instead of new', name: 'new_term', withDefaultValue: [defaultValue: 'new']
        string defaultValue: '', description: 'Old Commit ID', name: 'old_commit_id'
        editableChoice choices: ['old', 'good', 'fast', 'fixed'], choicesWithText: '''old
good
fast
fixed
''', defaultValue: 'old', description: 'Use you own terms instead of old', name: 'old_term', withDefaultValue: [defaultValue: 'old']
        string defaultValue: 'remotes/m/main', description: 'Remote Branch', name: 'remote_branch'
        string defaultValue: 'kernel-bisect', description: 'Local Branch', name: 'local_branch'
        separator name: 'separator-dut-testing', sectionHeader: '''<h2 style="color: blue">DUT Tests</h2>
<ul>These parameters are Testings on DUT</ul>''', sectionHeaderStyle: 'font-size: 1em;', separatorStyle: 'border-color: orange;'
    activeChoice choiceType: 'PT_SINGLE_SELECT', filterLength: 1, filterable: false, name: 'select_test_type', randomName: 'choice-parameter-137464070332008', script: groovyScript(fallbackScript: [classpath: [], oldScript: '', sandbox: false, script: ''], script: [classpath: [], oldScript: '', sandbox: true, script: '''return [
\'interactive\',
\'command\',
\'script\',
\'log\',
\'factory\',
\'tast\',
\'autotest\',
\'custom\'
]'''])
  activeChoiceHtml choiceType: 'ET_FORMATTED_HTML', description: 'Set the parameters for the various type of tests for kernel bisecting', name: 'test_type_params', omitValueField: false, randomName: 'choice-parameter-163185754291895', referencedParameters: 'select_test_type', script: groovyScript(fallbackScript: [classpath: [], oldScript: '', sandbox: false, script: ''], script: [classpath: [], oldScript: '', sandbox: false, script: '''switch(select_test_type) {
  case ~/.*interactive.*/:
      return "<b>Select passed or failed in input dialog</b>"
  case ~/.*command.*/:
      return "<b>Type the command running on DUT</b><br>\\
<label>Command: </label><br>\\
<input name=\'value\' value=\'suspend_stress_test -c 1\' class=\'command-input\' type=\'text\' style=\'width: 500px;\'>"
  case ~/.*script.*/:
      return "<b>Upload your test script file</b><br>\\
<label>Script file: </label><br>\\
<input name=\'file\' type=\'file\' jsonaware=\'true\' class=\'upload-script-file\'>"
  case ~/.*factory.*/:
      return "<b>Type the factory test item in factory toolkit</b><br>\\
<label>Factory test item: </label><br>\\
<input name=\'value\' value=\'RunIn.RunInDozingStress\' class=\'factory-test-item\' type=\'text\' style=\'width: 500px;\'>"
  case ~/.*log.*/:
      return "<b>Check the keyword message in log file</b><br>\\
<label>Fail Message: </label><br>\\
<input name=\'value\' value=\'Test Failed\' class=\'fail_msg\' type=\'text\' style=\'width: 500px;\'><br>\\
<label>Pass Message: </label><br>\\
<input name=\'value\' value=\'Test Passed\' class=\'pass_msg\' type=\'text\' style=\'width: 500px;\'>"
  case ~/.*tast.*/:
      return "<b>Running the tast test item on DUT</b><br>\\
<label>Tast test item: </label><br>\\
<input name=\'value\' value=\'storage.FullQualificationStress.stress\' class=\'tast-test-item\' type=\'text\' style=\'width: 500px;\'><br>\\
<label>Tast runtime variables: </label><br>\\
<input name=\'value\' value=\'tast_disk_size_gb=256\' class=\'tast-vars\' type=\'text\' style=\'width: 500px;\'>"
  case ~/.*autotest.*/:
      return "<b>Running the autotest suite on DUT</b><br>\\
<label>Autotest suit: </label><br>\\
<input name=\'value\' value=\'f:.*power_UiResume/control\' class=\'autotest-suite\' type=\'text\' style=\'width: 500px;\'><br>\\
<label>Test arguments: </label><br>\\
<input name=\'value\' value=\'\' class=\'test-argument\' type=\'text\' style=\'width: 500px;\'><br>\\
<label>Iterations: </label><br>\\
<input name=\'value\' value=\'\' class=\'test-iterations\' type=\'text\' style=\'width: 500px;\'>"
  case ~/.*custom.*/:
      return "<b>Upload the test bundle file (.zip)</b><br>\\
<label>Bundle file: </label><br>\\
<input name=\'file\' type=\'file\' jsonaware=\'true\' class=\'upload-bundle-file\'>"
}'''])
        string description: 'Testing timeout in seconds', name: 'timeout'
        booleanParam defaultValue: true, description: 'Consider timeout as test passed', name: 'timeout_as_passed'
        separator(name: "end")
    }
    stages {
        stage('Check DUT') {
            steps {
                sh 'printenv'
                echo "Check DUT IP: ${params.dut_ip}"
                sh "ssh root@${params.dut_ip} 'crossystem'"
                script {
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim().toLowerCase()
                    echo "product_name: ${product_name}"
                    def (_m, _f) = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s system-family'", returnStdout: true).trim().toLowerCase().split('_')
                    family_name = _f
                    echo "family_name: ${family_name}"
                    kernel_release = sh(script: "ssh root@${params.dut_ip} 'uname -r'", returnStdout: true).trim()
                    echo "kernel_release: ${kernel_release}"
                    if (family_name == 'brya' || family_name == 'brask') {
                        build_agent = 'cros-brya-d12'
                    } else {
                        build_agent = "cros-${family_name}-d12"
                    }
                    echo "build_agent=${build_agent}"
                }
            }
        }
        stage('Validate Build Parameters') {
            agent {
                label "${build_agent}"
            }
            steps {
                script {
                    if (params.kernel_version == 'auto') {
                        if (kernel_release.contains("5.10")) {
                            kernel_package = 'chromeos-kernel-5_10'
                            kernel_version = 'v5.10'
                        } else if (kernel_release.contains("5.15")) {
                            kernel_package = 'chromeos-kernel-5_15'
                            kernel_version = 'v5.15'
                        } else if (kernel_release.contains("6.1")) {
                            kernel_package = 'chromeos-kernel-6_1'
                            kernel_version = 'v6.1'
                        } else if (kernel_release.contains("6.6")) {
                            kernel_package = 'chromeos-kernel-6_6'
                            kernel_version = 'v6.6'
                        }
                    } else if (params.kernel_version == 'v5.10') {
                        kernel_package = 'chromeos-kernel-5_10'
                        kernel_version = 'v5.10'
                    } else if (params.kernel_version == 'v5.15') {
                        kernel_package = 'chromeos-kernel-5_15'
                        kernel_version = 'v5.15'
                    } else if (params.kernel_version == 'v6.1') {
                        kernel_package = 'chromeos-kernel-6_1'
                        kernel_version = 'v6.1'
                    } else if (params.kernel_version == 'v6.6') {
                        kernel_package = 'chromeos-kernel-6_6'
                        kernel_version = 'v6.6'
                    } else {
                        kernel_package = "chromeos-kernel-${params.kernel_version}"
                        kernel_version = "${params.kernel_version}"
                    }
                    echo "kernel_package = ${kernel_package}"
                    echo "kernel_version = ${kernel_version}"
                    dir("${cros_sdk_folder}/src/third_party/kernel/${kernel_version}") {
                        prev_local_branch = sh returnStdout: true, script: 'git rev-parse --abbrev-ref HEAD'
                        echo "prev_local_branch = ${prev_local_branch}"
                        def checkout_branch = sh returnStatus: true, script: "git checkout -b ${params.local_branch} ${params.remote_branch}"
                        if (checkout_branch != 0) {
                            currentBuild.result = 'ABORTED'
                            error("Can't checkout to remote branch ${params.remote_branch}, abort ...")
                        }
                        def new_commit_id_exist = sh returnStatus: true, script: "git cat-file -e ${params.new_commit_id}"
                        def old_commit_id_exist = sh returnStatus: true, script: "git cat-file -e ${params.old_commit_id}"
                        if (new_commit_id_exist == 0 && old_commit_id_exist == 0) {
                            echo "New Commit ${params.new_commit_id} exists"
                            echo "Old Commit ${params.old_commit_id} exist"
                        } else {
                            if (new_commit_id_exist != 0)
                                echo "New Commit ${params.new_commit_id} does not exist"
                            if (old_commit_id_exist != 0)
                                echo "Old commit ${params.old_commit_id} does not exist"
                            currentBuild.result = 'ABORTED'
                            error("Commit ID does not exist, abort ...")
                        }
                    }
                }
            }
        }
        stage('Starting Bisect') {
            agent {
                label "${build_agent}"
            }
            steps {
                sh 'printenv'
                dir("${env.WORKSPACE}") {
                    sh 'touch bisect-log.txt'
                }
                script {
                    dir("${cros_sdk_folder}/src/third_party/kernel/${kernel_version}") {
                        sh """
                            git bisect start --term-old ${params.old_term} --term-new ${params.new_term}
                            git bisect ${params.old_term} ${params.old_commit_id}
                            git bisect ${params.new_term} ${params.new_commit_id}
                            git bisect log
                        """
                        def first_new_commit_not_found = sh returnStatus: true, script: "git bisect log | grep -q \"first ${params.new_term} commit\""
                        echo "first_new_commit_not_found=${first_new_commit_not_found}"
                        while (first_new_commit_not_found == 1) {
                            build job: 'build-kernel-simple', parameters: [string(name: 'board', value: "${family_name}"), string(name: 'kernel_version', value: "${kernel_version}")]
                            build job: 'update-kernel-simple', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), booleanParam(name: 'update_firmware', value: true)]
                            def old_or_new = 'skip'
                            def testResult
                            echo "Test Type: ${params.select_test_type}"
                            echo "Test Params: ${params.test_type_params}"
                            testResult = runBisectTest(params.select_test_type, params.test_type_params)
                            if (testResult == 'TEST_TIMEOUT' && params.timeout_as_passed == true) {
                                echo "Test timeout is considered as test passed"
                                testResult = 'TEST_OK'
                            }
                            echo "TEST_RESULT=${testResult}"
                            if (testResult == 'TEST_OK') {
                                old_or_new = params.old_term
                                echo "Bisect test result: ${old_or_new}"
                            } else if (testResult == 'TEST_FAIL') {
                                old_or_new = params.new_term
                                echo "Bisect test result: ${old_or_new}"
                            } else if (testResult == 'BUILD_FAIL') {
                                echo "Bisect test build fail"
                                currentBuild.result = 'FAILURE'
                            } else if (testResult == 'BUILD_ABORT') {
                                echo "Bisect test build abort"
                                currentBuild.result = "ABORTED"
                            }
                            sh "git bisect ${old_or_new}"
                            sh 'git bisect log'
                            // If first new commit found ?
                            first_new_commit_not_found = sh returnStatus: true, script: "git bisect log | grep -q \"first ${params.new_term} commit\""
                            echo "first_new_commit_not_found=${first_new_commit_not_found}"
                        }
                    }
                }
            }
            post {
                always {
                    dir("${cros_sdk_folder}/src/third_party/kernel/${kernel_version}") {
                        sh """
                            git bisect log > ${env.WORKSPACE}/bisect-log.txt
                            git bisect reset
                            git checkout ${prev_local_branch}
                            git branch -D ${params.local_branch}
                        """
                    }
                }
            }
        }
        stage('Archive Bisect Log') {
            agent {
                label "${build_agent}"
            }
            steps {
                dir("${env.WORKSPACE}") {
                    archiveArtifacts artifacts: 'bisect-log.txt', followSymlinks: false
                }
            }
        }
    }
}
