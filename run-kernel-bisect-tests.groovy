@Library("jenkinsci-unstashParam-library") _

pipeline {
  agent any

  parameters {
    string defaultValue: '192.168.1.115', description: 'DUT IP address', name: 'dut_ip'
    activeChoice choiceType: 'PT_SINGLE_SELECT', filterLength: 1, filterable: false, name: 'select_test_type', randomName: 'choice-parameter-137464070332008', script: groovyScript(fallbackScript: [classpath: [], oldScript: '', sandbox: false, script: ''], script: [classpath: [], oldScript: '', sandbox: true, script: '''return [
\'manual\',
\'command\',
\'script\',
\'factory\',
\'tast\',
\'custom\'
]'''])

  activeChoiceHtml choiceType: 'ET_FORMATTED_HTML', description: 'Set the parameters for the various type of tests for kernel bisecting', name: 'test_type_params', omitValueField: false, randomName: 'choice-parameter-163185754291895', referencedParameters: 'select_test_type', script: groovyScript(fallbackScript: [classpath: [], oldScript: '', sandbox: false, script: ''], script: [classpath: [], oldScript: '', sandbox: false, script: '''switch(select_test_type) {
  case ~/.*manual.*/:
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
  case ~/.*tast.*/:
      return "<b>Running the tast test item on DUT</b><br>\\
<label>Tast test item: </label><br>\\
<input name=\'value\' value=\'storage.FullQualificationStress.stress\' class=\'tast-test-item\' type=\'text\' style=\'width: 500px;\'><br>\\
<label>Tast runtime variables: </label><br>\\
<input name=\'value\' value=\'tast_disk_size_gb=256\' class=\'tast-vars\' type=\'text\' style=\'width: 500px;\'>"
  case ~/.*custom.*/:
      return "<b>Upload the test bundle file (.zip)</b><br>\\
<label>Bundle file: </label><br>\\
<input name=\'file\' type=\'file\' jsonaware=\'true\' class=\'upload-bundle-file\'>"
}'''])

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

                }
            }
        }

        stage('Check test parameters') {
            steps {
                cleanWs()
                script {
                    if (params.select_test_type == 'manual') {
                        def pass_or_fail = input id: 'select_passed_or_failed', message: "Is this test Passed or Failed ?", parameters: [choice(choices: ["Passed", "Failed"], description: 'Test Passed or Failed', name: 'manual_test_result')]
                        if (pass_or_fail == 'Passed') {
                            env.TEST_RESULT = 'TEST_OK'
                        } else {
                            env.TEST_RESULT = 'TEST_FAIL'
                        }
                    } else if (params.select_test_type == 'command') {
                        def dut_cmd = params.test_type_params.replace(",", "")
                        echo "DUT command=${dut_cmd}"

                        def buildJob = build job: 'run-command-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'dut_cmd', value: "${dut_cmd}")]
                        def buildResult = buildJob.getBuildVariables()['TEST_RESULT']
                        println(buildResult)

                        def buildLog = Jenkins.getInstance().getItemByFullName('run-command-on-dut').getBuildByNumber(buildJob.getNumber()).log
                        println(buildLog)

                        env.TEST_RESULT = buildResult
                    } else if (params.select_test_type == 'script') {
                        def script_file = unstashParam "test_type_params"
                        sh "cat ${script_file}"

                        def testScript = readFile("${script_file}")
                        def buildJob = build job: 'run-script-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), base64File(name: 'script_file', base64: Base64.encoder.encodeToString(testScript.bytes))]
                        def buildResult = buildJob.getBuildVariables()['TEST_RESULT']
                        println(buildResult)

                        def buildLog = Jenkins.getInstance().getItemByFullName('run-script-on-dut').getBuildByNumber(buildJob.getNumber()).log
                        println(buildLog)

                        env.TEST_RESULT = buildResult
                    } else if (params.select_test_type == 'factory') {
                        def test_item = params.test_type_params.replace(",", "")
                        echo "factory test item: ${test_item}"

                        def buildJob = build job: 'run-factory-test', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: "${test_item}")]
                        def buildResult = buildJob.getBuildVariables()['TEST_RESULT']
                        println(buildResult)

                        def buildLog = Jenkins.getInstance().getItemByFullName('run-factory-test').getBuildByNumber(buildJob.getNumber()).log
                        println(buildLog)

                        env.TEST_RESULT = buildResult
                    } else if (params.select_test_type == 'tast') {
                        def test_item = ''
                        def vars = ''

                        echo "test_type_params=${params.test_type_params}"
                        def substringCount = params.test_type_params.split(',').length
                        echo "substringCount=${substringCount}"
                        if (substringCount == 1) {
                            test_item = params.test_type_params.replace(",", "")
                        } else {
                            (test_item, vars) = params.test_type_params.split(',')
                        }

                        echo "Tast test item: ${test_item}"
                        echo "Tast vars: ${vars}"

                        def buildJob = build job: 'run-tast-test', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: "${test_item}"), string(name: 'var', value: "${vars}"), booleanParam(name: 'verbose', value: false)]
                        def buildResult = buildJob.getBuildVariables()['TEST_RESULT']
                        println(buildResult)

                        def buildLog = Jenkins.getInstance().getItemByFullName('run-tast-test').getBuildByNumber(buildJob.getNumber()).log
                        println(buildLog)

                        env.TEST_RESULT = buildResult
                    } else if (params.select_test_type == 'custom') {
                        def bundle_file = unstashParam "test_type_params"
                        archiveArtifacts artifacts: "${bundle_file}", followSymlinks: false
                        
                        def buildJob = build job: 'run-test-bundle-file', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]
                        def buildResult = buildJob.getBuildVariables()['TEST_RESULT']
                        println(buildResult)

                        def buildLog = Jenkins.getInstance().getItemByFullName('run-test-bundle-file').getBuildByNumber(buildJob.getNumber()).log
                        println(buildLog)

                        env.TEST_RESULT = buildResult
                    }

                }
            }
        }
    
        
    }
}
