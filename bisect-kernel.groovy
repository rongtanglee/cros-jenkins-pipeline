@Library("jenkinsci-unstashParam-library") _

def product_name = ''
def family_name = ''
def current_dt = ''
def kernel_release = ''
def kernel_package = ''
def kernel_version = ''
def prev_local_branch = ''
def cros_sdk_folder = '/home/ron/cros-tot'
def build_agent = ''

def dut_cmd = null
def script_file = null
def testScript = null
def factory_test_item = null
def tast_test_item = null
def tast_vars = ''
def bundle_file = null

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

        string defaultValue: 'remotes/m/stable', description: 'Remote Branch', name: 'remote_branch'
        string defaultValue: 'test-bisect', description: 'Local Branch', name: 'local_branch'

        separator name: 'separator-dut-testing', sectionHeader: '''<h2 style="color: blue">DUT Tests</h2>
<ul>These parameters are Testings on DUT</ul>''', sectionHeaderStyle: 'font-size: 1em;', separatorStyle: 'border-color: orange;'

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

                    if (params.select_test_type == 'command') {
                        dut_cmd = params.test_type_params.replace(",", "").replace("\\", "")
                        echo "DUT command=${dut_cmd}"
                    } else if (params.select_test_type == 'script') {
                        script_file = unstashParam "test_type_params"
                        sh "cat ${script_file}"
                        testScript = readFile("${script_file}")
                    } else if (params.select_test_type == 'factory') {
                        factory_test_item = params.test_type_params.replace(",", "")
                        echo "factory test item: ${factory_test_item}"
                    } else if (params.select_test_type == 'tast') {
                        echo "test_type_params=${params.test_type_params}"
                        def substringCount = params.test_type_params.split(',').length
                        echo "substringCount=${substringCount}"
                        if (substringCount == 1) {
                            tast_test_item = params.test_type_params.replace(",", "")
                        } else {
                            (tast_test_item, tast_vars) = params.test_type_params.split(',')
                        }

                        echo "Tast test item: ${tast_test_item}"
                        echo "Tast vars: ${tast_vars}"
                    } else if (params.select_test_type == 'custom') {
                        bundle_file = unstashParam "test_type_params"
                        archiveArtifacts artifacts: "${bundle_file}", followSymlinks: false
                    }
                }
            }
        }

        stage('Starting Bisect') {
            agent {
                label "${build_agent}"
            }

            steps {
                script {
                    dir("${cros_sdk_folder}/src/third_party/kernel/${kernel_version}") {
                        sh """
                            git bisect start --term-old ${params.old_term} --term-new ${params.new_term}
                            git bisect ${params.old_term} ${params.old_commit_id}
                            git bisect ${params.new_term} ${params.new_commit_id}
                            git bisect log
                        """

                        def first_new_commit_not_found = sh returnStatus: true, script: "git bisect log | grep -q \"first ${params.new_term} commit\""
                        while (first_new_commit_not_found == 1) {
                            build job: 'build-kernel-simple', parameters: [string(name: 'board', value: "${family_name}"), string(name: 'kernel_version', value: "${kernel_version}")]

                            build job: 'update-kernel-simple', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), booleanParam(name: 'update_firmware', value: true)]

                            def old_or_new = 'skip'
                            def buildJob = null
                            def testResult = null

                            try {
                                if (params.select_test_type == 'manual') {
                                    old_or_new = input id: 'Choose_bisect_old_or_new', message: "Is bisect ${params.old_term} or ${params.new_term} ?", parameters: [choice(choices: ["${params.old_term}", "${params.new_term}", 'skip'], description: 'Bisect Old or New', name: 'manual_input')]
                                } else if (params.select_test_type == 'command') {
                                    buildJob = build job: 'run-command-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'dut_cmd', value: "${dut_cmd}")]

                                    def buildlog = Jenkins.getInstance().getItemByFullName('run-command-on-dut').getBuildByNumber(buildJob.getNumber()).log
                                    println(buildlog)
                                } else if (params.select_test_type == 'script') {
                                    buildJob = build quietPeriod: 5, job: 'run-script-on-dut', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), base64File(name: 'script_file', base64: Base64.encoder.encodeToString(testScript.bytes))]

                                    def buildlog = Jenkins.getInstance().getItemByFullName('run-script-on-dut').getBuildByNumber(buildJob.getNumber()).log
                                    println(buildlog)

                                } else if (params.select_test_type == 'factory') {
                                    buildJob = build job: 'run-factory-test', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: "${factory_test_item}")]
                                } else if (params.select_test_type == 'tast') {
                                    buildJob = build job: 'run-tast-test', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}"), string(name: 'test_item', value: "${tast_test_item}"), string(name: 'var', value: "${tast_vars}"), booleanParam(name: 'verbose', value: false)]

                                } else if (params.select_test_type == 'custom') {
                                    buildJob = build job: 'run-test-bundle-file', parameters: [string(name: 'dut_ip', value: "${params.dut_ip}")]
                                }
                            } catch (err) {
                                echo "Caught: ${err}"
                                testResult = 'BUILD_FAIL'
                            }

                            if (buildJob) {
                                testResult = buildJob.getBuildVariables()["TEST_RESULT"]
                                println(testResult)

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
                            } else {
                                if (testResult == 'BUILD_FAIL') {
                                    currentBuild.result = 'FAILURE'
                                }
                            }

                            sh "git bisect ${old_or_new}"

                            sh 'git bisect log'
                            first_new_commit_not_found = sh returnStatus: true, script: "git bisect log | grep -q \"first ${params.new_term} commit\""
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
