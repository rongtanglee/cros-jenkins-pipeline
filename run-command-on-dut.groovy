def result

pipeline {
    agent {
        label 'jenkins-master'
    }
    
    parameters {
        string defaultValue: '192.168.1.102', description: 'DUT IP address', name: 'dut_ip'
        string defaultValue: 'suspend_stress_test', description: 'Type the command running on DUT', name: 'dut_cmd'
        
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
        
        stage('Run command on DUT') {
            steps {
                script {
                    result = sh label: 'run-dut-cmd', returnStatus: true, script: "ssh root@${params.dut_ip} '${params.dut_cmd}'"
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
