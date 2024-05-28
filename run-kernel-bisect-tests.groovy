@Library("jenkinsci-unstashParam-library") _
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
@NonCPS
String getLogFromRunWrapper(RunWrapper runWrapper, int logLines) {
    runWrapper.getRawBuild().getLog(logLines).join('\n')
}
def buildTriggerByUpstream() {
    return currentBuild.getBuildCauses()[0]['shortDescription'].contains('upstream')
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
            def script_file = unstashParam "test_type_params"
            sh "cat ${script_file}"
            def testScript = readFile("${script_file}")
            buildJob = build job: 'run-script-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), base64File(name: 'script_file', base64: Base64.encoder.encodeToString(testScript.bytes)), string(name: 'timeout', value: "${params.timeout}")]
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
            def bundle_file = unstashParam "test_type_params"
            archiveArtifacts artifacts: "${bundle_file}", followSymlinks: false
            buildJob = build job: 'run-test-bundle-file', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]
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
    string defaultValue: '192.168.1.115', description: 'DUT IP address', name: 'dut_ip'
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
                    product_name = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s baseboard-product-name'", returnStdout: true).trim().toLowerCase()
                    echo "product_name: ${product_name}"
                    def (_m, _f) = sh(script: "ssh root@${params.dut_ip} 'dmidecode -s system-family'", returnStdout: true).trim().toLowerCase().split('_')
                    family_name = _f
                    echo "family_name: ${family_name}"
                }
            }
        }
        stage('Run test') {
            steps {
                cleanWs()
                script {
                    echo "Test Type: ${params.select_test_type}"
                    echo "Test Params: ${params.test_type_params}"
                    env.TEST_RESULT = runBisectTest(params.select_test_type, params.test_type_params)
                    if (env.TEST_RESULT == 'TEST_TIMEOUT' && params.timeout_as_passed == true) {
                        echo "Test timeout is considered as test passed"
                        env.TEST_RESULT = 'TEST_OK'
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
