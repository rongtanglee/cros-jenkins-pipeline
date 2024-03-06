def result

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        base64File description: 'Upload bash script file', name: 'script_file'
        text defaultValue: '''#!/bin/bash

output=$(suspend_stress_test -c 5 | tee >(cat >&2))

if [[ $output == *"s0ix errors: 1"* ]] || [[ $output == *"Premature wakes: 1"* ]]; then
	echo "found S0ix error"
	exit 1
fi''', description: 'Write bash script in this text editor if script file is not available', name: 'script_text'
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
        
        stage('Push script file') {
            steps {
                script {
                    withFileParameter(name: 'script_file', allowNoFile: true) {
                        //unstash 'script_file'
                        if (params.script_file != '') {
                            sh "dos2unix $script_file"
                            sh "cat $script_file"
                            sh "scp $script_file root@${params.dut_ip}:/usr/local/test.sh"
                            sh "ssh root@${params.dut_ip} 'chmod +x /usr/local/test.sh'"
                        } else {
                            echo 'script file is not available'
                            
                            writeFile file: 'test.sh', text: "${params.script_text}"
                            sh "cat test.sh"
                            sh "chmod +x test.sh"
                            sh "scp test.sh root@${params.dut_ip}:/usr/local/test.sh"
                        }
                    }
                }
            }
        }
        
        stage('Run script') {
            steps {
                script {
                    result = sh label: 'run-dut-cmd', returnStatus: true, script: "ssh root@${params.dut_ip} '/usr/local/test.sh'"
                    echo "result=${result}"

                    if (result == 0) {
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
        always {
            sh "ssh root@${params.dut_ip} 'rm /usr/local/test.sh'"
        }

        failure {
            script {
                env.TEST_RESULT = "BUILD_FAIL"
            }
        }
        aborted {
            script {
                env.TEST_RESULT = "BUILD_ABORT"
            }
        }
    }
}
